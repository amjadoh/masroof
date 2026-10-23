"""Fail-closed release gate for the checked-out commit's complete main CI run."""
import datetime as dt
import json
import os
import re
import subprocess
import sys
import time

REQUIRED_JOBS = ('unit-test', 'static-analysis', 'emulator-smoke', 'emulator-lifecycle')


def gh(path):
    return json.loads(subprocess.check_output(['gh', 'api', path], text=True))


def verify(repo, sha):
    """Return True when complete, False when pending; reject terminal failures."""
    runs = gh(f'repos/{repo}/actions/workflows/ci.yml/runs?head_sha={sha}&per_page=100')['workflow_runs']
    runs = [r for r in runs if r['head_sha'] == sha and r['event'] == 'push' and
            r['head_branch'] == 'main' and r.get('head_repository', {}).get('full_name', '').lower() == repo.lower()]
    if not runs:
        return False
    run = max(runs, key=lambda r: r['id'])
    if run['status'] != 'completed':
        return False
    if run['conclusion'] != 'success':
        raise ValueError(f"Latest complete CI run is {run['conclusion']}: {run['html_url']}")
    completed = dt.datetime.fromisoformat(run['updated_at'].replace('Z', '+00:00'))
    if dt.datetime.now(dt.timezone.utc) - completed > dt.timedelta(days=7):
        raise ValueError('CI evidence is older than seven days')
    jobs = []
    page = 1
    while True:
        batch = gh(f"repos/{repo}/actions/runs/{run['id']}/jobs?filter=latest&per_page=100&page={page}")['jobs']
        jobs.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    for name in REQUIRED_JOBS:
        matches = [j for j in jobs if j['name'] == name]
        if not matches or any(j['status'] != 'completed' or j['conclusion'] != 'success' for j in matches):
            raise ValueError(f'Missing or unsuccessful required CI job: {name}')
    print(f"Complete main CI verified for {sha}: {run['html_url']}")
    return True


def main():
    if os.environ.get('SKIP_CI_CHECK', 'false').lower() == 'true':
        raise ValueError('Skipping complete CI is not permitted')
    repo = os.environ.get('GITHUB_REPOSITORY', '')
    sha = sys.argv[1] if len(sys.argv) == 2 else subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    if not re.fullmatch(r'[0-9a-f]{40}', sha) or not re.fullmatch(r'[\w.-]+/[\w.-]+', repo):
        raise ValueError('Expected repository and immutable checked-out commit SHA')
    wait = min(900, max(0, int(os.environ.get('CI_CHECK_WAIT_SECONDS', '900'))))
    poll = min(30, max(1, int(os.environ.get('CI_CHECK_POLL_SECONDS', '20'))))
    deadline = time.monotonic() + wait
    while not verify(repo, sha):
        if time.monotonic() >= deadline:
            raise ValueError(f'Timed out waiting for complete main CI on {sha}')
        print(f'Complete main CI still pending on {sha}; waiting {poll}s', flush=True)
        time.sleep(min(poll, max(0, deadline - time.monotonic())))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, subprocess.CalledProcessError) as error:
        sys.exit(f'CI gate refused publication: {error}')
