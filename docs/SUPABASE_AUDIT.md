# Cloud dashboard security review (Supabase row-level security)

Reviewed: `cloud/*.sql` (single-owner, multi-owner, catalog, M-Pesa inbox) and the Edge Function.
**Live deployment (2026-11-20):** the hosted project `minimart-pos-cloud` (eu-central-1) was restored, found empty (no hand-made
policies), and `multitenant_setup.sql`, `catalog_setup.sql` and `mpesa_webhook.sql` were applied in that order.
The security advisor shows only the expected notes: tables with row-level security and no policies (by design) and the
phone-facing functions that the public key can call (each checks a per-shop secret). Owner functions are not callable by anon.
**Two-account test (run in a rolled-back transaction, nothing left behind):** both phones registered and pushed; a wrong secret and
a reused code were refused; the public key could not read `stores` or `store_snapshots` or create codes; owner A saw only A's
snapshot, could not read `stores` or `link_codes`, and could not delete B's shop; owner B saw only B's snapshot.
**Not done yet:** Auth settings (Confirm email, sign-up and API rate limits) are dashboard-only; the `till-payments` Edge Function is not
deployed; the dashboard and app are not pointed at this project.

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
