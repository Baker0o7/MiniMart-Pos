#!/usr/bin/env bash
# Runs gradle and writes its output to a log file. If it failed ONLY because a plugin or library could not be downloaded
# (public repositories sometimes refuse a busy CI runner: "Could not find ...jar"), it tries again, up to three times.
# Any other failure (a compile error, a failing test) is reported at once.
# Usage: scripts/gradle-retry.sh <log-file> <gradle arguments...>
log="$1"; shift
: > "$log"
for attempt in 1 2 3; do
  attempt_log="$(mktemp)"
  set +e
  set -o pipefail
  gradle "$@" 2>&1 | tee "$attempt_log"
  status=$?
  cat "$attempt_log" >> "$log"
  if [ "$status" -eq 0 ]; then rm -f "$attempt_log"; exit 0; fi
  if ! grep -qE "Could not (find|resolve|get) |429 Too Many|Connection reset|Read timed out" "$attempt_log"; then rm -f "$attempt_log"; exit "$status"; fi
  rm -f "$attempt_log"
  echo "::warning::A dependency download failed (attempt $attempt of 3); trying again"
  sleep $((attempt * 20))
done
exit 1
