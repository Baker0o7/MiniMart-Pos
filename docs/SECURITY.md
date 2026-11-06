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
