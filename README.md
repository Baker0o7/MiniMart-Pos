<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="110" alt="MiniMart POS" style="border-radius: 24px"/>

# 🛒 MiniMart POS

**Fast · Offline · Beautiful Android Point-of-Sale for Kenyan mini-markets**

Built with Kotlin + Jetpack Compose · Designed for Mambrui & beyond 🇰🇪

[![Release](https://img.shields.io/github/v/release/Baker0o7/MiniMart-Pos?color=00897B&label=Download%20APK&style=for-the-badge)](https://github.com/Baker0o7/MiniMart-Pos/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/Baker0o7/MiniMart-Pos/release.yml?label=Build&color=00897B&style=for-the-badge)](https://github.com/Baker0o7/MiniMart-Pos/actions)
[![Android](https://img.shields.io/badge/Android-7.0%2B-00897B?style=for-the-badge)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge)](https://kotlinlang.org)

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
- Camera barcode scanner (ML Kit — EAN-13, UPC, QR, Code128)
- Bluetooth / USB HID barcode scanner (pairs as keyboard)
- **∞ Continuous scan mode** — camera stays open for rapid scanning
- Product search by name or barcode with live dropdown
- Cart with quantity stepper, per-item discounts
- **Inclusive VAT** — tax extracted from price, not added on top
- Real-time total in top bar, animated subtitle

| Feature | Detail |
|---|---|
| Scanner | ML Kit camera + BT/USB HID keyboard |
| Continuous mode | ∞ tile toggles rapid scan |
| VAT | Inclusive (extracted from price) |
| Discounts | Per-item + global (RBAC gated) |

### 💳 Checkout & Payments
- **Cash** — quick-amount buttons (50/100/200/500/1000 KES), animated change display
- **M-Pesa** — ref number field, amount-due box
- **Credit** — customer wallet or buy-on-account (negative balance allowed)
- **Split payment** — combine credit + cash for one transaction
- Customer selector at checkout with search + contacts import + quick-add form
- Auto-opens cash drawer on cash payment (configurable)

### 👤 Customer Credit System
- Register customers: name, phone, email
- **Credit wallet** — add deposits, deduct on purchases
- **Buy on account** — customers take goods even with KES 0 balance (owe the store)
- Full transaction history per customer
- Quick credit-add (100 / 200 / 500 / 1000 KES)
- **Contact list import** — pick from phone contacts to auto-fill name + phone
- Credit Ledger screen — see all outstanding balances at a glance

### 🗃️ Cash Drawer
- ESC/POS `ESC p` kick via thermal printer RJ11 port (auto-detected)
- Direct Bluetooth cash drawer (configure MAC in Settings)
- Auto-opens on cash payment (toggle)
- Test button in Settings

### 📦 Inventory & Products
- Add/edit: price, cost, stock, category, SKU, unit, tax rate
- Supplier info + reorder quantity
- Batch number + expiry date tracking
- Color-coded expiry urgency badges
- Low-stock background alerts (WorkManager, 12h interval)
- Expiry notifications 1–3 months ahead (configurable)
- Stock adjustments with reason log

### 📊 Reports & Analytics
- Today's revenue with mini line chart + % vs yesterday
- Transaction count, average basket
- Top-selling items (fire icon)
- Expense tracking (11 categories) + P&L
- Sales history with debounced search

### 👥 Role-Based Access Control

| Permission | Owner | Manager | Cashier |
|---|:---:|:---:|:---:|
| Process sales | ✅ | ✅ | ✅ |
| Apply discounts | ✅ | ✅ | ❌ |
| View reports | ✅ | ✅ | ❌ |
| Edit products | ✅ | ✅ | ❌ |
| M-Pesa settings | ✅ | ✅ | Read-only |
| User management | ✅ | ❌ | ❌ |
| Cash drawer test | ✅ | ✅ | ❌ |

### 🔐 Security
- **Argon2id PIN hashing** (t=3, m=64MB, p=4) — OWASP recommended
- Auto-upgrades legacy SHA-256 hashes on first login
- Biometric login (fingerprint/face) with safe FragmentActivity cast
- 6-digit PIN keypad with show/hide toggle
- **3-strike lockout** — 30s countdown after 3 failed attempts

### 💾 Backup & Data
- One-tap backup to `Downloads/MiniMartPOS/backups/`
- Restore from saved backup list
- Share backup via USB, cloud, WhatsApp
- 100% offline — Room SQLite, no internet needed

### 🎨 UI / UX
- Deep dark teal theme — readable in bright retail lighting
- Custom numeric keypad on login (✓ Enter + ⌫ Backspace)
- Dashboard stats: revenue card with live line chart
- Animated credit balance breakdown at checkout
- Pull-to-refresh dashboard
- Spring-bounce bottom nav with raised QR center button
- Customizable quick action grid (hide/restore any card)

---

## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM · Clean Architecture · Repository |
| DI | Hilt |
| Database | Room 2.6 (SQLite, v7) |
| PIN Security | Argon2id (argon2-kt 1.4.0) |
| Camera | CameraX + ML Kit Barcode |
| Background | WorkManager |
| Preferences | DataStore |
| Printing | Bluetooth ESC/POS |
| Navigation | Navigation Compose |

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

Seeded with 5 demo products. PIN auto-upgrades to Argon2id on first login.

---

## 📁 Project Structure

```
app/src/main/kotlin/com/minimart/pos/
├── data/
│   ├── dao/         ProductDao · SaleDao · UserDao · ExpenseDao
│   │                ShiftDao · CustomerDao
│   ├── db/          AppDatabase (v7) · DatabaseCallback (seed)
│   ├── entity/      Product · Sale · SaleItem · User · Expense
│   │                Shift · Customer · CreditTransaction
│   └── repository/  (one per entity + SettingsRepository)
├── di/              DatabaseModule
├── printer/         ThermalPrinter · CashDrawerManager
├── scanner/         MLKitScanner · KeyboardScanner · BluetoothScannerManager
├── ui/
│   ├── screen/      17 screens (Login → CreditOverview)
│   ├── viewmodel/   Per-screen ViewModels
│   ├── theme/       DT color tokens
│   └── NavGraph.kt  Routes + BottomNavBar
├── util/            BackupManager · PdfReceiptGenerator · PinHasher · RoleManager
└── worker/          LowStockWorker · ExpiryAlertWorker
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
