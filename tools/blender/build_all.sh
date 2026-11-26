#!/usr/bin/env bash
# Rebuild every food/station/building/environment/store model (+ previews),
# render the scene checks and verify the results against the asset contract.
#
#   tools/blender/build_all.sh            # everything
#   tools/blender/build_all.sh --noprev   # models only, skip preview renders
#
# Character/hat/pet models are produced by separate scripts and are not touched.
set -euo pipefail
cd "$(dirname "$0")/../.."
BLENDER="${BLENDER:-$HOME/Applications/Blender.app/Contents/MacOS/Blender}"

fail=0
for script in food stations building env stores; do
  echo "== $script"
  log="$("$BLENDER" -b --factory-startup --python "tools/blender/$script.py" -- "$@" 2>&1)" || fail=1
  echo "$log" | grep -E "^(EXPORTED|OVER BUDGET|FAILED)" || true
  if echo "$log" | grep -q "Traceback"; then
    echo "$log" | grep -A20 "Traceback"
    fail=1
  fi
done

if [[ " $* " != *" --noprev "* ]]; then
  echo "== scenes"
  "$BLENDER" -b --factory-startup --python tools/blender/scenes.py 2>&1 | grep -E "^RENDERED|Traceback" || true
fi

echo "== verify"
python3 -I tools/blender/verify_models.py || fail=1
exit $fail
