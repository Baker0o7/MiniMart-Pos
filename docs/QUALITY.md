# Quality goals: what is checked, what is not

Honest status. "Zero bugs" cannot be proven by tests; this lists what is enforced by code and CI, known gaps, and what only a real phone can confirm.

| Goal | Enforced by (CI) | Known gap |
|---|---|---|
| No critical financial bugs | `completeSale` is one transaction (payment check, FIFO cost, stock, credit); `Money` for sums; refund/void restore stock once; remote sales re-validated | Money columns are still `Double` in the database (cents migration not done) |
| No cross-shop leaks | Sync needs pairing; webhook and backend payloads are validated; hosted backend uses row-level security (SQL written, **never deployed**) | Database is not encrypted at rest (`docs/SECURITY.md`) |
| No lost sales offline | Sale/refund/void and their sync record are saved in **one transaction** (`SaleSyncAtomicityTest`); workers retry with `RetryPolicy`; conflicts are kept, not dropped | Sync needs both devices online at some point; a lost phone loses unsynced sales |
| Accurate profit and inventory | FIFO batch costs; stock never negative; refund restores stock once | Profit ignores lines with no recorded cost (sales made before costing existed) |
| Verified M-Pesa | Codes normalised and unique; a code cannot pay two sales; amount checked against the payment record; webhook/SMS sanitised | A cashier can still accept "manual" M-Pesa if the owner allows it |
| Reliable backups | Backups are integrity-checked (`PRAGMA integrity_check`) before being kept; encrypted option round-trips (`BackupCryptoTest`); restore validates first | Restore itself is not tested end to end on a device |
| Real-device testing | **Not done.** Nothing here has run on a phone | See checklist |

## Real-device checklist (run on a release build, two phones if you use sync)
1. Airplane mode on: ring up 3 sales (cash, M-Pesa, credit), refund one. Turn network on: all appear on the main device once, stock and totals match.
2. Ring up a sale, force-stop the app right after the receipt, reopen: sale present and still syncs.
3. Pay by M-Pesa with a code already used: refused. Wrong amount: refused or flagged.
4. Compare End-of-Day totals and profit against a hand count of 10 sales.
5. Count stock of 5 products, sell and refund some, recount.
6. Make a backup (with passphrase), wipe the app data, restore: products, sales, customers and stock match.
7. Pair a second shop's phone with a different main device: confirm neither sees the other's sales.
8. Printer, scanner, kiosk mode and rotation on the cheapest phone you support.
