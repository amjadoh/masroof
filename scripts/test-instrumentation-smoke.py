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
''')
        adb.chmod(0o755)
        self.environment = dict(os.environ, ANDROID_SDK_ROOT="/synthetic/sdk",
                                PATH=str(tools) + os.pathsep + os.environ["PATH"])

    def run_suite(self, suite, **environment):
        return subprocess.run(["bash", str(WRAPPER), suite], cwd=self.work,
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

    def test_beta_runs_full_beta_suite_without_requiring_production_screenshots(self):
        result = self.run_suite("beta")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([":lab:beta:connectedDebugAndroidTest", "--no-parallel", "--stacktrace"], self.gradle_args())
        self.assertEqual([["pull", "/sdcard/beta-dashboard-screenshots/.", "beta-dashboard-screenshots"]], self.captures())

    def test_gradle_failure_is_preserved_after_screenshot_collection(self):
        for suite in ("app", "beta"):
            with self.subTest(suite=suite):
                result = self.run_suite(suite, TEST_GRADLE_STATUS="17")
                self.assertEqual(17, result.returncode)
        self.assertEqual(2, len(self.captures()))

    def test_capture_failure_does_not_replace_the_gradle_failure(self):
        result = self.run_suite("beta", TEST_GRADLE_STATUS="19", TEST_ADB_FAILURE="1")
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

    def test_workflow_keeps_each_suite_once_and_builds_before_boot(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text()
        self.assertEqual(1, workflow.count("run-instrumentation-smoke.sh app"))
        self.assertEqual(1, workflow.count("run-instrumentation-smoke.sh beta"))
        self.assertEqual(2, workflow.count("profile: pixel_2"))
        smoke = workflow.split("  emulator-smoke:", 1)[1].split("  emulator-lifecycle:", 1)[0]
        lifecycle = workflow.split("  emulator-lifecycle:", 1)[1]
        for job in (smoke, lifecycle):
            self.assertLess(job.index("- name: Assemble"), job.index("uses: reactivecircus/android-emulator-runner"))
        self.assertNotIn("run-instrumentation-smoke.sh beta", smoke)
        self.assertLess(lifecycle.index("run-instrumentation-smoke.sh beta"), lifecycle.index("bash scripts/beta-process-death.sh"))
        self.assertIn("bash scripts/m13-process-death.sh", lifecycle)
        self.assertIn("bash scripts/beta-apk-upgrade.sh", lifecycle)
        self.assertIn("name: beta-instrumentation-reports", lifecycle)


if __name__ == "__main__":
    unittest.main()
