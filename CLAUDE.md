# MiniMart POS: notes for AI coding assistants

Kotlin / Jetpack Compose point-of-sale app for small shops (Kenya, M-Pesa). Offline first. Read
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the map; this file is the short list of rules.

## Build and test
```
gradle :core:test testDebugUnitTest      # all unit tests (core JVM, data on Robolectric, app)
gradle :data:lintDebug :app:lintDebug    # lint: errors fail, warnings do not
gradle assembleDebug
gradle :app:assembleDebugAndroidTest     # UI tests must still compile (they run on an emulator in CI)
bash scripts/release-checks.sh           # migration present, version + CHANGELOG entry, Locale in String.format
```
The Gradle wrapper jar is committed on purpose (CI validates it); Gradle 8.7. Package: `com.minimart.pos`.

## Modules
`:core` pure Kotlin, no Android imports (business rules + tests). `:data` Room, repositories, settings. `:app` UI, Hilt,
workers, printer, sync. Put new rules in `:core` and test them there.

## Rules that have bitten before
* **Database change = version bump + `MIGRATION_n_m` in `AppDatabase.kt` + a test.** Never rely on destructive migration.
* `String.format` always takes a `Locale` (`Locale.US` / `Locale.ROOT`); the release check enforces it.
* Money is added with `Money` (cent-exact); never sum `Double`s for totals that are shown or stored.
* Compose: `FlowRow`/`FlowColumn` need `@OptIn(ExperimentalLayoutApi::class)`; `LocalLifecycleOwner` comes from
  `androidx.lifecycle.compose`; test assertions like `assertDoesNotExist` are methods, not imports.
* `Room.runInTransaction` with a Kotlin lambda is ambiguous: pass `Runnable { }`.
* Close order matters when writing files through stacked streams: finish/sync **before** the `use {}` block closes.
* Anything from the network, SMS, QR codes or sync peers is untrusted: validate (`MpesaWebhook.sanitize`,
  `MpesaSmsParser`, `SyncPayloads`) before it touches the database.
* Secrets go in `@SecurePrefs` preferences, never plain files or logs. Do not widen `file_paths.xml` or the network
  security config.
* Errors shown to people use `ErrorAdvice.explain(error, "what we were doing")`, not `localizedMessage`.
* UI: touch targets at least 48dp; give icon-only buttons a `contentDescription`; use `Role` semantics for custom
  clickable controls; keep text colours from `DT` so the contrast test covers them.
* Colours: only the `DT` palette. The app is dark only on purpose (hard-coded `Color.White` text is used widely).

## Releasing
Bump `versionCode`/`versionName` in `app/build.gradle.kts`, add a `## [x.y.z] - date` section at the top of
`CHANGELOG.md` (plain language, what changed for the shop), run `scripts/release-checks.sh`, push to `main`, wait for CI,
then run the *Build & Release* workflow (it signs with the keystore in repository secrets: see `docs/RELEASE_SIGNING.md`). Release notes live in the changelog, not the README.

## Style
Short KDoc on non-obvious classes saying *why*; plain-language user-facing strings (shop owners, not engineers);
small pure functions with tests over clever code; no new dependency without a reason in the PR.
