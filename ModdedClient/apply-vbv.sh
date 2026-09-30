#!/usr/bin/env bash
set -euo pipefail
PROJECT="${1:-}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ -z "$PROJECT" || ! -d "$PROJECT" ]]; then
  echo "Usage: $0 <generated-eagler-project>"
  exit 2
fi
python3 "$SCRIPT_DIR/apply-vbv.py" "$PROJECT"
