# Architecture

MiniMart POS is an offline-first point-of-sale app for small shops. Everything a till needs works without a network;
the network is only used for optional things (syncing tills over Wi-Fi, the cloud dashboard, M-Pesa prompts, instant
M-Pesa payments).

## Modules

| Module | Kind | Holds | Depends on |
|---|---|---|---|
| `:core` | pure Kotlin/JVM (no Android) | Business rules that can be tested in seconds: money maths, M-Pesa SMS parsing and reconciliation, store-health and hardware checks, end-of-day steps, error advice, archive policy, sync crypto, LAN HTTP | nothing |
| `:data` | Android library | Room database (entities, DAOs, migrations), repositories, DataStore settings, secure preferences, PIN hashing | `:core` |
| `:app` | Android application | Hilt wiring, ViewModels, Compose screens and navigation, printer, scanner, sync server/client, workers, cloud client | `:core`, `:data` |

Rule of thumb: if a rule can be written without an Android class, it belongs in `:core` with a unit test. Screens and
ViewModels only gather facts and show results.

## Layers inside `:app`

```
Compose screen  ->  ViewModel (Hilt)  ->  Repository (in :data)  ->  DAO / Room
      ^                   |                       |
      |                   +-- :core rules         +-- DataStore, KeystorePrefs
      +-- StateFlow state
```

* **Screens** (`ui/screen`) are stateless where possible and take callbacks; navigation lives in `ui/NavGraph.kt`.
* **ViewModels** (`ui/viewmodel`) expose one `StateFlow` of an immutable state class.
* **Theme**: `ui/theme/DarkTheme.kt` holds the palettes and the `DT` object that screens read colours from;
  `Palette.adapt` applies the "Deep black" and "High contrast" options. A test checks WCAG contrast for every
  accent and option combination.

## Data

* Room database `minimart_pos.db`, version in `data/.../db/AppDatabase.kt`. **Every version bump needs a matching
  `MIGRATION_n_m`** (`scripts/release-checks.sh` fails otherwise). Versions below 8 fall back to destructive recreation.
* Money is stored as `Double` in the database and handled cent-exactly with `Money` (`:core`) when added up.
* Sales: `sales` (header) + `sale_items` (lines, with FIFO cost) + `sale_item_costs` (which stock batches a line used).
  Stock comes in through `stock_batches` (purchase orders, manual receiving).
* Customer credit: `customers.creditBalance` always equals the last `credit_transactions.balanceAfter`; the integrity
  check (`IntegrityChecker`) verifies this together with sale totals, stock and duplicate M-Pesa codes.
* Old data: `DatabaseArchiver` writes sales older than a chosen age (at least 12 months) to a gzip CSV, verifies it,
  and only then deletes them. Weekly `MaintenanceWorker` drops delivered sync records older than 30 days.

## Payments

* **Cash / card / credit**: recorded at checkout in `CartViewModel` / `SaleRepository`.
* **M-Pesa** has three independent sources, all landing in the `mpesa_payments` table (unique per transaction code):
  1. SMS from sender `MPESA` (`MpesaSmsReceiver` + `MpesaSmsParser`),
  2. Daraja STK push started from checkout (`DarajaApiClient`),
  3. Instant webhook: Safaricom -> Supabase Edge Function -> cloud inbox -> `MpesaWebhookClient`
     (see [MPESA_WEBHOOK.md](MPESA_WEBHOOK.md)).
* A sale can only claim a code that exists in `mpesa_payments` and is not already used (`PaymentValidator`,
  `MpesaVerifier`). `MpesaReconciler` lists payments with no sale and sales with no payment.

## Sync and cloud

* **LAN sync**: one phone runs `SyncServer`; others use `SyncClient`. Bodies are AES-GCM encrypted and HMAC-signed with
  a pairing secret (`SyncCrypto`, `SyncAuth`); requests go through `LanHttp` raw sockets because the network security
  config blocks all cleartext HTTP.
* **Cloud dashboard**: the owner's own Supabase project (`cloud/supabase_setup.sql`, `cloud/dashboard`). The app
  registers with a setup code and pushes a snapshot with a per-store secret. `cloud/mpesa_webhook.sql` adds the
  instant M-Pesa inbox.

## Background work (WorkManager)

`SyncWorker`, `CloudUploadWorker`, `AutoBackupWorker`, `LowStockWorker`, `ExpiryAlertWorker`, `MaintenanceWorker`
(weekly, idle + charging), `MpesaWebhookWorker` (15 min). All are scheduled in `MiniMartApp.onCreate`.

## Security model

* Secrets (Daraja keys, sync secret, cloud secret) live in `KeystorePrefs` (AES-256-GCM, Android Keystore key); if the keystore is
  unavailable the app uses in-memory preferences (fail closed) rather than plain files.
* PINs are hashed with Argon2id; owner-only actions go through `PermissionGuard` / `OwnerGateViewModel`.
* The activity log is hash-chained (`AuditLogger`). `FileProvider` only exposes the folders in `res/xml/file_paths.xml`.
* No cleartext traffic and only system CAs (`network_security_config.xml`).

## Tests

| Where | Runs on | Covers |
|---|---|---|
| `core/src/test` | plain JVM | all `:core` rules |
| `data/src/test` | Robolectric + in-memory Room | DAOs, repositories, archiver, whole-shop scenarios (`EndToEndShopTest`), a year of trading with timing limits and index checks (`LargeDataTest`) |
| `app/src/test` | JVM | printer text, theme contrast, view-model state, parsers |
| `app/src/androidTest` | emulator (`instrumented.yml`) | Compose UI tests (components, Home quick-action tiles) |

## Where to look

| I want to... | Start at |
|---|---|
| change how a sale is saved | `data/.../repository/SaleRepository.kt` |
| change a health check | `core/.../util/StoreHealth.kt`, `HardwareDiagnostics.kt` |
| add a setting | `SettingsRepository` (key + flow), then a section in `ui/screen/Settings*.kt` |
| add a screen | `ui/screen`, a route in `NavGraph.kt`, a ViewModel in `ui/viewmodel` |
| change the database | entity + DAO + `AppDatabase` version + migration + a test |
