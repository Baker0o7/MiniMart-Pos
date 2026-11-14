# Cloud dashboard security review (Supabase row-level security)

Reviewed: `cloud/*.sql` (single-owner, multi-owner, catalog, M-Pesa inbox) and the Edge Function.
**Not reviewed live:** the hosted project `minimart-pos-cloud` (eu-central-1) is **paused**, so its real policies and the
Supabase security advisors could not be read. Nothing was changed on it. Restore it first, then run the advisors (see "Next").

## What is already right
- Every table has row-level security on. `stores`, `link_codes`, `mpesa_*` have no policies, so only the functions touch them.
- The public (anon) key can read nothing. Phones prove themselves with a random secret, stored only as a bcrypt hash.
- An owner reads only their own shops' snapshots (`owns_store()`), and the link code is stored hashed and expires in 30 minutes.
- The M-Pesa webhook token is stored hashed; incoming payment fields are validated (ID format, amount range, size limit).
- `mpesa_ingest` can only be run by the Edge Function (service role), not by the public key.

## Fixed in this change
1. **Leftover single-owner policy.** If `supabase_setup.sql` was run before `multitenant_setup.sql`, the policy
   "owner reads snapshots" (one e-mail may read every shop) stayed in place next to the per-owner one. The multi-owner script now removes it.
2. **`search_path` pinned with `pg_temp` last** on all 20 security-definer functions, so a temporary object can never shadow a table or function they use.
3. **A guard-rail check** (`scripts/check-cloud-sql.py`, run by `release-checks.sh`) fails the release if a new table lacks row-level
   security or a new security-definer function misses the pinned `search_path` or the revoke from public.

## Still open (not fixed here)
- **Guessing slowdown costs a connection.** Wrong secrets and wrong codes wait 1.5 s (`pg_sleep`). That slows guessing but lets anyone
  with the public key tie up database connections. Turn on Supabase's API rate limits, and keep "Confirm email" on with a sign-up limit.
- **Existing project may differ from these files.** Policies set by hand in the dashboard would not show here.

## Next (needs your approval, because it touches the live project)
1. Restore the paused project.
2. Run the security and performance advisors and compare `pg_policies` with these files.
3. Apply the updated SQL (safe to run again) and re-run the advisors.
