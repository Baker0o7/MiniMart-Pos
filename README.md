<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" alt="MiniMart POS" style="border-radius: 24px"/>

# 🛒 MiniMart POS

**A fast, beautiful, fully offline Android Point-of-Sale system**  
Built in Kotlin + Jetpack Compose for mini-markets, kiosks & convenience stores in Kenya 🇰🇪

[![Release](https://img.shields.io/github/v/release/Baker0o7/MiniMart-Pos?color=00897B&label=Latest%20APK&style=for-the-badge)](https://github.com/Baker0o7/MiniMart-Pos/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/Baker0o7/MiniMart-Pos/release.yml?label=Build&color=00897B&style=for-the-badge)](https://github.com/Baker0o7/MiniMart-Pos/actions)
[![Android](https://img.shields.io/badge/Android-7.0%2B-00897B?style=for-the-badge)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge)](https://kotlinlang.org)

</div>

---

## 📱 Screenshots

| Login | Home | New Sale |
|:-----:|:----:|:--------:|
| ![Login](screenshots/login.jpg) | ![Home](screenshots/dashboard.jpg) | ![New Sale](screenshots/new_sale.jpg) |

| Checkout | Customers | Inventory |
|:--------:|:---------:|:---------:|
| ![Checkout](screenshots/checkout.jpg) | ![Customers](screenshots/customers.jpg) | ![Inventory](screenshots/inventory.jpg) |

| Shift Management | Reports | Settings |
|:----------------:|:-------:|:--------:|
| ![Shifts](screenshots/shifts.jpg) | ![Reports](screenshots/reports.jpg) | ![Settings](screenshots/settings.jpg) |

> **Adding screenshots:** Drop `.jpg` files named as above into the `screenshots/` folder.

---

## ✨ Feature Highlights

### 🛍️ Sales & Cart
- **Camera barcode scanner** (ML Kit: EAN-13, UPC, QR, Code128) + USB/Bluetooth HID keyboard scanners
- **Continuous scan mode (∞)** — camera stays open for rapid multi-item scanning
- **Smart cart** — quantity stepper, per-item & global discounts (RBAC gated), real-time totals
- **Inclusive VAT** — tax extracted from price, not added on top
- **3 payment methods:** Cash (change calculator + quick-amount buttons) · M-Pesa · Customer Credit
- **Refund / Void** — one tap from receipt, auto-restores stock

### 👤 Customers & Credit
- Register customers (name, phone, email)
- **Credit wallet** — add deposits, track balance, full transaction history
- Link customers to any sale and pay via credit balance
- Per-customer stats: total spent, visit count, credit balance
- Quick credit add (100 / 200 / 500 / 1000 KES buttons)

### 📦 Inventory & Products
- Add / edit products: price, cost price, stock, category, SKU, unit, tax rate
- **Supplier info** — name, phone, reorder quantity
- **Batch & expiry tracking** — batch number + expiry date, color-coded urgency badges
- **Low-stock reminders** — one-tap Call or WhatsApp reorder to supplier
- **Expiry alerts** — background notifications 1 / 2 / 3 months before expiry (configurable)
- Stock adjustments with reason log

### 💰 M-Pesa Integration
- Paybill · Till (Buy Goods) · Withdrawal / Agent number · Account name
- Cashiers see till number read-only; editing locked to managers/owners

### 🗃️ Cash Drawer & Hardware
- **Cash drawer via thermal printer** (ESC/POS `ESC p` kick — drawer plugs into RJ11 port)
- **Direct Bluetooth cash drawer** — enter MAC address in Settings
- Auto-opens on cash payment (toggle in Settings)
- Test button to open drawer without a sale
- **Bluetooth barcode scanner** — HID scanners pair as keyboards, detected by device class or name (Honeywell, Zebra, Datalogic, Newland, Sunmi)

### 🖨️ Receipts & Printing
- **PDF receipts** — generated locally via Android `PdfDocument`
- **WhatsApp share** — send receipt directly (falls back to share sheet)
- **Thermal printer** — Bluetooth ESC/POS 80mm receipts

### 📊 Reports & Analytics
- Daily / weekly / monthly revenue, transactions, average basket
- Top-selling products (fire emoji — today's chart)
- Expense tracking (11 categories) + P&L overview
- Sales history with debounced search (receipt#, M-Pesa ref, notes)

### 👥 Users & Access Control (RBAC)

| Permission | Owner | Manager | Cashier |
|---|:---:|:---:|:---:|
| Process sales | ✅ | ✅ | ✅ |
| Apply discounts | ✅ | ✅ | ❌ |
| View reports | ✅ | ✅ | ❌ |
| Edit products / prices | ✅ | ✅ | ❌ |
| M-Pesa settings (edit) | ✅ | ✅ | Read-only |
| Backup / restore | ✅ | ✅ | ❌ |
| User management | ✅ | ❌ | ❌ |
| Cash drawer test | ✅ | ✅ | ❌ |

- **6-digit PIN** + biometric fallback (fingerprint / face)
- **3-strike lockout** — 30s countdown after 3 failed attempts

### 📅 Shift Management
- Clock in / out with opening & closing float
- Cash discrepancy tracking per cashier
- Shift summary: cash sales, M-Pesa sales, transactions, duration

### 🔒 Security & Backup
- **100% offline** — SQLite / Room, no internet required
- **One-tap backup** to `Downloads/MiniMartPOS/backups/`
- **Restore** from saved backup list
- **Share backup** via USB OTG, cloud, or any share target

### 🎨 UI / UX
- Deep dark teal theme throughout — readable in bright retail lighting
- Gradient stat cards with live mini line chart on dashboard
- 3-column quick action cards with icon + description + arrow
- Spring-bounce bottom nav animation
- Animated checkout change display (slide transition)
- Pull-to-refresh dashboard
- Quick action card customisation — hide/restore any card

---

## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.0 |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM · Clean Architecture · Repository pattern |
| **DI** | Hilt |
| **Database** | Room 2.6 (SQLite, v7) |
| **Async** | Kotlin Coroutines + Flow |
| **Camera** | CameraX + ML Kit Barcode Scanning |
| **Background** | WorkManager (low-stock & expiry alerts, 12h interval) |
| **Preferences** | DataStore |
| **Printing** | Bluetooth BluetoothSocket (ESC/POS 80mm) |
| **Navigation** | Navigation Compose |
| **Build** | Gradle 8.7 · AGP 8.6.1 |

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2+)
- JDK 17
- Android SDK 24+ (Android 7.0)

### Build
```bash
git clone https://github.com/Baker0o7/MiniMart-Pos.git
cd MiniMart-Pos
./gradlew assembleDebug
```

### Download APK
👉 **[Latest Release →](https://github.com/Baker0o7/MiniMart-Pos/releases/latest)**

### First-launch credentials
| Field | Value |
|---|---|
| Username | `admin` |
| PIN | `1234` |
| Role | Owner (full access) |

Seeded with 5 demo products on first install.

---

## 📁 Key Project Structure

```
app/src/main/kotlin/com/minimart/pos/
├── data/
│   ├── dao/          ProductDao · SaleDao · UserDao · ExpenseDao
│   │                 ShiftDao · CustomerDao
│   ├── db/           AppDatabase (v7) · DatabaseCallback (seed)
│   ├── entity/       Product · Sale · SaleItem · User · Expense
│   │                 Shift · Customer · CreditTransaction
│   └── repository/   (one per entity + SettingsRepository)
├── di/               DatabaseModule · HiltWorkerFactory config
├── printer/          ThermalPrinter · CashDrawerManager
├── scanner/          MLKitScanner · KeyboardScanner · BluetoothScannerManager
├── ui/
│   ├── screen/       17 screens
│   ├── viewmodel/    Per-screen ViewModels (StateFlow + Hilt)
│   ├── theme/        DT color palette
│   └── NavGraph.kt
├── util/             BackupManager · PdfReceiptGenerator · RoleManager
└── worker/           LowStockWorker · ExpiryAlertWorker
```

---

## 🗄️ Database Schema (v7)

**products** · **sales** · **sale_items** · **users** · **expenses** · **shifts** · **customers** · **credit_transactions**

---

## ⚙️ Configuration

### CI/CD signing secrets (GitHub Actions)
```
SIGNING_KEY_ALIAS      = minimart
SIGNING_KEY_PASSWORD   = android
SIGNING_STORE_PASSWORD = android
```

### M-Pesa
Settings → M-Pesa: paybill, till, withdrawal number, account name.

### Cash Drawer
Settings → Cash Drawer: toggle auto-open on cash sale, optional direct BT MAC address.

---

## 📜 License

```
MIT License — © 2025 Baker0o7
```

---

<div align="center">
Built with ❤️ for Kenyan mini-markets 🇰🇪
<br/><br/>
<a href="https://github.com/Baker0o7/MiniMart-Pos/releases/latest">
<img src="https://img.shields.io/badge/Download%20APK-00897B?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"/>
</a>
</div>
