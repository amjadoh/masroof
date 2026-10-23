#!/usr/bin/env bash
set -euo pipefail
PYTHONDONTWRITEBYTECODE=1 exec python3 "$(dirname "$0")/test_verify_ci_status.py"
