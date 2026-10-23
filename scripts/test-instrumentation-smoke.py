"""Exercise device-suite routing and failure collection without an SDK or emulator."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
WRAPPER = ROOT / ".github/scripts/run-instrumentation-smoke.sh"


class InstrumentationSmokeTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.work = Path(self.directory.name)
        tools = self.work / "bin"
        tools.mkdir()
        gradle = self.work / "gradlew"
        gradle.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
pathlib.Path("gradle-args.json").write_text(json.dumps(sys.argv[1:]))
sys.exit(int(os.environ.get("TEST_GRADLE_STATUS", "0")))
''')
        gradle.chmod(0o755)
        adb = tools / "adb"
        adb.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
with open("adb-calls.jsonl", "a") as output: output.write(json.dumps(sys.argv[1:]) + "\\n")
if os.environ.get("TEST_ADB_FAILURE") == "1": sys.exit(1)
if "m19-statement" in sys.argv[2] and os.environ.get("TEST_SCREENSHOT", "1") == "1":
    (pathlib.Path(sys.argv[-1]) / "m19-synthetic.png").write_bytes(b"synthetic")
if "beta-dashboard-screenshots" in sys.argv[2] and os.environ.get("TEST_SCREENSHOT", "1") == "1":
    for name in ("03-dashboard-with-data.png", "10b-original-classification-actions.png"):
        (pathlib.Path(sys.argv[-1]) / name).write_bytes(b"synthetic")
''')
        adb.chmod(0o755)
        self.environment = dict(os.environ, ANDROID_SDK_ROOT="/synthetic/sdk",
                                PATH=str(tools) + os.pathsep + os.environ["PATH"])

    def run_suite(self, suite, *arguments, **environment):
        return subprocess.run(["bash", str(WRAPPER), suite, *arguments], cwd=self.work,
                              env=dict(self.environment, **environment),
                              text=True, capture_output=True, timeout=10)

    def gradle_args(self):
        return json.loads((self.work / "gradle-args.json").read_text())

    def captures(self):
        return [json.loads(line) for line in (self.work / "adb-calls.jsonl").read_text().splitlines()]

    def test_app_runs_full_production_suite_and_requires_its_screenshots(self):
        result = self.run_suite("app")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([":app:connectedDebugAndroidTest", "--no-parallel", "--stacktrace"], self.gradle_args())
        self.assertEqual([["pull", "/sdcard/m19-statement-screenshots/.", "m19-statement-screenshots"]], self.captures())

    def test_gradle_failure_is_preserved_after_screenshot_collection(self):
        for suite in ("app",):
            with self.subTest(suite=suite):
                result = self.run_suite(suite, TEST_GRADLE_STATUS="17")
                self.assertEqual(17, result.returncode)
        self.assertEqual(1, len(self.captures()))

    def test_capture_failure_does_not_replace_the_gradle_failure(self):
        result = self.run_suite("app", TEST_GRADLE_STATUS="19", TEST_ADB_FAILURE="1")
        self.assertEqual(19, result.returncode)
        self.assertEqual(1, len(self.captures()))

    def test_successful_app_suite_still_fails_if_screenshots_are_missing(self):
        result = self.run_suite("app", TEST_SCREENSHOT="0")
        self.assertNotEqual(0, result.returncode)

    def test_bad_suite_cannot_start_gradle_or_adb(self):
        result = self.run_suite("app;echo unexpected")
        self.assertEqual(2, result.returncode)
        self.assertFalse((self.work / "gradle-args.json").exists())
        self.assertFalse((self.work / "adb-calls.jsonl").exists())

    def test_each_app_shard_passes_both_runner_arguments(self):
        for count in (2, 4):
            for shard in range(count):
                with self.subTest(count=count, shard=shard):
                    result = self.run_suite("app", str(shard), str(count), TEST_SCREENSHOT="0")
                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertEqual([
                        ":app:connectedDebugAndroidTest",
                        f"-Pandroid.testInstrumentationRunnerArguments.numShards={count}",
                        f"-Pandroid.testInstrumentationRunnerArguments.shardIndex={shard}",
                        "--no-parallel", "--stacktrace"], self.gradle_args())

    def test_invalid_shards_cannot_start_tools(self):
        for suite, args in (("app", ("2", "2")), ("app", ("4", "4")),
                            ("app", ("-1", "4")), ("app", ("00", "4")),
                            ("app", ("0", "3")), ("app", ("0",)),
                            ("app", ("0", "0")), ("beta", ("0", "2"))):
            self.assertEqual(2, self.run_suite(suite, *args).returncode)
        self.assertFalse((self.work / "gradle-args.json").exists())

    def test_shard_failure_is_preserved(self):
        for count in (2, 4):
            for shard in range(count):
                with self.subTest(count=count, shard=shard):
                    self.assertEqual(17, self.run_suite("app", str(shard), str(count),
                                     TEST_GRADLE_STATUS="17").returncode)
        self.assertEqual(6, len(self.captures()))

    def test_workflow_keeps_complete_coverage_and_clean_devices(self):
        workflow = (ROOT / '.github/workflows/ci.yml').read_text()
        app = workflow.split('  emulator-app:', 1)[1].split('  emulator-recovery:', 1)[0]
        recovery = workflow.split('  emulator-recovery:', 1)[1].split('  emulator-lifecycle:', 1)[0]
        self.assertIn('shard: [0, 1, 2, 3]', app)
        self.assertIn('scripts/ci_device.py run --shard', app)
        self.assertNotIn('gradlew', app)
        self.assertIn('fail-fast: false', app)
        self.assertIn('suite: [production, upgrade]', recovery)
        self.assertNotIn('gradlew', recovery)
        self.assertIn('CI_DEVICE_BUNDLE:', recovery)
        for section in [app, recovery]:
            self.assertIn('name: shared-test-apks', section)
            self.assertIn('-no-snapshot', section)
            self.assertIn('if: always()', section)
        gate = workflow.split('  emulator-smoke:', 1)[1]
        self.assertIn('unit-test, static-analysis, emulator-app, emulator-lifecycle', gate)
        self.assertIn('Require complete CI', gate)
        self.assertIn('Require statement reconciliation screenshots', gate)


if __name__ == "__main__":
    unittest.main()
