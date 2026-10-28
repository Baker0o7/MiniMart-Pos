#!/usr/bin/env bash
# Prepares a release: raises versionCode by one, sets versionName and adds an empty CHANGELOG section to fill in.
# Usage: scripts/bump-version.sh 1.20.0 [YYYY-MM-DD]
set -eu
new="${1:-}"; day="${2:-$(date +%F)}"
[[ "$new" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Usage: $0 x.y.z [date]" >&2; exit 2; }
gradle=app/build.gradle.kts
code=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$gradle" | head -1)
old=$(grep -oP 'versionName\s*=\s*"\K[0-9]+\.[0-9]+\.[0-9]+' "$gradle" | head -1)
[ -n "$code" ] && [ -n "$old" ] || { echo "Could not read the current version from $gradle" >&2; exit 1; }
newest=$(printf '%s\n%s\n' "$old" "$new" | sort -V | tail -1)
{ [ "$new" != "$old" ] && [ "$newest" = "$new" ]; } || { echo "$new is not newer than $old" >&2; exit 1; }
grep -q "^## \[$new\]" CHANGELOG.md && { echo "CHANGELOG.md already has a $new section" >&2; exit 1; }
sed -i -E "s/(versionCode\s*=\s*)[0-9]+/\1$((code+1))/; s/(versionName\s*=\s*\")[0-9.]+\"/\1$new\"/" "$gradle"
python3 - "$new" "$day" <<'PY'
import sys
new, day = sys.argv[1:3]
text = open("CHANGELOG.md").read()
marker = "\n## ["
i = text.index(marker)
stub = f"\n## [{new}] - {day}\n\n- **TODO:** describe what changed for the shop, in plain words.\n"
open("CHANGELOG.md", "w").write(text[:i] + stub + text[i:])
PY
echo "Version $old ($code) -> $new ($((code+1))). Fill in the CHANGELOG entry, then run scripts/release-checks.sh."
