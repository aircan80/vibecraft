#!/bin/bash
# Measures the test count per module at each 3A task's final commit, in a throwaway worktree.
set -u
export PATH="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot/bin:$PATH"
S="C:/Users/aircan80/AppData/Local/Temp/claude/H--claude-java/a1319091-e49b-497b-a1d5-19b251c5d1a8/scratchpad"
WT="$S/wt-3a-count"
OUT="$S/dash/task-tests"
cd /h/claude_java || exit 1
git worktree add --detach "$WT" 4e19548 >/dev/null 2>&1 || { git worktree prune; git worktree add --detach "$WT" 4e19548 || exit 1; }
for pair in 1:4e19548 2:14b02e9 3:0f82085 4:7ac1847 5:9751be5 6:43b5b32 7:9efed5d 8:6d97b06 9:cd2795e 10:befc422; do
  n=${pair%%:*}; c=${pair##*:}
  [ -f "$OUT/task-$n.json" ] && { echo "task $n cached"; continue; }
  git -C "$WT" checkout -q --detach "$c" || { echo "task $n checkout failed"; continue; }
  ( cd "$WT" && ./mvnw -B -q -o test -Dsurefire.printSummary=false >"$OUT/task-$n.log" 2>&1 ); rc=$?
  /h/claude_java/tools/clientdata/.venv/Scripts/python -I - "$WT" "$OUT/task-$n.json" "$n" "$c" "$rc" <<'PY'
import sys, glob, os, json, xml.etree.ElementTree as ET
wt, out, n, c, rc = sys.argv[1:]
res = {}
for mod in ("lua-table", "importer", "app"):
    t = f = e = 0
    for p in glob.glob(os.path.join(wt, mod, "target", "surefire-reports", "TEST-*.xml")):
        r = ET.parse(p).getroot()
        t += int(r.get("tests", 0)); f += int(r.get("failures", 0)); e += int(r.get("errors", 0))
    res[mod] = {"tests": t, "failures": f + e}
json.dump({"task": int(n), "commit": c, "exit": int(rc), "modules": res}, open(out, "w"))
print("task", n, c, "exit", rc, {m: v["tests"] for m, v in res.items()})
PY
done
cd /h/claude_java && git worktree remove --force "$WT" >/dev/null 2>&1; git worktree prune
echo "ALL DONE"
