# Quality goals: what is checked, what is not

Honest status. "Zero bugs" cannot be proven by tests; this lists what is enforced by code and CI, known gaps, and what only a real phone can confirm.

| Goal | Enforced by (CI) | Known gap |
|---|---|---|
| No critical financial bugs | `completeSale` is one transaction (payment check, FIFO cost, stock, credit); `Money` for sums; refund/void restore stock once; remote sales re-validated | Money columns are still `Double` in the database (rounded to cents on write and by the v17 migration; shown totals add in exact cents). A full whole-cents schema is deliberately not done: large, hard to undo, never run on a phone |
| No cross-shop leaks | Sync needs pairing; webhook and backend payloads are validated; hosted backend uses row-level security (SQL written, **never deployed**) | The database is encrypted at rest (SQLCipher, key sealed by the Android Keystore): a lost key means the file is set aside and the app starts empty, so keep manual backups |
| No lost sales offline | Sale/refund/void and their sync record are saved in **one transaction** (`SaleSyncAtomicityTest`); workers retry with `RetryPolicy`; conflicts are kept, not dropped | Sync needs both devices online at some point; a lost phone loses unsynced sales |
| Accurate profit and inventory | FIFO batch costs; stock never negative; refund restores stock once | Profit ignores lines with no recorded cost (sales made before costing existed) |
| Verified M-Pesa | Codes normalised and unique; a code cannot pay two sales; amount checked against the payment record; webhook/SMS sanitised | A cashier can still accept "manual" M-Pesa if the owner allows it. A refunded sale keeps its code; a voided one releases it |
| Reliable backups | Backups are integrity-checked (`PRAGMA integrity_check`) before being kept; encrypted option round-trips (`BackupCryptoTest`); restore validates first | Restore is swapped in atomically and tested on an emulator (`BackupRestoreInstrumentedTest`); not on a physical phone |
| Real-device testing | **Not done.** Nothing here has run on a phone | See checklist |

## Known gaps (kept short on purpose)
* **Cash-up** counts opening float plus cash sales (and the cash share of split sales). It does not know about cash paid out of the drawer for expenses, or refunds paid from the drawer for sales made in an earlier shift: the database records neither where an expense was paid from nor when a refund happened. Fixing it needs a schema change.
* **Sync:** a till sale the main phone refuses (for example a reused M-Pesa code) is kept as a conflict on the till and shown there, but the main phone's owner is not alerted.
* **Observability** is on the phone only: crash notes, the handled-problem list and Store health. Nothing is sent anywhere unless the owner shares it or turns on the opt-in reports.
* **Integrity check** (Store health) catches totals, stock, duplicate M-Pesa codes and batch mismatches after the fact; it does not stop them.

## Real-device checklist (run on a release build, two phones if you use sync)
1. Airplane mode on: ring up 3 sales (cash, M-Pesa, credit), refund one. Turn network on: all appear on the main device once, stock and totals match.
2. Ring up a sale, force-stop the app right after the receipt, reopen: sale present and still syncs.
3. Pay by M-Pesa with a code already used: refused. Wrong amount: refused or flagged.
4. Compare End-of-Day totals and profit against a hand count of 10 sales.
5. Count stock of 5 products, sell and refund some, recount.
6. Make a backup (with passphrase), wipe the app data, restore: products, sales, customers and stock match.
7. Pair a second shop's phone with a different main device: confirm neither sees the other's sales.
8. Printer, scanner, kiosk mode and rotation on the cheapest phone you support.

## Hardware and UI test coverage (added)
`ScanBufferTest` (scanner timing/filtering), `EscPosCommandsTest` (printer and drawer bytes, hostile text),
`MpesaFieldEncryptionTest`, `FieldCipherTest`, `TileColumnsPickerUiTest` (emulator). Not covered: real printers, drawers and scanners on a phone.

## Accounting export, forecast accuracy, stale data and stability (added)
* **Accountant export** (`AccountingExport`, Reports > table icon): a double-entry journal that is checked to balance, a profit and loss,
  and the VAT collected, as CSV for Excel or Sheets. Cent-exact (`Money`); text cells that could run as a formula are defused. Refunded and voided
  sales are not income and are left out. Sales made before item costs were recorded show a warning that profit is overstated.
* **Forecast accuracy** (`DemandForecast.accuracy`): the forecast is replayed on the last week and the product shows how far off it would have been.
* **Stale data** (`SyncFreshness`): a till that has not synced for 12 hours gets a note on the health screen; after 48 hours (or never) Home shows a
  "tap to sync" banner. Disagreements between phones are counted from the activity log.
* **Printer fallbacks** (`PrinterHistory`): print results are remembered; one automatic reconnect is tried before a print is reported as failed;
  repeated failures show on the hardware screen with the PDF/WhatsApp fallback.
* **Crash-loop guard** (`CrashLoopGuard`, `StartupGuard`): three starts within five minutes holds background work back for two minutes so the till screen stays usable.
Not covered: any of this on a real phone, printer or second till.
