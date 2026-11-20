# Two-shop pilot plan

Goal: run the app in two real shops for 4 weeks and find what breaks before rolling out wider. Nothing here needs new
software: it uses Store health, the daily summary and the shadow ledger already in the app (1.33.0).

## Before day 1 (each shop)
- Install the signed release APK; finish setup including the encrypted backup, and confirm the backup file is saved off the phone.
- Enter real products and opening stock; do a test sale and a test refund, then check Store health is all green.
- Agree who the shop contact is and how they send the daily summary (WhatsApp is fine).
- Keep the old way of recording sales (notebook) for the first 2 weeks as a cross-check.

## Every day
- Shop: at closing, do End of day, then Store health > "Send today's summary".
- Pilot lead: read the summary. Any `[PROBLEM]` line is answered the same day; `[CHECK]` lines are looked at within 2 days.
- Compare the app's total with the notebook total (first 2 weeks). Differences of any size are written down.

## Every week
- Restore a backup on a spare phone once and confirm sales and stock match.
- Count cash and stock by hand for a sample of items; compare with the app.
- Collect crash reports ("Send crash report") and list what the shop found confusing, in the shop's own words.

## What we measure
Sales per day per shop, mismatches against the notebook, number of Store health problems (and how long they stay open),
crashes, sync failures between tills, M-Pesa payments not matched, days with no backup, shop owner's confusion points.

## Stop and go
- **Stop at once** (fix before continuing): any sale total that differs from the notebook for no known reason, a ledger
  mismatch in Store health, lost data after a restart or update, a crash during a sale more than once.
- **Go to wider rollout** when for 2 consecutive weeks in both shops: no unexplained money differences, no ledger
  mismatches, no crash during a sale, backups made every day, and the shopkeepers say they would not go back.
- Otherwise extend the pilot by 2 weeks and fix the top three issues first.

## Not covered
The pilot does not prove Arabic wording (needs a native reader), thermal-printer Arabic, or multi-phone conflict alerts
unless the shops actually use those.

## Changes made for the pilot (code side, 2026-11)
- "Require verified code" for M-Pesa is now **on by default**.
- Any start-up "cent repair" of a sale total is recorded and shown in Store health as a **problem** (14-day window).
- Database v21: sync now records the stock actually applied, so a refund of a sale that was short of stock gives back only what was taken.
- A refund refused during sync now alerts the main phone's owner, like a refused sale does.
- Every backup that leaves the phone needs a passphrase; the end-of-day backup stays in private storage.
- Every GitHub Action is pinned to a commit SHA; CI retries only dependency-download failures.

## Still needs people, phones and accounts
Device matrix (32-bit, 16 KB-page, tablet, printers, drawer, scanner, kiosk), live Daraja and real callbacks with real SMS samples,
the Supabase deployment with a two-account RLS test, and the signing-job environment protection (adds a manual approval to each release).
Features are frozen for the pilot; releases are batched weekly.
