# Security notes

What protects a shop's till, and what does not yet.

## In place
* **Secrets** (Daraja keys, sync and cloud secrets) live in Android Keystore AES-256-GCM preferences (`@SecurePrefs`), never plain files or logs.
* **PINs** are hashed with Argon2id; sign-in has attempt limits and an idle timeout.
* **Network:** HTTPS only, system certificate authorities only (`network_security_config.xml`). Till-to-till sync runs on the shop Wi-Fi, encrypted and signed with the pairing code, with replay protection.
* **Untrusted input** (webhook, SMS, QR, sync peers) is validated before it reaches the database; SMS is read only from the sender ID `MPESA`.
* **Screen:** optional screenshot blocking (Settings > Privacy). On Android 12+ other apps' overlay windows are hidden while MiniMart is open, so nothing can sit over the PIN pad and capture taps.
* **Shop database encrypted at rest** (SQLCipher, AES-256, `util/DbEncryption.kt`). The key is random and lives only in the Android Keystore. The first start after the update converts the existing database into a new file, checks it (integrity, schema version, every table's row count) and only then swaps it in; if anything fails the plain database is left untouched and Settings says why. A plain copy of the old file is kept for 3 days, then deleted. The conversion is exercised on a real emulator (`DbEncryptionInstrumentedTest`, workflow *UI tests (emulator)*).
* **Backups:** automatic backups are copies of the encrypted file (readable on this phone only). Manual backups are plain snapshots, optionally passphrase-encrypted: use a passphrase for any backup that leaves the phone, and for moving to a new phone. The database is excluded from Android cloud backup and device transfer, because a copy could not be opened without the Keystore key. Credential files are never backed up.
* **Release build:** R8 shrinking, `Log.v/d/i` removed, signed APKs only from the release workflow.
* **Audit log:** hash-chained and anchored, so deleted or edited entries show up in the integrity check.
* **Release check** (`scripts/release-checks.sh`) fails if the set of components other apps can reach changes (`.MainActivity`, `.mpesa.MpesaSmsReceiver`), or the manifest allows debugging or cleartext traffic. Add to that list on purpose, not by accident.
* Fields for secrets use the password keyboard (no suggestions or learning), and the web-hook address is copied as a sensitive clip.

## In-app updater
* Contacts only `api.github.com` (latest release of `Baker0o7/MiniMart-Pos`) once a day (switch in Settings) and, only when the owner taps Download, the project's own `github.com/.../releases/download/` address. Redirects are followed by hand and only to GitHub's download hosts (`AppUpdate.isTrustedUrl`). Nothing about the shop or owner is sent.
* The file is size-checked, SHA-256-checked when GitHub publishes a digest, kept in the app's private `files/updates/` and given to Android's `PackageInstaller` (no file sharing). Android refuses it unless it is signed with the same key as the installed app, and the owner confirms on Android's own screen.
* Needs `REQUEST_INSTALL_PACKAGES`; the owner allows it once in Android settings. The receiver is not exported.
* Gap: an attacker who controls the GitHub repo *and* the signing key could ship a bad update. Keep the release keystore safe.

## Owner account (Google sign-in)
* Optional. Uses Android's Credential Manager and Firebase Authentication; no shop data, sales or customers are uploaded and no database is used.
* The account only identifies the owner; the till, PINs and backups do not depend on it.

## Hardening in 1.31.1
* **PIN lockout clock:** the lockout is now timed with the phone's since-boot clock (`LockoutClock`), so changing the phone's date no longer shortens it. After a restart the wall clock is used again, capped at the lockout length. Remaining gap: restart the phone *and* move the date forward in one go still ends a lockout early.
* **Web dashboard (removed):** the old Supabase web dashboard and its vendored script were removed with the move to Google sign-in and Firestore.
* **Known limit, M-Pesa SMS:** text-message sender names can be faked by a determined attacker, so a forged "MPESA" text could be logged as a payment. Where that matters, use the instant M-Pesa (webhook) or STK push, which come from Safaricom directly, and treat SMS-logged payments as a convenience, not proof.

## Known gaps
* **PIN lockout uses the phone's clock.** Setting the clock forward ends a lockout early. PINs are also checked against `PinPolicy` (no repeated digits, runs, repeated pairs or the commonest PINs), which makes the first guesses useless, but a short numeric PIN is still weak against someone with unlimited time.
* **If the Keystore key is lost** (a phone reset around the app, a damaged Keystore), the encrypted database cannot be opened. The app puts the file aside (`minimart_pos.db.unreadable-*`), starts empty and says so; restore a manual backup. Keep a manual backup off the phone.
* A rooted phone can read the database and the settings file.

## Sensitive field encryption (app level)
M-Pesa sender names, sender phones and raw messages are sealed with AES-256-GCM (`FieldCipher`, `enc1:` prefix) before they
reach the database, on top of SQLCipher. The key lives in the Android Keystore (`minimart_field_key_v1`) and never leaves the
phone. Old rows are sealed once at startup. If the Keystore is unavailable the app stores as before rather than losing sales.

Trade-offs: a manual backup restored on a *different* phone cannot read those three fields (code and amount are intact).
Customer phone numbers are not sealed because they are searched and looked up. Sync traffic is a separate protection.

## Release build checks (Nov 2026)
CI inspects the APK it builds (job "Release build smoke test (apk)"): not debuggable, signed with scheme v2/v3, an R8
`mapping.txt` exists (so Crashlytics stack traces can be read), and the permission list (printed on the run summary)
contains none of: READ_SMS, SEND_SMS, READ_PHONE_STATE, RECORD_AUDIO, location, SYSTEM_ALERT_WINDOW, READ_CALL_LOG,
WRITE_SETTINGS, MANAGE_EXTERNAL_STORAGE, QUERY_ALL_PACKAGES. `scripts/release-checks.sh` still guards the source manifest
(exported components, no debuggable or cleartext).
Reviewed and fine: `allowBackup` limited to plain settings by the backup rules, cleartext off, only system CAs trusted,
`FLAG_SECURE` available, `HIDE_OVERLAY_WINDOWS` requested, SMS receiver protected by `BROADCAST_SMS` and sender check.
Open: `Log.w/e` calls are kept in release builds on purpose (they carry exception text, never secrets, but check new ones);
RECEIVE_SMS and REQUEST_INSTALL_PACKAGES remain Play Store blockers (see AUDIT_2026-11.md).

## M-Pesa edge cases covered by tests (MpesaEdgeCasesTest)
Masked sender numbers (`0712***456`), odd spacing and line breaks, zero or lowercase-code messages, impossible dates,
text dates in the future (now fall back to the network time), amounts rounded to whole cents, control characters in names,
case and spacing in transaction codes, the 0.50 tolerance boundary, split sales, and the same message stored twice.
