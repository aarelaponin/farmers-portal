#!/usr/bin/env bash
# One-time bootstrap: create the venv under tooling/.venv and install deps.
# Re-running is safe — venv is reused, pip install is idempotent.
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
PYTHON="${PYTHON:-python3}"

if [ ! -d "$DIR/.venv" ]; then
    echo "→ creating venv at $DIR/.venv"
    "$PYTHON" -m venv "$DIR/.venv"
else
    echo "→ venv already exists at $DIR/.venv"
fi

"$DIR/.venv/bin/pip" install --upgrade pip >/dev/null
"$DIR/.venv/bin/pip" install -r "$DIR/requirements.txt"

echo
echo "Done. Activate with:"
echo "    source $DIR/.venv/bin/activate"
echo
echo "Or run directly without activating:"
echo "    $DIR/.venv/bin/python $DIR/seed.py"
