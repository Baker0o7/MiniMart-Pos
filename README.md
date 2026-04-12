# 🛒 MiniMart POS

A lightweight, fully offline Android Point-of-Sale app built in **100% Kotlin + Jetpack Compose** for small mini-markets, kiosks, and convenience stores.

---

## ✨ Features

| Module | What's implemented |
|---|---|
| **Barcode Scanning** | ML Kit camera (EAN-13/UPC/QR/Code128) + USB/Bluetooth HID keyboard scanners |
| **Cart & Checkout** | Scan-to-add, quantity stepper, per-item & global discounts, hold state |
| **Payments** | Cash (with change calculator), M-Pesa (with ref capture), Card |
| **Thermal Printer** | Bluetooth ESC/POS 80mm receipts via BluetoothSocket |
| **Product Management** | Add/edit/delete products, categories, stock tracking, low-stock alerts |
| **Reports** | Daily / weekly / monthly revenue, transaction count, avg basket, top sellers |
| **User Auth** | PIN-based login, cashier + manager + owner roles |
| **Settings** | Store name, currency, tax rate, receipt footer, M-Pesa paybill, dark mode |
| **Offline-first** | 100% Room/SQLite — zero internet required for daily ops |
| **Cloud sync** | WorkManager stub ready for Supabase/Firebase integration |
| **CI/CD** | GitHub Actions → signed APK + AAB on every version tag |

---

## 🏗️ Architecture

```
com.minimart.pos
├── data/
│   ├── db/          AppDatabase (Room), DatabaseCallback (seed)
│   ├── dao/         ProductDao, SaleDao, UserDao
│   ├── entity/      Product, Sale, SaleItem, CartItem, User
│   └── repository/  ProductRepository, SaleRepository, UserRepository, SettingsRepository
├── domain/          (use cases — expand as app grows)
├── ui/
│   ├── theme/       MiniMartTheme (Material 3, dynamic color, dark mode)
│   ├── screen/      LoginScreen, DashboardScreen, ScannerCartScreen,
│   │                CheckoutScreen, ReceiptScreen, ProductListScreen,
│   │                ReportsScreen, SettingsScreen
│   ├── viewmodel/   CartViewModel, ProductViewModel, DashboardViewModel,
│   │                AuthViewModel, ReportsViewModel
│   └── NavGraph.kt  Type-safe Compose Navigation
├── di/              DatabaseModule (Hilt)
├── scanner/         MLKitScanner.kt, KeyboardScanner.kt, ScannerManager.kt
├── printer/         ThermalPrinter.kt (ESC/POS over BluetoothSocket)
├── worker/          SyncWorker.kt (WorkManager)
└── util/            Extensions.kt (money formatting, date helpers, haptics)
```

**Stack:** Kotlin • Jetpack Compose • Material 3 • MVVM + Clean Architecture  
**DI:** Hilt • **DB:** Room + DataStore • **Scanner:** ML Kit + CameraX  
**Printer:** BluetoothSocket ESC/POS • **Async:** Kotlin Flows + Coroutines

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17
- Android device or emulator (API 26+)

### Clone & Run
```bash
git clone https://github.com/Baker0o7/minimart-pos.git
cd minimart-pos
./gradlew assembleDebug
```

### Default Login
| Field    | Value   |
|----------|---------|
| Username | `admin` |
| PIN      | `1234`  |

> ⚠️ Change the default PIN immediately after first login via Settings → Users.

### Seeded Products (for testing)
The DB pre-loads 5 sample products on first install:
- Coca-Cola 500ml — KES 50
- Lays Chips 50g — KES 30
- Mentos Roll — KES 15
- Vaseline 250ml — KES 120
- Marlboro Red 20s — KES 350

---

## 🖨️ Thermal Printer Setup

1. Pair your Bluetooth 80mm printer in Android **Settings → Bluetooth**
2. Open MiniMart POS → **Settings → Pair Printer**
3. Select your printer from the list
4. Tap **Test Print** to verify

Compatible with any ESC/POS 80mm Bluetooth printer (common brands: Epson TM, GOOJPRT, Xprinter, MUNBYN).

---

## 📦 Adding Products

**Manual:** Products → `+` button → fill barcode, name, price, stock  
**Scan barcode:** On the Add Product dialog, scan the product's barcode directly  
**Bulk import (Pro):** Products → Import → select CSV file

CSV format:
```csv
barcode,name,price,costPrice,stock,category,unit
6001007519173,Coca-Cola 500ml,50.00,35.00,48,Drinks,pcs
```

---

## 🔑 Release Signing (CI/CD)

Add these secrets to your GitHub repository (`Settings → Secrets`):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -i minimart-release.jks` output |
| `KEY_ALIAS` | Your key alias |
| `KEY_PASSWORD` | Key password |
| `STORE_PASSWORD` | Keystore password |

Then tag a release:
```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions will build and attach the signed APK + AAB to the release automatically.

---

## 🗺️ Roadmap

### MVP (done ✅)
- [x] Barcode scanning (ML Kit + HID keyboard)
- [x] Cart with discounts
- [x] Cash / M-Pesa / Card checkout
- [x] Thermal receipt printing
- [x] Product CRUD + stock management
- [x] Daily/weekly/monthly reports
- [x] PIN login with roles
- [x] Signed release CI/CD

### Pro Features (next)
- [ ] CSV bulk product import
- [ ] WhatsApp receipt sharing
- [ ] Supabase cloud backup
- [ ] Multi-cashier shift tracking
- [ ] Customer loyalty points
- [ ] Revenue dashboard charts (MPAndroidChart)
- [ ] Low-stock email/SMS alerts
- [ ] Multi-branch support

---

## 📄 License

MIT — free for personal and commercial use.
