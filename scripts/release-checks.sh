#!/usr/bin/env bash
# Cheap guards that catch the mistakes that hurt shops most: a database version bump without a
# migration (wipes or crashes upgraded phones) and a malformed version in the build file.
set -u
fail=0
db=$(find data -name AppDatabase.kt | head -1)
ver=$(grep -oP 'version\s*=\s*\K[0-9]+' "$db" | head -1)
if [ -z "$ver" ]; then echo "::error::Could not read the Room version from $db"; exit 1; fi
prev=$((ver-1))
if [ "$prev" -ge 8 ] && ! grep -q "MIGRATION_${prev}_${ver}" "$db"; then
  echo "::error::Database is version $ver but MIGRATION_${prev}_${ver} is missing"; fail=1
fi
if [ ! -s gradle/wrapper/gradle-wrapper.jar ]; then
  echo "::error::gradle/wrapper/gradle-wrapper.jar is missing (it is committed on purpose; do not git-ignore it)"; fail=1
fi
code=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' app/build.gradle.kts | head -1)
name=$(grep -oP 'versionName\s*=\s*"\K[0-9]+\.[0-9]+\.[0-9]+' app/build.gradle.kts | head -1)
if [ -n "${name:-}" ] && [ -f CHANGELOG.md ] && ! grep -q "^## \[${name}\]" CHANGELOG.md; then
  echo "::error::CHANGELOG.md has no '## [${name}]' section for this version"; fail=1
fi
[ -n "$code" ] || { echo "::error::versionCode missing or not a number"; fail=1; }
[ -n "$name" ] || { echo "::error::versionName must look like 1.2.3"; fail=1; }
if grep -rn 'String.format("' --include=*.kt app/src/main data/src/main | grep -v Locale >/dev/null; then
  echo "::error::String.format without Locale found (breaks on phones set to other languages)"
  grep -rn 'String.format("' --include=*.kt app/src/main data/src/main | grep -v Locale | head -5
  fail=1
fi
[ "$fail" -eq 0 ] && echo "Release checks passed (db v$ver, app $name/$code)"
exit $fail
