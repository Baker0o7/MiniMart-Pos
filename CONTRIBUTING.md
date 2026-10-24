# Contributing to MiniMart POS

Thanks for helping. MiniMart POS is a point-of-sale app that real shops use to take money and track stock, so the bar for correctness is high and the bar for friendliness is higher. Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

By contributing you agree that your contribution is licensed under the [Apache License 2.0](LICENSE), the same as the project (see section 5 of the license).

## Ways to help

- **Report a bug** or suggest an improvement by opening an issue. Say what you did, what you expected, what happened, the app version (Settings) and your phone model. Screenshots help; please blur customer names, phone numbers and M-Pesa codes.
- **Report a security problem** privately. Follow [SECURITY.md](SECURITY.md); never open a public issue for it.
- **Send a fix or feature** as a pull request (see [Pull request process](#pull-request-process)). For anything bigger than a small fix, open an issue first so we can agree on the approach before you spend time on it.
- **Improve the docs**, wording on screen, or translations of plain-language messages.
- **Pick a good first contribution** from the [roadmap](docs/ROADMAP.md): small tasks with a clear "done". Comment on the issue (or open one) so two people do not do the same thing.
- **Review pull requests and try releases** on a real phone: telling us what confused you is a contribution.

## Project layout

| Module | What it is | Rules |
|---|---|---|
| `:core` | Pure Kotlin/JVM logic: money, barcode decoding, backup and sync cryptography, M-Pesa SMS parsing, store-health and end-of-day rules, error messages | **No Android imports.** Everything here must be unit-testable on a laptop. |
| `:data` | Room database, DAOs, entities, migrations, repositories, settings | Money and stock rules live here, not only in screens. Tests run on Robolectric. |
| `:app` | Jetpack Compose UI, ViewModels, Hilt wiring, printer, scanner, sync, M-Pesa, workers | Keep screens thin; put decisions in `:core` or `:data` where they can be tested. |
| `cloud/` | Optional multi-store web dashboard and Supabase SQL | Separate from the Android build. |

## Building

You need:

- **JDK 17**
- **Android SDK** with platform 35 (set `ANDROID_HOME`, or let Android Studio manage it)
- **Gradle 8.7**. The Gradle wrapper jar is not committed, so either install Gradle 8.7 and use `gradle` as below, or run `gradle wrapper --gradle-version 8.7` once to create `./gradlew`.

```bash
git clone https://github.com/Baker0o7/MiniMart-Pos.git
cd MiniMart-Pos

gradle assembleDebug                      # debug APK in app/build/outputs/apk/debug/
gradle :core:test testDebugUnitTest       # all unit tests (core, data on Robolectric, app)
gradle :data:lintDebug :app:lintDebug     # lint: errors fail the build, warnings do not
gradle :app:assembleDebugAndroidTest      # make sure the Compose UI tests still compile
gradle :app:connectedDebugAndroidTest     # run the UI tests (needs an emulator or phone)
bash scripts/release-checks.sh            # cheap guards CI also runs
```

The default login on a fresh install is `admin` / `1234`; you are asked to change the PIN immediately.

Release builds are signed by the **Build & Release** workflow using repository secrets. You never need signing keys to contribute.

## Coding standards

**General**

- Kotlin with the official style (`kotlin.code.style=official`). Use the formatting Android Studio gives you with the default Kotlin style.
- Prefer clear names and small functions over comments. Write a comment when the *why* is not obvious, especially for money, stock and security code.
- Do not add a library for something a few lines can do. Every dependency is a download, a licence and an attack surface.
- No unused code, no commented-out code, no TODOs without an issue.

**Money, stock and data**

- Never do money arithmetic with raw `Double` shortcuts when `Money` (in `:core`) exists. Reject non-finite and negative amounts at the data layer, not only in the UI.
- Anything that changes several tables (a sale, a delivery, a void) runs in one database transaction.
- `String.format` must always be given a `Locale` (phones set to other languages otherwise print wrong digits). `scripts/release-checks.sh` fails the build if you forget.
- **Changing the database schema:** bump the version in `AppDatabase`, add `MIGRATION_<old>_<new>` to `AppMigrations`, and add a test. Never rely on destructive migration; it wipes a shop's records. The release check fails if a migration is missing.

**Security and privacy**

- Secrets (M-Pesa keys, the sync pairing code) go in encrypted storage only. If secure storage is unavailable, fail closed; never fall back to plaintext.
- No cleartext HTTP. Internet calls use `https://`. Till-to-till sync uses the `LanHttp` client with encrypted, signed bodies.
- Do not log personal data, PINs, codes or keys.
- Do not add Android permissions without a clear reason written in the pull request.
- Treat all input as untrusted: scanned barcodes, SMS text, synced data and backup files.

**User-facing text**

- Plain words a shop assistant understands. Say what happened *and what to do next*. Use `ErrorAdvice` for failures instead of showing a raw exception message.
- Keep screens usable on small phones and with large fonts. Give icons a content description.

## Tests

- **Every bug fix gets a test** that fails without the fix. Every new rule in `:core` or `:data` gets tests.
- Put pure logic in `:core` and test it with JUnit. Put database behaviour in `:data` tests (they use a real in-memory Room database). Use whole-shop scenarios (`EndToEndShopTest`) when several pieces must agree.
- Compose UI tests live in `app/src/androidTest`. They are compiled on every push and run on an emulator by the **UI tests (emulator)** workflow.
- Large-data behaviour is covered by `LargeDataTest` (a year of sales; timings appear on the CI run summary). If you change a query, check that it still uses an index there.
- Tests must be deterministic: no real clocks, network or sleeping where a parameter will do.

## Pull request process

1. **Fork** the repository and create a branch from `main` (`fix/short-description` or `feature/short-description`).
2. **Keep it focused.** One problem per pull request; unrelated clean-ups go in their own.
3. **Make it pass locally:** unit tests, lint and `bash scripts/release-checks.sh` (commands above).
4. **Update the docs:**
   - Add a line under the right version in [CHANGELOG.md](CHANGELOG.md) for anything a shop owner or developer would notice. Write it for the shop owner, in plain words.
   - Update the README if a feature or setup step changed.
5. **Open the pull request** against `main`. Describe what changed and why, how you tested it, and whether you ran it on a real device (say so honestly if you did not). Link the issue it fixes. Screenshots for UI changes, with private data blurred.
6. **CI must be green.** The workflow runs release checks, unit tests (all modules), lint, and compiles the UI tests.
7. **Review.** A maintainer will review; expect questions, especially for money, stock, security and migrations. Push follow-up commits to the same branch. Please be patient and kind, and we will do the same.
8. **Merge.** Maintainers squash or merge once approved. Releases (version bump and tag) are done by maintainers.

Maintainers bumping a version change `versionCode` and `versionName` in `app/build.gradle.kts` and add the matching `## [x.y.z]` section to the changelog; the release check fails if that section is missing.

## Commit messages

A short imperative summary (about 70 characters), then a blank line and, if useful, a paragraph on *why*. Example: `Reject non-finite amounts in expense form`.

## Questions

Open an issue labelled **question** (see also [SUPPORT.md](SUPPORT.md)). There are no silly questions about a codebase this size.
