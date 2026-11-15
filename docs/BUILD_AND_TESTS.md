# Build times, modules, tests, hardware and observability

Measured on GitHub's `ubuntu-24.04` runners (Nov 2026). CI is the only place the Android build is compiled.

## Build times
| Job | Before | Now |
|---|---|---|
| Release smoke test (bundle + APK, one after the other) | about 580-620 s | two jobs side by side, about 300 s each |
| Unit tests (tests step ~118 s) | about 305 s | unchanged |
| Debug build with Firebase plugins | about 156 s | unchanged |
| Lint | about 113 s (inside the unit-test job) | unchanged |

The whole pipeline is as slow as its slowest job, so splitting the release build roughly halves the wait.
Not done, on purpose: the Gradle configuration cache (`gradle.properties` keeps it off; AGP 9 plus the Firebase and
Hilt plugins have not been proven with it here). Try it on a branch and watch CI before turning it on.

## Modules
`:app` holds about 89% of the code (about 24k lines). Splitting it into feature modules would need a local compiler to
be safe, so the approach is: move pure logic to `:core` a few files at a time, each with its tests.
Moved this round (same packages, so no import changes): `SyncAuth`, `LanAddress`, `PrinterText`, `RestockPlanner`,
`ScanBuffer`, `BarcodeNormalizer`. They now test on a plain JVM in seconds.
Next candidates: anything in `:app` whose imports are only `java.*`/`kotlin.*`
(find them with `grep -L "^import android\|^import androidx\|^import dagger" $(git ls-files 'app/src/main/*.kt')`).
Later, once a local build is possible: `:feature-printer`, `:feature-sync`, `:feature-mpesa` behind interfaces in `:core`.

## Testing strategy
* Rules live in `:core` (JUnit 5, seconds). Database behaviour in `:data` (Robolectric). UI in `:app`.
* Schema: `SchemaGoldenTest` compares the current schema with `schema/v<N>.sql` and upgrades every older saved schema.
* UI: fingerprint snapshot tests (`ui/snapshot`) catch cut-off Arabic text; goldens are printed in CI on failure.
* Compose UI tests compile in CI and run on an emulator in `instrumented.yml`.
* Gaps: no test on a real phone (printer, camera, SMS, two-phone sync), Arabic wording needs a native reader.

## Hardware compatibility and graceful degradation
Already in place: Bluetooth connect watchdog (10 s) with secure-to-insecure fallback, hardware diagnostics in
`HardwareDiagnostics`, keyboard-wedge scanner as a fallback to the camera, SMS and manual M-Pesa entry when Daraja
is unreachable. Open: thermal printer Arabic output unverified; StrongBox not used (falls back to the normal Keystore);
low-RAM phones untested.

## Observability
Opt-in Firebase Crashlytics plus `Telemetry.nonFatal` (class name and what we were doing, never the message).
`ProblemRecorder` keeps a masked local list; Store health shares it. Nothing leaves the phone without opt-in.
