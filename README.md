<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120" alt="MiniMart POS Logo"/>

# 🛒 MiniMart POS

**A beautiful, fully offline Android Point-of-Sale app**  
Built 100% in Kotlin + Jetpack Compose for mini-markets, kiosks & convenience stores in Kenya and beyond.

[![Release](https://img.shields.io/github/v/release/Baker0o7/MiniMart-Pos?color=00897B&label=Latest%20APK)](https://github.com/Baker0o7/MiniMart-Pos/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/Baker0o7/MiniMart-Pos/release.yml?label=Build&color=00897B)](https://github.com/Baker0o7/MiniMart-Pos/actions)
[![Android](https://img.shields.io/badge/Android-7.0%2B-00897B)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-00897B)](LICENSE)

</div>

---

## 📱 Screenshots

| Login | Dashboard | New Sale |
|-------|-----------|----------|
| ![Login](screenshots/login.jpg) | ![Dashboard](screenshots/dashboard.jpg) | ![New Sale](screenshots/new_sale.jpg) |

| Checkout | Inventory | Settings |
|----------|-----------|----------|
| ![Checkout](screenshots/checkout.jpg) | ![Inventory](screenshots/inventory.jpg) | ![Settings](screenshots/settings.jpg) |

| Sales History | Low Stock | Reports |
|---------------|-----------|---------|
| ![History](screenshots/sales_history.jpg) | ![Low Stock](screenshots/low_stock.jpg) | ![Reports](screenshots/reports.jpg) |

> **To add screenshots:** Create a `screenshots/` folder in the repo root and add `.jpg` images with the names above.

---

## ✨ Features

### 🛍️ Sales & Cart
- **Barcode scanning** — ML Kit camera (EAN-13/UPC/QR/Code128) + USB/Bluetooth HID keyboard scanners
- **Continuous scan mode** — ∞ toggle keeps camera open for rapid multi-item scanning
- **Smart cart** — quantity stepper, per-item & global discounts (manager/owner only), tax calculation
- **Payments** — Cash (with change calculator & quick-amount buttons), M-Pesa (with ref capture)
- **Refund / Void** — One-tap from receipt screen, automatically restores stock

### 📦 Inventory & Products
- **Product management** — Barcode, name, price, cost price, stock, category, SKU, unit
- **Supplier info** — Name, phone number, reorder quantity per product
- **Batch & expiry tracking** — Batch number, expiry date with color-coded urgency badges
- **Low stock reminders** — With supplier name, phone, one-tap Call & WhatsApp reorder
- **Stock adjustments** — +/- with reason log
- **Expiry alerts** — Background notifications 1/2/3 months before expiry (configurable)

### 💰 Payments & M-Pesa
- **Cash** — Change calculation, quick-amount preset buttons (50/100/200/500/1000)
- **M-Pesa** — Paybill, Till (Buy Goods), Withdrawal/Agent number, Account name
- **Cashier view** — Shows till number read-only; editing locked to managers/owners

### 📊 Reports & Analytics
- Daily, weekly, monthly revenue & transaction counts
- Average basket size, top-selling products
- Expense tracking with 11 categories + P&L
- Sales history with debounced search by receipt#, M-Pesa ref, or notes

### 👥 Users & Access Control (RBAC)
| Permission | Owner | Manager | Cashier |
|---|:---:|:---:|:---:|
| Process sales | ✅ | ✅ | ✅ |
| View reports | ✅ | ✅ | ❌ |
| Edit prices / add products | ✅ | ✅ | ❌ |
| Apply discounts | ✅ | ✅ | ❌ |
| M-Pesa settings (edit) | ✅ | ✅ | Read-only |
| User management | ✅ | ❌ | ❌ |
| Backup / restore | ✅ | ✅ | ❌ |

- **6-digit PIN** with biometric (fingerprint/face) fallback
- **3-strike lockout** — 30 second countdown after 3 failed attempts

### 🔒 Security & Data
- **100% offline** — No internet required. All data in local Room/SQLite DB
- **Local backup** — One-tap backup to `Downloads/MiniMartPOS/backups/`
- **Restore** — Select from list of saved backups
- **Share backup** — via USB OTG, cloud, or any share target

### 🖨️ Receipts & Sharing
- **PDF receipts** — Generated locally using Android `PdfDocument` API
- **WhatsApp share** — Send PDF receipt directly to WhatsApp (falls back to share sheet)
- **Thermal printer** — Bluetooth ESC/POS 80mm receipts via `BluetoothSocket`

### 📅 Shifts
- Clock in / clock out with opening/closing float
- Sales totals per cashier per shift
- Cash discrepancy tracking

### 🎨 UI/UX
- Dark teal theme (`DT` color system) — beautiful in any lighting
- Animated bottom navigation with spring-bounce icons
- Pull-to-refresh on dashboard
- Screen slide+fade transitions (280ms)
- Quick action card customization — hide/rearrange dashboard cards

---

## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.0 |
| **UI** | Jetpack Compose + Material3 |
| **Architecture** | MVVM + Clean Architecture + Repository pattern |
| **DI** | Hilt |
| **Database** | Room 2.6 (SQLite) |
| **Async** | Kotlin Coroutines + Flow |
| **Camera** | CameraX + ML Kit Barcode Scanning |
| **Background** | WorkManager (low-stock & expiry alerts) |
| **Preferences** | DataStore |
| **Printing** | Bluetooth BluetoothSocket (ESC/POS) |
| **Navigation** | Navigation Compose |
| **Build** | Gradle 8.7 + AGP 8.6.1 |

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug or later
- Android SDK 24+ (Android 7.0)
- Java 17

### Build & Run
```bash
git clone https://github.com/Baker0o7/MiniMart-Pos.git
cd MiniMart-Pos
./gradlew assembleDebug
```

### Default Credentials
| Field | Value |
|---|---|
| Username | `admin` |
| PIN | `1234` |
| Role | Owner (full access) |

> **First launch** seeds 5 sample products: Coca-Cola, Lays Chips, Mentos, Vaseline, Marlboro.

### Download APK
👉 **[Latest Release →](https://github.com/Baker0o7/MiniMart-Pos/releases/latest)**

---

## 📁 Project Structure

```
app/src/main/kotlin/com/minimart/pos/
├── data/
│   ├── dao/          ProductDao, SaleDao, UserDao, ExpenseDao, ShiftDao
│   ├── db/           AppDatabase (v6), DatabaseCallback (seed data)
│   ├── entity/       Product, Sale, SaleItem, User, Expense, Shift
│   └── repository/   ProductRepository, SaleRepository, UserRepository,
│                     SettingsRepository, ExpenseRepository, ShiftRepository
├── di/               DatabaseModule (Hilt providers)
├── printer/          ThermalPrinter (Bluetooth ESC/POS)
├── scanner/          MLKitScanner, KeyboardScanner, ScannerManager
├── ui/
│   ├── screen/       17 screens (Login, Dashboard, Scanner, Checkout,
│   │                 Receipt, Products, Inventory, Reports, Expenses,
│   │                 Settings, Shifts, Users, SalesHistory, LowStock,
│   │                 UserManagement, ScannerCart, ReceiptView)
│   ├── viewmodel/    Per-screen ViewModels with StateFlow
│   ├── theme/        DT color palette + MiniMartTheme
│   └── NavGraph.kt   Navigation + bottom nav bar
├── util/             Extensions, PdfReceiptGenerator, BackupManager,
│                     RoleManager
└── worker/           LowStockWorker, ExpiryAlertWorker, SyncWorker
```

---

## 🗄️ Database Schema (v6)

**Products** — barcode, sku, name, price, costPrice, stock, lowStockThreshold, category, unit, taxRate, supplierName, supplierPhone, reorderQuantity, batchNumber, expiryDate  
**Sales** — receiptNumber, subtotal, taxAmount, discountAmount, totalAmount, amountPaid, changeGiven, paymentMethod, status, cashierId, mpesaRef, notes  
**SaleItems** — productId, productName, productBarcode, unitPrice, quantity, lineDiscount  
**Users** — username, pinHash (SHA-256), displayName, role, isActive  
**Expenses** — amount, category, description, cashierId, date  
**Shifts** — cashierId, clockIn, clockOut, openingFloat, closingFloat, totalSales, status  

---

## 🔧 Configuration

### Signing (CI/CD)
Set these GitHub Actions secrets:
```
SIGNING_KEY_ALIAS     = minimart
SIGNING_KEY_PASSWORD  = android
SIGNING_STORE_PASSWORD = android
```

### M-Pesa Setup
Settings → M-Pesa Configuration:
- **Account Name** — Business name on receipts
- **Paybill Number** — For business payments
- **Till Number** — Buy Goods (shown read-only to cashiers)
- **Withdrawal Number** — Agent number for end-of-day cash-out

---

## 📜 License

```
MIT License — Copyright (c) 2025 Baker0o7
```

---

<div align="center">
Made with ❤️ for Kenyan mini-markets 🇰🇪
</div>
