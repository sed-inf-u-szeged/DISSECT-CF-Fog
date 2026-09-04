#!/bin/sh
# Gated §9 campaign driver.
#
#   sh tools/run_campaign.sh [stage ...]
#
# With no arguments runs every stage in order. Each stage is resumable: the
# scenario runner skips any cell whose pass3/run_metadata.json already exists,
# so re-invoking after an interruption continues rather than restarts.
#
# Stage order is the protocol, not a convenience: cell V produces the calibration
# that S1-S4 consume, and its fidelity gate (F1/F3) must pass before the
# trace-driven measurements are trusted.
set -u

# Repository root is derived from this script's own location, so the driver
# works from any checkout without editing.
REPO="$(cd "$(dirname "$0")/.." && pwd)"
SIM="$REPO/simulator"
RESULTS="$SIM/results"
LOGS="$RESULTS/paper/logs"
CELL_V="$RESULTS/paper/V/cell/seed_11"

# Python and Maven are overridable; sensible defaults are probed in order.
#   PY=/path/to/python3.12  MVN=/path/to/mvn  sh tools/run_campaign.sh
# Probe for an interpreter that HAS the dependencies, not merely the first one
# that exists. A machine can easily have several Pythons — on Windows the
# Microsoft Store ships a bare python3.12 that shadows a fully provisioned one —
# and picking by name alone stops the driver before it starts.
if [ -z "${PY:-}" ]; then
  py_tried=""
  for c in python3.12 python3 python py; do
    command -v "$c" >/dev/null 2>&1 || continue
    py_tried="$py_tried $c"
    if "$c" -c "import torch, numpy, scipy, pandas" >/dev/null 2>&1; then
      PY="$c"; break
    fi
  done
  if [ -z "${PY:-}" ]; then
    echo "No Python with the harness dependencies found (tried:${py_tried:- none})." >&2
    echo "Install them into one of those, or point PY at the right interpreter:" >&2
    echo "  PY=/path/to/python sh tools/run_campaign.sh" >&2
    exit 1
  fi
fi
if [ -z "${MVN:-}" ]; then
  if [ -x "$REPO/tools/apache-maven-3.9.16/bin/mvn" ]; then
    MVN="$REPO/tools/apache-maven-3.9.16/bin/mvn"
  elif command -v mvn >/dev/null 2>&1; then
    MVN="mvn"
  else
    echo "No Maven found. Install one, or set MVN=/path/to/mvn." >&2
    exit 1
  fi
fi
"$PY" -c "import torch, numpy, scipy, pandas" 2>/dev/null || {
  echo "Python '$PY' lacks the harness dependencies." >&2
  echo "  $PY -m pip install -r $REPO/harness/requirements.txt" >&2
  exit 1
}

# NB: maven-exec-plugin splits -Dexec.args on WHITESPACE, so no argument passed
# through it may contain a space. A checkout path very often does, which is why
# every path inside exec.args is relative to $SIM (the working directory below)
# and the runner absolutises it itself. Passing an absolute --out here would be
# truncated at the first space and write the campaign to a stray directory.
mvn_goal() {
  cd "$SIM" || exit 1
  "$MVN" "$@"
}

run_scenario() {
  name="$1"
  echo "=== [$(date '+%H:%M:%S')] scenario $name ==="
  mvn_goal -q -o exec:java \
    -Dexec.mainClass=hu.u_szeged.inf.fog.simulator.fl.run.FLScenarioRunner \
    -Dexec.args="--config ../scenarios/$name.yaml --out results --campaign paper --python $PY" \
    2>&1 | tee "$LOGS/$name.log"
}

mkdir -p "$LOGS"

stage_prereg() {
  echo "=== [$(date '+%H:%M:%S')] pre-registration ==="
  # The dry-run expansion is archived BEFORE any compute, so the grid that was
  # committed to can be compared against the grid that was executed.
  {
    echo "# Pre-registration — campaign grid, archived $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    echo "# git: $(cd "$REPO" && git rev-parse HEAD)"
    echo
    for s in V S1 S2 S3 S4; do
      mvn_goal -q -o exec:java \
        -Dexec.mainClass=hu.u_szeged.inf.fog.simulator.fl.run.FLScenarioRunner \
        -Dexec.args="--config ../scenarios/$s.yaml --dry-run --out results --campaign paper" 2>&1
    done
  } > "$RESULTS/paper/preregistration.txt" 2>&1
  grep -E "dry-run" "$RESULTS/paper/preregistration.txt"
}

stage_V() {
  run_scenario V
  echo "=== [$(date '+%H:%M:%S')] ERQ1 fidelity gate (Track B, independent selection) ==="
  # Assert the runner actually produced this cell before trusting anything in it:
  # a leftover cell from an earlier campaign at the same path would otherwise be
  # read as if it were this run's output.
  if [ ! -f "$CELL_V/pass3/run_metadata.json" ]; then
    echo "ERROR: $CELL_V has no pass3/run_metadata.json — the V scenario did not"
    echo "       write where the gate is looking. Check --out handling."
    return 2
  fi
  # Remove any previous verdict FIRST. Otherwise a gate that crashes leaves the
  # earlier fidelity.json in place and the checks below read it as this run's
  # result — which is how a failed gate once reported "GATE PASSED".
  rm -f "$CELL_V/trackb/fidelity.json"
  # `cmd | tee` reports tee's exit status, not the gate's, so the real status is
  # smuggled out through a file. Without this a Python traceback exits 0 here.
  gate_rc_file="$LOGS/.fidelity.rc"
  rm -f "$gate_rc_file"
  { "$PY" "$REPO/harness/fidelity_check.py" --cell "$CELL_V" 2>&1; \
    echo $? > "$gate_rc_file"; } | tee "$LOGS/fidelity.log"
  gate_rc="$(cat "$gate_rc_file" 2>/dev/null || echo 1)"
  rm -f "$gate_rc_file"
  if [ "$gate_rc" -ne 0 ]; then
    echo "ERROR: fidelity_check.py exited $gate_rc — see $LOGS/fidelity.log"
    return 2
  fi
  if [ ! -f "$CELL_V/trackb/fidelity.json" ]; then
    echo "ERROR: fidelity_check.py produced no fidelity.json — see $LOGS/fidelity.log"
    return 2
  fi
  # F4 may legitimately fail (absolute timing becomes indicative); F1/F3 may not,
  # because every downstream comparison rests on them.
  "$PY" - "$CELL_V/trackb/fidelity.json" <<'PYEOF'
import json, sys
d = json.load(open(sys.argv[1], encoding="utf-8"))
crit = {c["id"]: c for c in d["criteria"]}
hard = [i for i in ("F1", "F3") if not crit[i]["pass"]]
for c in d["criteria"]:
    print(f"  {c['id']} {c['criterion']:<52} {str(c['measured']):>16}  {'PASS' if c['pass'] else 'FAIL'}")
if hard:
    print(f"\nGATE FAILED on {hard} — stopping. Every downstream number rests on these.")
    sys.exit(2)
if not crit["F4"]["pass"]:
    print("\nF4 failed: absolute timing/energy are INDICATIVE; comparisons still valid.")
print("\nGATE PASSED")
PYEOF
  return $?
}

# Any other stage name is taken to be a scenario file — adding a scenario must
# not require editing this dispatcher.

stage_report() {
  echo "=== [$(date '+%H:%M:%S')] report ==="
  "$PY" "$REPO/analysis/report.py" --all --results-root "$RESULTS" 2>&1 | tee "$LOGS/report.log"
}

STAGES="${*:-prereg V S1 S2 S3 S4 S5 S6 report}"
for st in $STAGES; do
  case "$st" in
    prereg) stage_prereg ;;
    V)      stage_V || { echo "campaign halted at the fidelity gate"; exit 2; } ;;
    report) stage_report ;;
    *)
      if [ -f "$REPO/scenarios/$st.yaml" ]; then
        run_scenario "$st"
      else
        echo "unknown stage '$st' (no scenarios/$st.yaml)"; exit 1
      fi
      ;;
  esac
done
echo "=== [$(date '+%H:%M:%S')] campaign done ==="
