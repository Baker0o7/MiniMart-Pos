# Runbook: releasing, watching, and fixing

For whoever looks after the app. Short on purpose.

## Before a release
1. `main` is green on CI (unit tests, debug build, release smoke build). The emulator workflow (*UI tests (emulator)*) is run by hand when anything touching the database, backups or encryption changed.
2. `scripts/bump-version.sh x.y.z`, replace the TODO in `CHANGELOG.md` with plain language for shop owners, `bash scripts/release-checks.sh`.
3. If the database changed: version bump, `MIGRATION_n_m`, a migration test. Never rely on destructive migration.
4. Install the build on ONE real phone with real data (after a backup) before telling shops to update. The checklist is in [QUALITY.md](QUALITY.md).
5. Run *Build & Release* (signing: [RELEASE_SIGNING.md](RELEASE_SIGNING.md)). Check `releases/latest` lists the five files.

## Rolling back
* Android will not install an older versionCode over a newer one. To go back, ship the fix as a NEW, higher version (`x.y.z+1`) built from the last good code.
* Data: every phone keeps a daily automatic backup (last 7) and a safety copy before any restore (`pre_restore_backup.db`). Settings → Data & Backup lists them.
* A database change cannot be rolled back by the app. That is why the migration test and the one-phone trial come first.

## When a shop says "something is wrong"
1. Ask them to open **Store health**. It is the shortest honest answer to "is my data OK?". Red rows come first.
2. **Share list** under "Problems the app handled" sends a short text: what failed, how often, when. It holds no sales, customers, payment details or secrets (long digit runs, codes and e-mails are masked).
3. After a crash the app offers the crash note. **About → Copy support info** gives version, phone and settings, nothing about the shop.
4. Backups: if the last good backup is old, make one first, then investigate.
5. Data problems listed by Store health (a sale that does not add up, a duplicate M-Pesa code, stock that disagrees with its batches) are reports, not repairs: nothing is changed automatically. Decide with the owner what is true, then correct it through the app (refund, void, stock adjustment) so the history shows it.

## What is and is not watched
* On the phone: crash notes (last 5), handled-problem list (last 60 distinct, newest first), Store health, the activity log.
* Not watched: anything off the phone. The opt-in Firebase reports (see [FIREBASE.md](FIREBASE.md)) are the only remote signal, and only when the owner turns them on.
* Nothing alerts a person. The owner has to look, which is why the daily owner report and the Home backup warning exist.

## Keys and secrets
* The release keystore is only in the repository secrets and the owner's safe copy. Never commit `*.jks` or passwords.
* The shop database key lives in the Android Keystore on each phone. It does not move with a backup: use a manual backup (optionally with a passphrase) to move shop data between phones.
