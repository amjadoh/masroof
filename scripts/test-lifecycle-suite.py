"""Check isolated recovery routing and failure propagation without an emulator."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / '.github/scripts/run-lifecycle-suite.sh'


class LifecycleSuiteTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.work = Path(self.temp.name)
        (self.work / 'scripts').mkdir()
        (self.work / 'bin').mkdir()
        stub = '''#!/usr/bin/env python3
import json, os, pathlib, sys
with open('calls.jsonl', 'a') as f: f.write(json.dumps([pathlib.Path(sys.argv[0]).name, *sys.argv[1:]]) + '\\n')
sys.exit(int(os.environ.get('FAIL_STATUS', '0')))
'''
        for name in ('gradlew', 'bin/adb', 'scripts/beta-process-death.sh',
                     'scripts/m13-process-death.sh', 'scripts/beta-apk-upgrade.sh'):
            path = self.work / name
            if name.startswith('scripts/'):
                path.write_text('exec python3 "$(dirname "$0")/../recorder.py" "$0"\n')
            else:
                path.write_text(stub)
            path.chmod(0o755)
        (self.work / 'recorder.py').write_text(stub.replace(
            "pathlib.Path(sys.argv[0]).name, *sys.argv[1:]",
            "pathlib.Path(sys.argv[1]).name, *sys.argv[2:]"))
        self.env = dict(os.environ, ANDROID_SDK_ROOT='/synthetic/sdk',
                        PATH=str(self.work / 'bin') + os.pathsep + os.environ['PATH'])

    def run_suite(self, *args, **env):
        return subprocess.run(['bash', str(SCRIPT), *args], cwd=self.work,
                              env=dict(self.env, **env), capture_output=True, text=True, timeout=10)

    def calls(self):
        return [json.loads(s) for s in (self.work / 'calls.jsonl').read_text().splitlines()]

    def test_builds_only_the_selected_suite(self):
        expected = {
            'beta': [':lab:beta:assembleDebugAndroidTest'],
            'production': [':app:assembleDebug', ':app:assembleDebugAndroidTest'],
            'upgrade': [':lab:upgrade-host:assembleBeforeDebug', ':lab:upgrade-host:assembleAfterDebug',
                        ':lab:upgrade-host:assembleBeforeDebugAndroidTest',
                        ':lab:upgrade-host:assembleAfterDebugAndroidTest'],
        }
        for suite, tasks in expected.items():
            self.assertEqual(0, self.run_suite(suite, 'build').returncode)
            self.assertEqual(['gradlew', *tasks, '--stacktrace'], self.calls()[-1])

    def test_each_test_job_runs_only_its_own_journeys(self):
        for suite, script, count in [('beta', 'beta-process-death.sh', 2),
                                     ('production', 'm13-process-death.sh', 3),
                                     ('upgrade', 'beta-apk-upgrade.sh', 1)]:
            before = len(self.calls()) if (self.work / 'calls.jsonl').exists() else 0
            result = self.run_suite(suite, 'test')
            self.assertEqual(0, result.returncode, result.stderr)
            calls = self.calls()[before:]
            self.assertEqual(count, len(calls))
            self.assertEqual([script], calls[-1])
            self.assertFalse(any(c[0] == 'gradlew' for c in calls))
            self.assertIn('(exit 0)', result.stdout)

    def test_failure_stops_the_suite_and_is_preserved(self):
        for phase in ('build', 'test'):
            result = self.run_suite('beta', phase, FAIL_STATUS='17')
            self.assertEqual(17, result.returncode)
        self.assertEqual(2, len(self.calls()))

    def test_invalid_input_cannot_start_tools(self):
        for args in [('other', 'build'), ('beta', 'other'), ('beta',), ('beta', 'test', 'extra')]:
            self.assertNotEqual(0, self.run_suite(*args).returncode)
        self.assertFalse((self.work / 'calls.jsonl').exists())


if __name__ == '__main__':
    unittest.main()
