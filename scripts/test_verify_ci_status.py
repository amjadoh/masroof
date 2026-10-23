import copy
import datetime as dt
import importlib.util
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('gate', Path(__file__).with_name('verify_ci_status.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
SHA = 'a' * 40
REPO = 'bakhshb/masroof'


class ReleaseGateTest(unittest.TestCase):
    def setUp(self):
        self.run = dict(id=123, head_sha=SHA, event='push', head_branch='main',
                        head_repository={'full_name': REPO}, status='completed', conclusion='success',
                        updated_at=dt.datetime.now(dt.timezone.utc).isoformat(), html_url='https://github.com/run/123')
        self.jobs = [dict(name=n, status='completed', conclusion='success') for n in gate.REQUIRED_JOBS]
        self.runs = [self.run]
        self.mock = patch.object(gate, 'gh', side_effect=self.api).start()
        self.addCleanup(patch.stopall)

    def api(self, path):
        return {'workflow_runs': self.runs} if '/workflows/' in path else {'jobs': self.jobs}

    def test_all_required_jobs_and_complete_workflow_pass(self):
        self.assertTrue(gate.verify(REPO, SHA))

    def test_each_failed_cancelled_skipped_or_missing_gate_refuses_release(self):
        for name in gate.REQUIRED_JOBS:
            for conclusion in ['failure', 'cancelled', 'skipped', 'timed_out', None]:
                self.jobs = [dict(name=n, status='completed', conclusion=conclusion if n == name else 'success')
                             for n in gate.REQUIRED_JOBS]
                with self.subTest(name=name, conclusion=conclusion), self.assertRaises(ValueError):
                    gate.verify(REPO, SHA)
            self.jobs = [j for j in self.jobs if j['name'] != name]
            with self.assertRaises(ValueError): gate.verify(REPO, SHA)

    def test_latest_cancelled_run_is_not_replaced_by_older_green_run(self):
        self.runs = [copy.deepcopy(self.run), dict(self.run, id=124, conclusion='cancelled')]
        with self.assertRaises(ValueError): gate.verify(REPO, SHA)

    def test_running_run_waits_even_with_old_success(self):
        self.runs.append(dict(self.run, id=124, status='in_progress', conclusion=None))
        self.assertFalse(gate.verify(REPO, SHA))

    def test_pr_or_different_commit_or_repository_does_not_prove_checkout(self):
        for change in [dict(event='pull_request'), dict(head_sha='b' * 40),
                       dict(head_repository={'full_name': 'other/repo'}), dict(head_branch='other')]:
            self.runs = [dict(self.run, **change)]
            self.assertFalse(gate.verify(REPO, SHA))

    def test_expired_evidence_is_rejected(self):
        self.run['updated_at'] = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=8)).isoformat()
        with self.assertRaises(ValueError): gate.verify(REPO, SHA)

    def test_environment_cannot_bypass_or_reduce_required_jobs(self):
        with patch.dict(os.environ, SKIP_CI_CHECK='true'), self.assertRaises(ValueError): gate.main()
        with patch.dict(os.environ, CI_JOB_NAMES='unit-test'):
            self.jobs = self.jobs[:1]
            with self.assertRaises(ValueError): gate.verify(REPO, SHA)

    def test_release_uses_trusted_workflow_gate_and_immutable_checkout(self):
        workflow = (Path(__file__).resolve().parents[1] / '.github/workflows/release.yml').read_text()
        self.assertNotIn('skip_ci_check', workflow)
        self.assertNotIn('testDebugUnitTest', workflow)
        self.assertIn('ref: ${{ github.workflow_sha }}', workflow)
        self.assertIn('python3 .release-control/scripts/verify_ci_status.py "$(git rev-parse HEAD)"', workflow)

    def test_moving_branch_is_not_an_accepted_sha(self):
        with patch.dict(os.environ, GITHUB_REPOSITORY=REPO, SKIP_CI_CHECK='false'), \
                patch.object(sys, 'argv', ['verify', 'main']), self.assertRaises(ValueError): gate.main()


if __name__ == '__main__': unittest.main()
