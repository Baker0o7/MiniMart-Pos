# Hosted backend (multi-owner)

By default each owner can still connect their own Supabase project (`cloud/supabase_setup.sql`, see the README). The hosted mode removes that setup: **you** run one backend, and owners only sign up on the web dashboard and type a short code into the app.

## One-time setup by the operator
1. Create a Supabase project. Run `cloud/multitenant_setup.sql` in the SQL editor.
2. Authentication → Providers → Email: turn on **Confirm email**; set sign-up rate limits; set the Site URL to where the dashboard is hosted.
3. Put the project URL and **anon (public)** key in `cloud/dashboard/config.js` and publish the `cloud/dashboard/` folder (GitHub Pages, Netlify, any static host).
4. Build the app with the same two values: `minimart.cloudUrl` / `minimart.cloudAnonKey` in `gradle.properties` (or the `CLOUD_URL` / `CLOUD_ANON_KEY` environment variables / repository variables in the release workflow). Left empty, the app asks owners for their own project as before.

## What owners do
Dashboard → create account → confirm email → **Add a shop** → type the code (e.g. `K7QM-4TX9`, valid once for 30 minutes) into the app: Settings → Cloud dashboard.

## What it protects, and what you take on
* Each owner sees only their own shops (row-level security via `owns_store()`); the public key reads nothing.
* Phones prove themselves with a random secret, stored only as a bcrypt hash. Codes are single use and expire.
* Owners can delete a shop or their whole account (`owner_delete_account`) from the dashboard.
* You hold every owner's sales summaries (no customer names or receipts). That means looking after backups, access to the Supabase project, a privacy notice and a way to answer deletion requests. Supabase's free plan pauses idle projects and has no backups; use a paid plan for real shops.
