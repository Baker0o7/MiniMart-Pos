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

## Dashboard features and companion mode
* **Range** (7, 14 or 30 days), **store comparison** (each shop's share of revenue), **top products**, **Export CSV** of the daily figures for the selected shops, and an **English / Arabic** switch (Arabic flips the page right to left). The page refreshes itself every 5 minutes while open.
* **Companion mode:** the dashboard is an installable web page. Open it in Chrome on a phone and choose *Add to Home screen*; it then opens like an app. Only the page itself (`index.html`, icon) is cached by `sw.js`; no shop data and no sign-in is stored offline, so without a connection it shows the sign-in or an error. It needs `https` hosting (GitHub Pages and Netlify are fine).
* **Top products** come from the phones: `topProducts` (name, quantity, revenue, profit) was added to the snapshot in 1.31.0. Phones on older versions simply show "No data yet" until they update.
* CSV cells that start with `=`, `+`, `-` or `@` are prefixed so a spreadsheet will not run them as formulas.

## Shared product list between shops
Run `cloud/catalog_setup.sql` once, after `multitenant_setup.sql`. Then, in the app, on one shop choose Settings → Cloud dashboard → Shared product list → *Main shop (sends)*, and on the others *Follows main shop*. The phones share the list every few hours with the daily upload, or at once with the button. Only names, prices, tax and supplier details are shared; each shop keeps its own stock. The SQL was tested on a local PostgreSQL 16 with a stand-in for Supabase's `auth` schema (publish, fetch, one sender per owner, take-over, two owners kept apart, wrong secret, and the public key being unable to read the table).

The dashboard no longer needs the internet for its script: `vendor/supabase.js` is bundled. Upload the whole `cloud/dashboard` folder including `app.js` and `vendor/`.
