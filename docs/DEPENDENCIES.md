# Dependencies and updates

MiniMart POS handles money and shop data, so libraries are added slowly and kept current deliberately.

## Where versions live

* `gradle/libs.versions.toml` is the version catalogue: change a version in one place.
* A few build-only or one-off libraries are declared directly in a module's `build.gradle.kts`, with a comment saying why.
* **No new dependency without a reason in the pull request** (what it does, why the platform or Kotlin cannot, size cost, licence, how active it is).

## How updates arrive

| What | How | Cadence |
|---|---|---|
| Libraries and GitHub Actions | Dependabot pull requests (`.github/dependabot.yml`), grouped by family | monthly |
| Known vulnerabilities in a pull request | *Dependency review* workflow fails on high severity | every pull request |
| Gradle wrapper jar | CI validates it against official Gradle releases | every push |
| Kotlin, KSP, Compose compiler | **by hand**, together (Dependabot only sends their patch releases) | when a feature or fix needs it, and at least twice a year |

## Reviewing an update

1. CI is green (unit tests, lint, release checks, instrumented tests still compile).
2. Read the release notes for anything that changes behaviour we rely on: Room migrations, WorkManager, DataStore, camera and barcode scanning, Compose Material.
3. For `androidx.room`: confirm the exported schema did not change; a schema change needs a migration (see `CLAUDE.md`).
4. For anything touching payments, sync or storage encryption, also run the UI tests workflow and try a sale on a real phone.
5. Merge, then note it under the next version in `CHANGELOG.md` only if a shop owner would notice.

## Updating Kotlin, KSP and Compose together

Pick a Kotlin version, then the KSP release built for it (`<kotlin>-<ksp>`), and the Compose BOM that supports it.
Change the three versions in `gradle/libs.versions.toml` in one pull request and run the full build and tests.

Two build workarounds to know about (both are in the build files with a comment):

* `gradle.properties` sets `android.builtInKotlin=false` and `android.newDsl=false`: AGP 9 opts in to its own Kotlin
  support and new DSL, which this project has not moved to yet. Remove them when the plugins are migrated.
* `app/build.gradle.kts` forces `kotlin-metadata-jvm` to the Kotlin version, because Hilt otherwise reads Kotlin 2.4
  classes with an older metadata reader and fails ("Provided Metadata instance has version 2.4.0").
* Compose BOM 2026 needs `compileSdk 37`; `targetSdk` stays 35 until the behaviour changes of newer Android are checked.

## Watch list

* `androidx.security:security-crypto` is kept for **one purpose only**: reading secrets saved by versions before 1.18.7
  (`LegacySecurePrefsMigrator`). Delete the migrator and the dependency in a later release once most phones have updated.
* Libraries on alpha or beta releases (for example `androidx.biometric`) are re-checked each release for a stable version.
* Check what is outdated locally: `gradle dependencies --configuration releaseRuntimeClasspath` and compare with the catalogue.
