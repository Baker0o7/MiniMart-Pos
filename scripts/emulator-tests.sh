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
grep -E "INSTRUMENTATION_STATUS: (class|test|numtests)=|INSTRUMENTATION_STATUS_CODE|^OK \(|^FAILURES|Tests run|stack=|shortMsg|INSTRUMENTATION_RESULT" instrument-output.txt \
  | head -200 | while IFS= read -r line; do echo "::notice::$line"; done
if grep -q "INSTRUMENTATION_STATUS_CODE: -[12]" instrument-output.txt || grep -q "^FAILURES" instrument-output.txt; then
  echo "::error::an emulator test failed"; exit 1
fi
grep -q "^OK (" instrument-output.txt || { echo "::error::the emulator tests did not report OK"; exit 1; }
