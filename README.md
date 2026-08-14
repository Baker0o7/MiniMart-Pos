<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="110" alt="MiniMart POS" style="border-radius: 24px"/>

# 🛒 MiniMart POS

**Fast · Offline · Secure Android Point-of-Sale for Kenyan mini-markets**

Built with Kotlin + Jetpack Compose 🇰🇪

[![Release](https://img.shields.io/github/v/release/Baker0o7/MiniMart-Pos?color=00897B&label=Download%20APK&style=for-the-badge)](https://github.com/Baker0o7/MiniMart-Pos/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/Baker0o7/MiniMart-Pos/release.yml?label=Build&color=00897B&style=for-the-badge)](https://github.com/Baker0o7/MiniMart-Pos/actions)
[![Android](https://img.shields.io/badge/Android-8.0%2B-00897B?style=for-the-badge)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge)](https://kotlinlang.org)
[![Room DB](https://img.shields.io/badge/Room-v12-00897B?style=for-the-badge)](https://developer.android.com/jetpack/androidx/releases/room)
[![Tests](https://img.shields.io/badge/Unit%20Tests-JUnit4-00897B?style=for-the-badge)](https://junit.org/junit4/)

</div>

---

## 📱 Screenshots

| Login | Dashboard | New Sale |
|:-----:|:---------:|:--------:|
| ![Login](screenshots/login.jpg) | ![Dashboard](screenshots/dashboard.jpg) | ![New Sale](screenshots/new_sale.jpg) |

| Checkout | Credit Payment | Customer Search |
|:--------:|:-------------:|:---------------:|
| ![Checkout](screenshots/checkout.jpg) | ![Credit](screenshots/checkout_credit.jpg) | ![Customer](screenshots/customer_search.jpg) |

---

## ✨ Features

### 🛍️ New Sale
- Camera barcode scanner (ML Kit — EAN-13, UPC, QR, Code-128, Code-39, Data Matrix)
- Bluetooth / USB HID barcode scanner support
- **Weighing scale support (PLU)** — decodes variable-weight EAN-13 barcodes,
  auto-calculates price from weight × price/kg, weight persisted in sale records
  and correctly shown (not "×1") on both the in-app and shareable PDF receipt
- **Continuous scan mode** with animated laser overlay, corner brackets,
  green flash confirmation, and live scan counter badge
- Keyboard "Next" navigation flows through every field in the Add Product form
- Product search by name or barcode with live dropdown
- Cart with quantity stepper, per-item discounts
- **Inclusive VAT** — tax extracted from price, not added on top
- **Cent-exact totals** — the checkout subtotal/discount/total math runs on an
  internal `Money` value class (Long cents) rather than raw `Double`, avoiding
  the floating-point rounding drift that repeated addition across many cart
  lines can otherwise produce
- **Cart badge** on bottom nav shows pending item count when navigating away
- Guarded against double-tap: rapid double-tapping "Complete Sale" can't create
  two sales from one transaction

### 💳 Checkout & Payments
- **Cash** — quick-amount buttons, real change calculation
- **M-Pesa** — ref number field
- **Credit** — customer wallet or buy-on-account (negative balance allowed)
- **Split payment** — combine credit + cash in one transaction, with proper
  error feedback if it fails (shown as an on-screen banner, not silently dropped)
- Customer selector with search + contacts import — debtors (customers who
  owe money) are clearly flagged in red, not shown the same as a zero balance
- Cash drawer auto-opens on cash payment (configurable)
- Haptic feedback confirms every completed sale
- All money displays respect the app's configurable currency setting
  (no screen is hardcoded to "KES")

### 👤 Customer Credit System
- Register customers with name, phone, email
- **Credit wallet** — deposits, deductions on purchases
- **Buy on account** — negative balance allowed
- Full transaction history per customer
- **Credit Ledger** — all non-zero balances at a glance
  - 🔴 Debtors (negative, owe money) shown first with "OWES [currency] X" in red
  - 🟢 Wallet balances shown in green
  - Two summary stats: "Owed to Shop" + "Wallet Credit"

### 🌐 Multi-Device LAN Sync
- No internet, no cloud — pure local WiFi sync
- **Pairing code authentication** — 6-digit code shown on server device,
  required on client device, compared in constant time (not vulnerable to a
  timing side-channel), and **rate-limited** (5 failed attempts locks out
  further guesses for 30s — a 1,000,000-combination code can't be brute-forced
  at LAN speed)
- **Pairing secrets encrypted at rest** — both this device's own code and any
  remembered peer code are stored via Android Keystore-backed
  EncryptedSharedPreferences, not plaintext
- **Duplicate-safe** — retrying a sync (dropped connection, double-tapping
  "Sync Now") can't apply the same remote change twice
- Server device: toggle "Act as Sync Server", share the displayed code
- Client device: enter server IP + pairing code → Sync Now

### 🗃️ Cash Drawer
- ESC/POS kick via thermal printer RJ11 port
- Direct Bluetooth cash drawer support
- Auto-opens on cash payment (toggle) · Test button in Settings

### 📦 Inventory & Products
- Add/edit: price, cost, stock, category, SKU, unit, tax rate
- Supplier info + reorder quantity · Batch number + expiry date
- Color-coded expiry urgency badges · Low-stock background alerts (WorkManager)
- Stock adjustments with reason log
- **PLU / Weighing scale toggle** per product (PLU code + price/kg)
- Negative price/stock can't be saved (validated at both the UI and repository layer)

### 📊 Reports & Analytics
- Revenue vs yesterday (real % comparison, flips red when down)
- Dashboard auto-refreshes at midnight — "today" always means today
- Transaction count, average basket, top-selling items
- **Reports & Expenses** use proper calendar week (Mon–Sun) and calendar month,
  not rolling 7/30-day windows
- Sales History: color-coded payment method chips (💵 Cash / 📱 M-Pesa / 🤝 Credit / 🔀 Split)
- **Quick Void** on COMPLETED sales from the history list (Manager+)

### 👥 Role-Based Access Control

| Permission | Owner | Manager | Cashier |
|---|:---:|:---:|:---:|
| Process sales | ✅ | ✅ | ✅ |
| Apply discounts | ✅ | ✅ | ❌ |
| View reports | ✅ | ✅ | ❌ |
| Edit products | ✅ | ✅ | ❌ |
| Void sales | ✅ | ✅ | ❌ |
| Multi-device sync | ✅ | ✅ | ❌ |
| User management | ✅ | ❌ | ❌ |

Every route above is enforced by a route-level `AccessGuard` that bounces
unauthorized users, even on direct navigation. Cannot remove the last active
Owner account (permanent lockout protection).

### 🔐 Security
- **Argon2id PIN hashing** (t=3, m=64MB, p=4), auto-upgrades legacy SHA-256 on
  login, constant-time comparison on both paths
- **Biometric login** — bound to one explicitly opted-in user per device
  (Settings → Account). Any fingerprint on the device cannot authenticate as
  an arbitrary username.
- **Persisted 3-strike lockout** — survives force-close, task-kill, and device
  reboot
- **15-minute inactivity auto-logout**
- **Persistent, thread-safe audit log** at `files/audit.log` covering logins,
  logouts, completed sales, discounts, and credit usage
- **Sync pairing secrets** — rate-limited, constant-time compared, and
  encrypted at rest (see Multi-Device Sync above)
- **At-rest database protection**: relies on Android's File-Based Encryption
  (FBE), hardware-backed and enabled by default since Android 7.0. App-level
  SQLCipher encryption was evaluated twice and reverted both times due to
  native-library crashes on startup; FBE was judged the safer,
  zero-maintenance choice for this app.

### 💾 Backup & Data
- One-tap backup to `Downloads/MiniMartPOS/backups/`
- **Restore requires explicit two-step confirmation** — selecting a backup
  shows exactly what will be lost before anything is overwritten, then the
  app automatically restarts (WAL/SHM files handled correctly)
- 100% offline — Room SQLite v12, no internet required for core operation

### 🎨 UI / UX
- Deep dark teal theme — readable in bright retail lighting
- Time-of-day Swahili greeting: *Habari ya asubuhi / mchana / jioni*
- Consistent gradient top bar across all screens
- Press-scale animation on dashboard action cards
- Color-coded payment method chips throughout
- Consistent destructive-action dialogs app-wide (styled red confirm +
  bordered cancel, clear "cannot be undone" copy)
- Animated scanner overlay: pulsing border, sweeping laser, corner brackets
- Pull-to-refresh on dashboard (updates today + yesterday revenue)

---

## 🧪 Testing

Local JVM unit tests (`app/src/test`) cover the core financial calculation
logic — no emulator needed:

```bash
./gradlew test
```

| Test file | Covers |
|---|---|
| `MoneyTest` | Cent-exact arithmetic, the classic `0.1 + 0.2 != 0.3` Double failure case, rounding, clamping |
| `CartUiStateTest` | Checkout subtotal/discount/total math, the discount-floor regression, weighed-item pricing |
| `PluDecoderTest` | Weighing-scale barcode decode/reject cases, price calculation |

---

## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM · Clean Architecture · Repository |
| DI | Hilt |
| Database | Room 2.6 (SQLite v12), Android FBE at rest |
| Money | Custom `Money` value class (Long cents) for checkout-critical math |
| PIN Security | Argon2id (argon2-kt 1.4.0) |
| Sensitive Storage | Jetpack Security (`EncryptedSharedPreferences`, Keystore-backed) |
| Camera | CameraX + ML Kit Barcode |
| Sync | Custom HTTP server/client over LAN WiFi, pairing-code authenticated |
| Background | WorkManager (low-stock + expiry alerts) |
| Preferences | DataStore + SharedPreferences |
| Printing | Bluetooth ESC/POS |
| Navigation | Navigation Compose |
| State | `SavedStateHandle` for process-death recovery |
| Testing | JUnit4 (local unit tests) |

---

## 🚀 Getting Started

```bash
git clone https://github.com/Baker0o7/MiniMart-Pos.git
cd MiniMart-Pos
./gradlew assembleDebug
```

**First-launch credentials**

| Field | Value |
|---|---|
| Username | `admin` |
| PIN | `1234` |

---

## 📁 Project Structure

```
app/src/main/kotlin/com/minimart/pos/
├── data/
│   ├── dao/          ProductDao · SaleDao · UserDao · ExpenseDao
│   │                 ShiftDao · CustomerDao · SyncDao
│   ├── db/           AppDatabase (v12) · DatabaseCallback (seed)
│   │                 AppMigrations (v8→v9→v10→v11→v12)
│   ├── entity/       Product · Sale · SaleItem · User · Expense
│   │                 Shift · Customer · CreditTransaction · SyncLog
│   └── repository/   (one per entity + SettingsRepository)
├── di/               DatabaseModule (incl. @SecurePrefs qualifier)
├── printer/          ThermalPrinter · CashDrawerManager
├── scanner/          MLKitScanner · KeyboardScanner · BluetoothScannerManager
├── sync/             SyncServer · SyncClient
├── ui/
│   ├── screen/       16 screens (Login → CreditOverview)
│   ├── viewmodel/    Per-screen ViewModels + SessionViewModel · SyncViewModel
│   ├── theme/        DT color tokens
│   └── NavGraph.kt   Routes + BottomNavBar (cart badge) + AccessGuard
├── util/             Money · BackupManager · PdfReceiptGenerator · PinHasher
│                     RoleManager · SessionManager · AuditLogger · PluDecoder
│                     UiResult
└── worker/           LowStockWorker · ExpiryAlertWorker

app/src/test/kotlin/com/minimart/pos/
├── util/             MoneyTest · PluDecoderTest
└── ui/viewmodel/     CartUiStateTest
```

---

## ⚙️ CI/CD Signing Secrets

```
SIGNING_KEY_ALIAS      = minimart
SIGNING_KEY_PASSWORD   = android
SIGNING_STORE_PASSWORD = android
```

---

<div align="center">

Built with ❤️ for Kenyan mini-markets 🇰🇪

[![Download APK](https://img.shields.io/badge/Download%20APK-00897B?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Baker0o7/MiniMart-Pos/releases/latest)

</div>
