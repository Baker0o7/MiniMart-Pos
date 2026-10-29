# Security notes

What protects a shop's till, and what does not yet.

## In place
* **Secrets** (Daraja keys, sync and cloud secrets) live in Android Keystore AES-256-GCM preferences (`@SecurePrefs`), never plain files or logs.
* **PINs** are hashed with Argon2id; sign-in has attempt limits and an idle timeout.
* **Network:** HTTPS only, system certificate authorities only (`network_security_config.xml`). Till-to-till sync runs on the shop Wi-Fi, encrypted and signed with the pairing code, with replay protection.
* **Untrusted input** (webhook, SMS, QR, sync peers) is validated before it reaches the database; SMS is read only from the sender ID `MPESA`.
* **Screen:** optional screenshot blocking (Settings > Privacy). On Android 12+ other apps' overlay windows are hidden while MiniMart is open, so nothing can sit over the PIN pad and capture taps.
* **Backups:** the cloud backup rules include only the database and plain settings, and cloud backup is skipped on phones that cannot encrypt it (no screen lock). Credential files are never backed up.
* **Release build:** R8 shrinking, `Log.v/d/i` removed, signed APKs only from the release workflow.
* **Audit log:** hash-chained and anchored, so deleted or edited entries show up in the integrity check.
* **Release check** (`scripts/release-checks.sh`) fails if the set of components other apps can reach changes (`.MainActivity`, `.mpesa.MpesaSmsReceiver`), or the manifest allows debugging or cleartext traffic. Add to that list on purpose, not by accident.
* Fields for secrets use the password keyboard (no suggestions or learning), and the web-hook address is copied as a sensitive clip.

## Known gaps
* **The shop database is not encrypted at rest** (customer names and phone numbers, sales). Android's own app sandbox and device encryption protect it. Encrypting it with SQLCipher would need a one-time migration of every phone's data and a tested rollback, so it is a planned item, not a quick change.
* A rooted phone can read the database and the settings file.
