# Security Policy

MiniMart POS handles sales, customer credit balances, staff PINs and M-Pesa payment records, so we take security reports seriously and are grateful to the people who send them.

## Supported versions

Only the **latest release** receives security fixes. Please check you can reproduce the problem on the newest version on the [Releases page](https://github.com/Baker0o7/MiniMart-Pos/releases/latest) before reporting.

## How to report a vulnerability

**Please do not open a public issue, pull request or discussion for a security problem.**

Report it privately through GitHub:

1. Open the repository's **Security** tab.
2. Choose **Report a vulnerability** (GitHub private vulnerability reporting).
3. Describe the problem as described below.

If that option is not available to you, contact the maintainer, [@Baker0o7](https://github.com/Baker0o7), on GitHub and ask for a private way to send the details. Do not put the details in the first message.

### What to include

- What the problem is and what an attacker could do with it.
- Exact steps to reproduce, or a short proof of concept.
- The app version (Settings), Android version and phone model.
- Whether it needs physical access, being on the same Wi-Fi, a rooted phone, or a crafted file or SMS.
- Any ideas for a fix, if you have them.

Please do not include real customer data. Use test data.

## What to expect

- We aim to **acknowledge your report within 7 days**.
- We aim to **confirm whether it is a vulnerability and share a plan within 14 days**, and to ship a fix as quickly as the risk requires. Serious problems that could lose money or expose data are fixed first.
- We will keep you informed and tell you when the fix is released.
- We will credit you in the release notes if you wish. Tell us how you would like to be named, or ask to stay anonymous.

This is a small, volunteer-run project: these are goals, not guarantees, but we will always respond.

## Coordinated disclosure

Please give us a reasonable chance to fix the problem before you tell anyone else. We ask for **up to 90 days** from your report, or until a fix is released, whichever is sooner. If a fix needs longer, we will tell you why and agree a date with you. Once a fix is out we are happy to publish an advisory together.

## Scope

In scope:

- The Android app (`app/`, `data/`, `core/`), including PIN login and roles, backups and restore, till-to-till LAN sync and its encryption and pairing, M-Pesa (Daraja and SMS) handling, receipt and file sharing, and local storage of secrets.
- The optional cloud dashboard and its SQL in `cloud/`.
- The build and release workflows in `.github/`, for example anything that could leak signing keys or tamper with released files.

Out of scope (unless you can show a realistic way around the protection):

- Attacks that need the phone to be already unlocked and in the attacker's hands, or a rooted or compromised device.
- Social engineering of shop staff, or theft of PINs by watching someone type.
- Spoofed SMS from ordinary numbers (the app only reads messages from the MPESA sender ID; sender names can still be forged on the network, which is why large payments should also be checked against the real M-Pesa balance).
- Missing hardening that has no demonstrable impact, and reports from automated scanners with no analysis.
- Denial of service by flooding your own Wi-Fi network.

## Safe harbour

If you act in good faith, follow this policy, avoid harming other people's data or shops, and give us time to respond, we will not take legal action against you and will work with you to fix the issue. Please test only on devices and accounts you own, never on a real shop's live data.

## How the app protects data (for context)

- PINs are stored hashed with Argon2id. Login attempts are limited, with growing lockouts.
- Payment credentials and the sync pairing code are kept in Android's encrypted storage and are not saved at all if secure storage is unavailable.
- Till-to-till sync bodies are encrypted (AES-256-GCM) and signed with a key derived from the pairing code, with rate limiting and lockout on the receiving phone.
- Backups can be encrypted with a passphrase. The app blocks cleartext HTTP and trusts only the system certificate authorities.
- Sensitive actions are written to a tamper-evident activity log.
