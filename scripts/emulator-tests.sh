#!/usr/bin/env bash
# Runs on the emulator (started by the instrumented workflow): installs the app and its tests, runs them, and
# prints every result line as a CI annotation, because the artifact download is not always reachable.
set -u
gradle :app:installDebug :app:installDebugAndroidTest --no-daemon --stacktrace || exit 1
adb shell pm list instrumentation | tee instr.txt
runner=$(sed -n 's/^instrumentation:\([^ ]*\) .*/\1/p' instr.txt | head -1)
echo "runner=$runner"
[ -n "$runner" ] || { echo "::error::no instrumentation runner is installed"; exit 1; }
adb shell am instrument -w -r "$runner" > instrument-output.txt 2>&1
python3 - instrument-output.txt <<'PY' > summary.txt
import re, sys
cur = {}; rows = []
for line in open(sys.argv[1], errors="replace"):
    line = line.rstrip("\n")
    m = re.match(r"INSTRUMENTATION_STATUS: (\w+)=(.*)", line)
    if m: cur[m.group(1)] = m.group(2); last = m.group(1); continue
    m = re.match(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", line)
    if m:
        code = int(m.group(1))
        if code != 1:   # 1 = test started
            rows.append(("passed" if code == 0 else "FAILED", cur.get("class", "").split(".")[-1], cur.get("test", "?"), cur.get("stack", "")[:300]))
        cur = {}; continue
    if "last" in dir() and last == "stack" and line and not line.startswith("INSTRUMENTATION"):
        pass
for r in rows:
    print(f"{r[0]}  {r[1]}.{r[2]}" + (f"  -- {r[3]}" if r[0] == "FAILED" else ""))
print(f"TOTAL {len(rows)} tests, {sum(1 for r in rows if r[0]=='FAILED')} failed")
PY
cat summary.txt
# One annotation holds the whole list (GitHub shows only the first few separate annotations).
echo "::notice title=Emulator test results::$(sed ':a;N;$!ba;s/%/%25/g;s/\r/%0D/g;s/\n/%0A/g' summary.txt)"
if grep -q "INSTRUMENTATION_STATUS_CODE: -[12]" instrument-output.txt || grep -q "^FAILURES" instrument-output.txt; then
  echo "::error::an emulator test failed"; exit 1
fi
grep -q "^OK (" instrument-output.txt || { echo "::error::the emulator tests did not report OK"; exit 1; }
