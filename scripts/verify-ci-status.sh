#!/usr/bin/env bash
# Verify the exact checkout, never resolve a moving branch again after checkout.
set -euo pipefail
exec python3 "$(dirname "$0")/verify_ci_status.py" "${1:-$(git rev-parse HEAD)}"
