#!/usr/bin/env python3
"""Static safety checks for cloud/*.sql (Supabase). Cheap guard-rails, not a substitute for the live advisors.

1. Every table created has row level security enabled in the same file.
2. Every SECURITY DEFINER function pins search_path and ends it with pg_temp.
3. Every function that takes a secret or a token is revoked from `public`.
"""
import glob, re, sys

problems = []
for path in sorted(glob.glob("cloud/*.sql")):
    text = open(path, encoding="utf-8").read()
    code = "\n".join(l.split("--")[0] for l in text.splitlines())
    tables = re.findall(r"create table if not exists\s+(\w+)", code, re.I) + re.findall(r"create table\s+(?!if)(\w+)", code, re.I)
    for t in sorted(set(tables)):
        if not re.search(rf"alter table\s+{t}\s+enable row level security", code, re.I):
            problems.append(f"{path}: table {t} has no 'enable row level security'")
    for m in re.finditer(r"create (?:or replace )?function\s+(\w+)\s*\((.*?)\)(.*?)\$\$", code, re.I | re.S):
        name, args, head = m.group(1), m.group(2), m.group(3)
        if re.search(r"security definer", head, re.I):
            sp = re.search(r"set search_path\s*=\s*([\w, ]+)", head, re.I)
            if not sp or not re.sub(r"\s+as\s*$", "", sp.group(1).strip()).endswith("pg_temp"):
                problems.append(f"{path}: function {name} is security definer without 'set search_path = ..., pg_temp'")
            if not re.search(rf"revoke all on function[^;]*\b{name}\b", code, re.I | re.S):
                problems.append(f"{path}: function {name} is security definer but never revoked from public")
if problems:
    print("Cloud SQL checks failed:"); [print(" -", p) for p in problems]; sys.exit(1)
print("Cloud SQL checks passed")
