# Changelog

All notable changes to MiniMart POS are listed here, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/).

Every release is published, with its signed APKs, on the [Releases page](https://github.com/Baker0o7/MiniMart-Pos/releases).

## [1.33.2] - 2026-11-14

- **Fewer silent failures:** if the cash drawer does not open after a cash sale, or the Home numbers cannot load, the problem is now written to the problem list (Store health), where it can be seen and sent, instead of being ignored.

## [1.33.1] - 2026-11-14

- **Refused till sales are no longer silent:** when the main phone cannot accept a sale from another till (for example not enough stock), it now shows a notification with the receipt number and the reason, so the sale can be checked and sold again here. Before, only the till heard about it.

## [1.33.0] - 2026-11-13

- **Exact money everywhere:** shift cash-up, Z-reports, the daily report, Reports, Analytics, the accounting files and the cloud report now add each sale in whole cents, so a total can never be a cent out. Totals the database adds up for the dashboard and staff report use the same whole cents. Store health now says if any sale's cents ever disagree with its price, and the app repairs them when it starts (normally there is nothing to repair).
- **Sync is safer:** a phone that keeps sending a wrong pairing code is now locked out on its own, with waits that grow from 30 seconds up to 15 minutes. Before, one stranger on the Wi-Fi could lock out every real till. Sync messages that contain Arabic, accents or emoji are now read exactly byte for byte, and broken or oversized messages get a clear refusal instead of a confusing error.
- **Setup now includes a backup:** a new brand-new shop must choose a backup passphrase, make an encrypted backup file, send it somewhere that is not the phone, and confirm it before setup can finish.
- **A second record of every sale:** each sale, refund and void is also written to a separate ledger on the phone. Store health compares it with the sales list and says if they ever disagree. It only detects a mismatch; it does not change any sale. (Database version 20; older phones are upgraded on first start.)
- **Pilot help:** Store health has two new buttons: "Send today's summary" (sales today, health checks, app version, in plain words) and "Send crash report" (the latest crash, shared only when you choose). Nothing is sent automatically.

## [1.32.8] - 2026-11-12

- **More of the app in Arabic:** the messages you see after saving, deleting or failing (products, customers, suppliers, purchase orders, expenses, shifts, sign-in and fingerprint), the Analytics card titles and weekday names, Customers (labels and buttons), User management, End of day, Archive, Store health buttons, Hardware check buttons, the Activity log filters, the M-Pesa Daraja and web address settings, and the backup and Bluetooth messages in Settings.
- Spoken labels for TalkBack (call supplier, add customer, go back and others) are now in Arabic too.

## [1.32.7] - 2026-11-12

- **More of the app in Arabic:** M-Pesa payments, Stock insights, Today, Analytics, the Settings summaries (printer, cash drawer, M-Pesa Daraja, sync, daily report), Account, Activity log, Hardware check, Store health, Barcode labels, and the period buttons (Today, Week, Month) all follow the language now. Counts use the right Arabic wording ("two payments", "no payments").
- **Zero is easier to read in Arabic:** a plain zero now shows as 0 instead of the small dot, so "KES 0" and "0 payments" no longer look like "KES ." Server addresses and version numbers also keep normal digits so they can be typed into another phone.
- **Appearance:** theme names and colour names are translated, the Deep black and High contrast switches match the other switches, and Deep black says so when it is not showing because the light theme is on.
- **Home:** the "update available" banner is easier to read in the light theme, and its text is shorter.
- Hours on charts read 12 ص / 6 م in Arabic.

## [1.32.6] - 2026-11-11

- **Lock screen.** In Arabic the PIN keypad now reads 1 2 3 from the top left like any phone keypad (it was mirrored). Tapping a key is lighter on slow phones, and the delete, sign-in and fingerprint buttons can now be read out by TalkBack.
- **Accountant export.** Reports now also sends a sales list (one row per sale with net, VAT, total and the M-Pesa code), next to the journal and the profit and loss.
- **Checkout.** If you tap Pay while a scanned item is still being added, the till now tells you to tap again, instead of doing nothing.
- **Printed receipts.** Arabic product and customer names now print in Latin letters instead of "????", and Arabic digits print as normal digits.

## [1.32.5] - 2026-11-10

- **More Arabic.** Payment methods, expense categories, order statuses (Draft, Ordered, Received…) and the activity log's event names now show in Arabic when the app is in Arabic. The receipt screen's status, buttons and refund/void dialogs too.

## [1.32.4] - 2026-11-10

- **Forms keep what you typed.** Tapping outside a form pop-up (add expense, supplier, customer, pay supplier, receive goods, add credit) no longer closes it and loses your typing. Use Cancel or Back to close it.
- **More Arabic.** Purchasing (orders and suppliers), Sales history, Reports buttons and the Checkout tiles now follow the app language.
- **Sales history** now uses the same tidy empty list as Expenses and Customers.

## [1.32.3] - 2026-11-10

- **Alert when two tills disagree.** If two tills refund or void the same sale in different ways, you now get a notification naming the sale and which one was kept (the first one, so money is never returned twice). Before, it was only in the activity log.
- **Tidier empty lists.** Empty Expenses and Customers lists now look the same, with clearer text and a bigger button.
- **Arabic.** The "No results for…" lines in Products and Inventory now follow the app language.

## [1.32.2] - 2026-11-10

- **A safety copy before every update.** Just before a new version opens your shop data for the first time, the app keeps a copy of it. You will find it in Settings > Data & Backup, named `pre-upgrade-...`, and the normal Restore button can bring it back. Each update replaces the previous copy, so it does not fill up your phone.
- **Search in Settings.** Type part of a section name at the top of Settings and only the matching sections show.
- **More Arabic.** The product form, Inventory, Expenses, the barcode scanner and the Home update banner now follow the app language.

## [1.32.1] - 2026-11-09

- **Arabic numbers.** In Arabic mode, amounts, counts, percentages and times now show in Arabic digits (١٢٣). Codes such as M-Pesa references, and anything you type, keep their normal digits so they still match and scan.
- **More Arabic words.** The customer display, the "set a new PIN" screen and the M-Pesa hints at checkout.
- **PDF receipts and business reports in Arabic.** The words are in Arabic; the layout and digits in the PDF stay as before. Printed (thermal) receipts are still in English.

## [1.32.0] - 2026-11-09

- **Old M-Pesa payment warning.** If a payment arrived hours before the sale it is being used for, the till now asks you to check it belongs to this customer, not an earlier one. It is a warning only, so a customer who paid ahead can still be served.
- **Cash-up.** The next shift now starts with the float the last shift counted (you can change it), and sales, cash paid out and refunds are all cut off at the same moment when a shift closes.
- **Exact totals.** The Z-report, the credit overview and supplier part-payments now add money to the cent, so no stray fractions of a cent.
- **Preparing exact-cent storage.** Sales now also keep their total in whole cents. Nothing looks different; this is the first step of a safer way of storing money. Your data is upgraded automatically the first time you open the new version.
- **Arabic.** Fixed the wording of "ago" for two minutes, hours and days.

## [1.31.2] - 2026-11-09

- **More of the app in Arabic.** The rest of Settings, Analytics, Stock insights and M-Pesa now show in Arabic, along with many sentences that include amounts and names.
- **Messages the app writes itself are in Arabic too.** Error advice, the health check, backup "how long ago" and password strength, the daily report you send to the owner, and the low-stock and expiry notifications.
- Receipts and printed reports are still in English. They need a printer test first.

## [1.31.1] - 2026-11-08

- **Safer PIN lockout.** After too many wrong PINs the till stays locked for the full time, even if someone changes the phone's date or time.
- **Safer web dashboard.** The dashboard page now carries its own copy of the code it needs instead of loading it from a public website, and blocks any other code from running. Upload the whole `cloud/dashboard` folder again to get this.
- **A note on M-Pesa text messages.** A determined person can fake the sender name on a text message, so for payments you cannot afford to be wrong about, use instant M-Pesa (webhook) or STK push.

## [1.31.0] - 2026-11-08

- **Share your product list between shops.** In Settings, Cloud dashboard, choose "Shared product list". One shop is the main shop and sends its names, prices, tax and supplier details; your other shops follow it. Stock, sales and customers are never shared, and a following shop's own price edits are replaced by the main shop's list. You need to run the new `cloud/catalog_setup.sql` once in your online database first.
- **Better web dashboard.** Choose 7, 14 or 30 days, compare your shops, see top products, export the daily figures as a CSV file, switch to Arabic, and install the page on a phone's home screen. Shops send their top products from this version on.
- **More of the app in Arabic.** The sale screen, receipt screen, setup wizard, and many labels with amounts and names are translated.
- **The first-run setup now looks like Settings**, with a purple header, cards, and buttons that stay at the bottom.

## [1.30.1] - 2026-11-08

- **Vibration on touch and scan.** Every tap now gives a light tick, a scan that is accepted gives one short buzz with the beep, and one that is not found or is out of stock gives two buzzes. Turn it off in Settings, Bluetooth Barcode Scanner, \"Vibrate on touch and scan\".
- **More of the app in Arabic.** Purchasing, Labels and Analytics are now translated. The setup wizard, Customer display, longer help sentences, receipts and printed reports are still English.

## [1.30.0] - 2026-11-07

- **The app can now be used in Arabic.** Open Settings, then Language, and choose العربية. The whole screen turns right to left. Login, Home, the bottom bar, Checkout, Inventory, Products, Customers, Reports, Expenses, Shifts, Sales history, Credit ledger, Users and most of Settings are translated. Some screens (Purchasing, Labels, Analytics, the setup wizard), longer help sentences, receipts and printed reports are still in English for now. Choose "Same as the phone" to go back to the phone's language.

## [1.29.0] - 2026-11-07

- **Update the app from inside the app.** Open Settings, then About & support, and tap "Check for updates". If a new version is out you see what changed and can tap "Download and install"; Android then asks you to confirm. The first time, Android asks you to allow MiniMart to install updates. Home shows a card when an update is waiting, and the app checks once a day by itself (you can turn that off). Your sales and settings are kept. Use Wi-Fi if you can, because the download uses data.

## [1.28.0] - 2026-11-07

- **Cash count matches the drawer.** On the Add expense form, switch on "Paid in cash from the till" when you take money from the drawer. The end-of-shift cash count then expects that much less, and cash refunds of earlier shifts' sales are subtracted too. The Z-report shows both. If you forget to switch it on, the count will still show a shortage. Refunds made before this update are not counted.
- **Harder-to-guess PINs.** New PINs can no longer be repeated digits (1111), digits in a row (1234, 9876), a repeated pair (1212) or a very common PIN. Existing PINs keep working until they are changed.
- **Accounts for your accountant.** In Reports, the new table button sends two spreadsheet files for the period: a journal and a profit and loss, with VAT shown separately. If item costs were not recorded, the profit and loss warns that profit is overstated.
- **How reliable a forecast is.** Inventory Insights now shows how close the forecast was for each product over the last week.
- **Out-of-date warning on till phones.** A till phone that has not synced for 12 hours shows a note on the health screen. After 2 days, or if it never synced, Home shows a "tap to sync" banner. The health screen also counts changes made on two phones at once.
- **Printer problems are easier to spot.** The app remembers recent print failures, tries one reconnect before giving up, and the hardware screen says when the printer last worked and suggests sharing a PDF or WhatsApp receipt.
- **More stable start-up.** If the app keeps closing right after opening, background work is held back for two minutes so you can still sell.
- **Update note.** This update changes the database. Back up first, as usual.

## [1.27.1] - 2026-11-06

- **Sign-in screen follows the light theme.** In Light (or Phone setting while the phone is light), the PIN screen now uses the same pale colours as the rest of the app instead of staying dark.
- **No dark flash when opening.** The app remembers your theme, so Light no longer starts with a dark screen.
- **Easier to read in Light.** WhatsApp buttons on Receipt and Low Stock, the Confirm button for voids and refunds, and Home tiles and alerts were adjusted so text is clear on pale backgrounds.
- **Matching card sizes.** The paired cards on Analytics (Revenue and Sales, Gross and Net profit), Reports (Avg Basket and Active Days) and Credit Ledger (Owed to Shop and All Customers) are now the same height.

## [1.27.0] - 2026-11-06

- **Light theme.** Settings > Appearance now has Dark, Light and Phone setting. Dark is still the default and looks exactly as before. Light uses pale backgrounds with dark text, and every accent colour has a light version that stays easy to read.
- **Hidden tiles are restored from Edit.** The "Restore hidden tiles" button no longer sits on Home. Tap Edit and it appears there, below the tiles-per-row choice.
- **Private customer details on M-Pesa payments.** Sender names, sender phone numbers and the original message are now locked with a key that stays on this phone, on top of the database lock. After restoring a manual backup on a different phone those three details will be blank; the code and amount are kept.
- **Same colour for each way of paying.** Cash, M-Pesa, card, mixed and credit now have one colour on Reports, Sales History and Analytics, readable on every theme.
- **Tests.** More automatic checks for the barcode scanner, the printer and cash drawer commands, and the tiles-per-row picker.

## [1.26.0] - 2026-11-05

- **What goes in a basket.** Analytics now shows how many items a typical sale has, how many sales had two or more different products, and which products are often bought together, so you can put them side by side.
- **Staff and reversals.** See each cashier's sales and how often their sales were refunded or voided. A high rate is shown in amber as something to talk about, not as proof of anything.
- **Where money leaks.** The discounts you gave and the sales that were taken back, with their value, for the period you choose.

## [1.25.0] - 2026-11-05

- **Beep when you scan.** A short beep when an item is added, and a lower double beep when a scan is not found or the item is out of stock. Turn it off in Settings, under Bluetooth Barcode Scanner.
- **"Leave without saving?"** on the product form, so a stray tap does not throw away what you typed.
- **Store health shows more.** It now lists problems the app handled (kept only on the phone, with no sales or customer details) and has a Share button to send the list to whoever is helping you. It also warns if the shop database is not locked.
- **Clearer messages** when the printer or cash drawer does not answer, or sync and the cloud upload fail.
- **Safer sync.** A till no longer receives technical error text from the main phone.
- **Totals add up to the exact cent** in Reports, Expenses, Analytics, purchase orders and the cloud summary.

## [1.24.1] - 2026-11-04

- **An M-Pesa code can no longer be used twice after a refund.** If you refunded a sale, its M-Pesa code stays used, so the same payment cannot buy goods again. A sale you cancelled (voided) by mistake still lets you ring it up again with the same code.
- **Today's cash on the Shifts page.** Owners and managers see one total for the day: what should be in the drawers, what was counted, and whether you are over or short. Shifts still open are listed as not counted yet.
- **Cash totals are exact to the cent**, and a difference under KSh 1 counts as balanced.
- **Safer restore.** Restoring a backup now checks the copy first and only then replaces your data, so a damaged or incomplete backup can no longer leave your shop data half-overwritten.
- **Stronger data check.** The check now also warns about an M-Pesa sale bigger than its payment, and about stock records that do not match the sales that used them.

## [1.24.0] - 2026-11-03

- **Your shop data is now locked on the phone.** The sales database is encrypted with a key kept safe by Android, so someone who copies the files off a lost or stolen phone cannot read them. Your existing data is converted automatically the first time you open this version. If anything goes wrong during that, the app keeps your old data and tells you.
- **Backups still work.** Backups you make yourself can be restored on another phone as before. The database is no longer copied to Google's automatic backup, because it could not be read there.
- **Daily summary for the owner.** Get a notification each evening with the day's takings, and optionally send it to a phone number (Settings).
- **Choose how many Home tiles per row.** Tap Edit on Home and pick 4, 3, 2 or 1. With 1 or 2 per row the tiles are bigger and easier to read.
- **Steadier screens.** Several screens now handle errors and slow loading without closing or freezing.
- **One-time step for some phones:** if your phone has an earlier test build installed (signed with a different key), uninstall it once before installing this version. Your data is not affected if you made a backup first.

## [1.23.0] - 2026-10-31

- **No sale is lost when the phone is offline or crashes.** A sale, refund or void is now saved together with its entry in the sync queue, so the main device always hears about it.
- **Amounts are exact to the cent.** The first time you open this version, every stored amount is tidied to the exact cent. Totals for sales, profit, expenses, M-Pesa and credit are added up exactly, and credit balances no longer drift by tiny fractions.
- Your data is kept. This update changes the database (version 17), so make a backup first if you want to be extra safe.

## [1.22.0] - 2026-10-30

- **Stock forecast:** Stock Insights now shows when each product is likely to run out ("runs out around Tue 3 Nov"), using the last four weeks of sales and busy days such as Saturdays. Suggested order amounts use it once a product has about two weeks of sales.
- **Kiosk mode:** Settings > Privacy has a new switch that locks the device to MiniMart and keeps the screen on, for a till staff share. Android asks you to confirm once.
- **Easier to read:** the empty Inventory screen now explains what to do and has an "Add your first product" button, and switches that are off now show clearly.
- **New Sale screen:** the infinity button is now a labelled Multi-scan switch.

## [1.21.2] - 2026-10-29

- **New Sale screen:** the infinity button is now a labelled **Multi-scan** switch (keeps the camera open after each item), and the hints on an empty cart are easier to read.

## [1.21.1] - 2026-10-29

- **Safer screens:** on Android 12 and newer, other apps' floating windows are hidden while MiniMart is open, so nothing can sit over the PIN pad or payment buttons. Cloud backup is now skipped on phones without a screen lock, because it could not be encrypted.
- **Better keyboards:** passwords, keys and codes use the private keyboard (no suggestions), M-Pesa references and currency use capital letters, names start each word with a capital, and search fields have a Search key.

## [1.21.0] - 2026-10-29

- **Optional crash and speed reports:** Settings > Privacy has a new switch, "Send crash and speed reports". It is off by default and nothing is sent unless you turn it on. (It only works in builds made with the developer's Firebase file.)
- **Till sync conflicts:** if two tills refund and void the same sale in different ways, the first one stands, money is not returned twice, and the Activity Log now says what happened.
- **Behind the scenes:** more automatic tests for payments, checkout and the database upgrade path.

## [1.20.0] - 2026-10-28

- **Safer M-Pesa payments:** a payment that arrives twice (by SMS and by the till link, or because the phone retried) is now recorded once, even if the code is typed in capitals or with spaces.
- **Steadier background work:** syncing, backups and the cloud upload now retry the same way when the network drops, and give up quietly until the next round instead of losing data.
- **Checkout:** the rule for when "Complete sale" turns on (cash covers the total, M-Pesa checked, customer chosen for credit) is now tested, so it cannot drift.
- **Behind the scenes:** the app is rebuilt on current Android tools and libraries (newer Android, Kotlin and Compose). Nothing should look different; please report anything that does.

## [1.19.2] - 2026-10-26

- **Rearranging Home tiles, two ways:** in Edit mode you can now drag a tile straight away (no waiting for a long press), or tap one tile to pick it up and tap the place it should go. The picked tile is highlighted and the phone gives a small tap. A long press on any tile opens Edit, so you do not have to hunt for the button.
- **Clearer:** the hint explains both ways, "Restore hidden tiles" now says how many are hidden, and screen readers still announce each tile's description even though it is no longer shown on screen.

## [1.19.1] - 2026-10-25

- **Home tiles fixed:** the Quick Actions tiles now really fit four across (they were wrapping to three), so all of them fit in four rows. Dragging a tile to rearrange them now works: in Edit mode, hold a tile for a moment, then drag it over another one.

## [1.19.0] - 2026-10-25

- **Smaller Home tiles you can rearrange:** the Quick Actions tiles are now compact, four across on a phone (six on a tablet), so more fit on the screen without scrolling. Tap **Edit**, then hold a tile and drag it where you want it; the other tiles move out of the way and your order is remembered. Tap the red × to hide a tile, and "Restore hidden cards" brings them back. While editing, tapping a tile no longer opens it by accident. The one-line descriptions under each name were dropped to make room.

## [1.18.9] - 2026-10-24

- **Easier for everyone to use:** eleven buttons that only showed a picture (clear search, call or WhatsApp a supplier, show or hide a key, edit a customer, change or remove a user, search) now have a spoken name for screen readers such as TalkBack; about 40 tappable rows and chips are now announced as buttons; and the small delete and call buttons are now a full finger-sized 48 dp, so they are harder to miss or hit by accident.

## [1.18.8] - 2026-10-24

- **Clearer error messages:** when something fails (opening a receipt, saving a product or supplier, the data check, hardware tests, label printing, sending to the printer, or an M-Pesa request) the app now says what went wrong and what to do about it, in plain words, instead of showing a technical message such as "failed to connect to /192.168.1.5".

## [1.18.7] - 2026-10-24

- **Stronger storage for secrets:** M-Pesa keys, pairing codes and cloud settings are now encrypted with a key kept inside the phone's secure chip/Keystore, using our own small, tested code instead of an alpha-stage library. Your existing saved values are carried over automatically the first time you open the app, so nothing needs to be re-entered. If a phone's Keystore cannot be used, secrets are held in memory only (as before) and Store health says so.

## [1.18.6] - 2026-10-24

- **Pairing is stricter:** the main device only ever uses a full 16-digit pairing code that is not a simple pattern. If an older, shorter code is found it is replaced with a new one, and tills must enter the new code once (Settings shows it on the main device). Tills now ask for all 16 digits before trying to sync, and spaces or dashes you type are ignored.
- **Safer after a restart:** the main device now remembers the sync requests it has already accepted, even if the app is closed or the phone restarts, so a recorded request cannot be replayed afterwards.
- **Activity log can no longer be quietly trimmed:** the newest entry is also remembered in protected storage and in the cloud summary, so deleting the latest lines now shows a warning on the Activity log screen.
- **Under the hood:** the build tool (Gradle wrapper) is now stored with the code and checked on every build; more automatic tests for sync signing, payment checks and stock costing.

## [1.18.5] - 2026-10-23

- **Performance and security tidy-up:** release builds no longer write debug or info messages to the phone's system log (a little faster, and nothing chatty can leak); two unused queries that would have loaded every sale into memory at once were removed so they cannot be used by mistake. Checked and left as they were: notification links already can only open the inventory screen after sign-in, and database indexes already cover the busy queries.

## [1.18.4] - 2026-10-23

- **Easier-to-read amounts:** big amounts now show thousands separators everywhere on screen and on PDF receipts (KES 1,250.00 instead of KES 1250.00): checkout, cart, sales history, shifts, customers and credit. Thermal printer receipts are unchanged so columns still line up.
- **Under the hood:** the cash drawer now uses Android's current Bluetooth API instead of a deprecated one.

## [1.18.3] - 2026-10-23

- **Smoother typing and selling:** Settings fields now move to the next field with the keyboard's Next key; the store name and receipt footer capitalise words and spell-check, while keys and codes are never auto-corrected; and adding a product by tapping it in the search list (or pressing Enter) now gives the same short vibration as a scan.

## [1.18.2] - 2026-10-22

- **Security hardening:** archive files are now kept in the app's private storage instead of the shared app folder, so other apps cannot read them on older Android versions; the instant M-Pesa address is hidden from Android's clipboard preview when copied; the M-Pesa cloud function refuses oversized requests; and a new **Block screenshots** switch (Settings → Privacy, owner only, off by default) stops screenshots and screen recording and hides the app in the recent-apps list. PIN lockout already grows with repeated mistakes.

## [1.18.1] - 2026-10-22

- **About & support:** a new section in Settings shows the version and licence, links to the project page, and has a **Copy support info** button that copies the app version, phone model and a few health facts (never sales, customers or keys) so problems are easy to report.
- **Project polish:** issue and pull-request templates, and monthly dependency update checks, for people helping with the code.

## [1.18.0] - 2026-10-22

- **Hardware check:** a new screen (open it from Store health) tests the receipt printer and cash drawer with one tap and checks Bluetooth, camera, M-Pesa SMS permission, alerts, network, storage and battery, saying in plain words what to fix.
- **Archive old sales:** in Settings (owner only) you can move sales older than 1, 2 or 3 years into a compressed file you keep. The file is written and checked first; only then are the sales removed from the phone. Weekly housekeeping also clears old, already-delivered sync records.
- **Instant M-Pesa (webhook):** payments can now reach the phone through your own Supabase project as soon as Safaricom reports them, even if the SMS never arrives. Needs a one-time setup (see docs/MPESA_WEBHOOK.md) and has not been tried against live Safaricom yet; SMS tracking keeps working alongside it.
- **Accessibility:** larger touch targets, screen-reader roles for buttons, tabs, switches and colour choices, headings, expandable sections that say whether they are open, and error or success messages that are read out when they appear.
- **More tests and documentation:** tests for credit balances, M-Pesa payments, archiving, the hardware check and the webhook; new architecture notes and assistant guidance for people working on the code.

## [1.17.1] - 2026-10-22

- **Theme improvements:** two new accent colours (Green and Graphite); a **Deep black** option for near-black backgrounds that saves battery on OLED screens; a **High contrast** option with whiter text and stronger borders; the Teal, Blue and Orange accents are slightly deeper so white button text is easy to read; unselected bottom-bar icons are brighter; and a test now checks text, button and icon contrast for every colour and option combination. The app is still dark only.

## [1.17.0] - 2026-10-21

- **Store health, Today, setup and end of day:** a **Store health** screen checks your records, backups, sync, storage, security and stock and says what to fix; an owner **Today** screen shows takings, profit, how customers paid, the busiest hour and how it compares with the same time last week; **M-Pesa reconciliation** lists payments with no sale, sales with no payment message, wrong amounts and reused codes; a first-launch **setup wizard** helps a new shop name itself and pick its printer; an **End of day checklist** (in Shifts) blocks closing while a shift is open and walks you through held sales, M-Pesa, sync, expenses, restock and a backup; failures now say what happened and what to do; and a whole-shop test suite checks stock, cost, credit, voids and M-Pesa together.

## [1.16.37] - 2026-10-20

- **Tablets, more phones, stricter networking:** tablets and other large screens can now rotate (phones stay upright) and the app sits in a centred column instead of stretching; the release now includes `app-universal-release.apk` plus per-chip APKs, so older 32-bit (armeabi-v7a) phones can install it (before, only 64-bit ARM could); plain-text HTTP is now blocked across the whole app (till-to-till sync uses its own encrypted socket client), and only the system certificate authorities are trusted; Compose UI tests are added (built on every push, run on an emulator from the "UI tests (emulator)" workflow).

## [1.16.36] - 2026-10-20

- **Bug fix:** the expense form no longer accepts an invalid or absurdly large amount.

## [1.16.35] - 2026-10-20

- **Security and speed:** file sharing is limited to receipts and backup folders (it used to expose the whole cache and Downloads folder to the app receiving a share), and a customer's ledger loads their latest 200 entries so customers with long histories open instantly.

## [1.16.34] - 2026-10-20

- **Bug fixes:** purchase deliveries now ignore invalid quantities and record whole units for counted products (so the order tally always matches the stock added); purchase order lines with zero, negative or invalid quantity or cost are dropped on save; store credit use rejects zero or invalid amounts.

## [1.16.33] - 2026-10-18

- **Stability and small UX fixes:** if the app ever closes unexpectedly it now keeps a local crash note and, next time you open it, offers to share the technical details (nothing is sent automatically). In the customer picker, searching for someone who is not there now offers "Add this as a new customer" with the name or phone already filled in. The PDF receipt date format is created per receipt instead of being shared.

## [1.16.32] - 2026-10-18

- **Backup screen and M-Pesa keys:** the backup card now says in plain words how old your last backup is (green, amber or red), offers "Save a copy off this phone" the moment a backup finishes, rates passphrase strength and warns that a forgotten passphrase cannot be recovered, and shows each backup's age, size and whether it is encrypted. M-Pesa keys are no longer written to a plain file if the phone's secure storage fails (they are refused instead), saved secrets are never shown again on screen, and the Settings box tells you when keys could not be saved.

## [1.16.31] - 2026-10-18

- **Paper width:** Settings → Thermal Printer now has a 58 mm / 80 mm choice, so receipts and labels use the whole width of 80 mm paper (48 columns instead of 32).

## [1.16.30] - 2026-10-17

- **Business insights and charts:** Analytics now shows which few products make most of your sales (A/B/C), which products sell below cost or on a thin margin, your biggest profit earners, and your typical week. Charts are now one shared component you can tap or drag across to read exact figures; the unused MPAndroidChart library and the JitPack repository were removed.

## [1.16.29] - 2026-10-17

- **Encrypted sync, pure-JVM core, more tests:** sync between phones is now encrypted end to end with the pairing code, so someone on the same Wi-Fi can no longer read sales or customers (both phones must be updated; old 6-digit codes can be upgraded in Settings). `:core` is now a pure Kotlin/JVM module, and sync parsing, remote-sale validation and duplicate-change handling have automated tests.

## [1.16.28] - 2026-10-17

- **CI hardening:** the data layer (Room queries, unique barcodes, stock updates, void/refund rules) now has automated tests that run on every push, and lint errors now fail the build instead of being ignored.

## [1.16.27] - 2026-10-16

- **Add Credit fix:** the 100 / 200 / 500 / 1000 quick-amount buttons in the Add Credit dialog no longer wrap their digits onto separate lines.

## [1.16.26] - 2026-10-16

- **Security:** new sync pairing codes are now 16 random digits made with a secure generator (the old 6-digit code could be worked out from one captured sync request on shared Wi-Fi, and was not made with a secure generator). Tills already paired keep working with their current code.

## [1.16.25] - 2026-10-16

- **Faster start:** background jobs (low-stock and expiry alerts, backups, sync) are now set up off the launch path, so the app reaches the login screen sooner on slower phones.

## [1.16.24] - 2026-10-16

- **Product form check:** the product form now rejects absurd or non-numeric prices and stock (typing "Infinity" or "NaN" used to be accepted and could break totals and reports).

## [1.16.23] - 2026-10-16

- **Receipt printing:** receipts now print accented names, "×", bullets and curly quotes correctly on thermal printers (they used to come out as garbage characters), and stray control characters in a product or customer name can no longer be read by the printer as commands.

## [1.16.22] - 2026-10-16

- **Whole-app check:** receipt numbers and backup file names now always use plain digits (on phones set to an Arabic or Indian-digit language they could contain other numerals, which breaks matching between tills). The build now runs on a pinned Ubuntu 24.04 image, because GitHub switches its default image on 19 October and an unplanned change could have broken releases.

## [1.16.21] - 2026-10-14

- **Scan button and New Sale layout:** the raised scan button now follows the accent colour (its dark ring was a fixed teal tint) and the cart-count badge is no longer cut off at the edge. On New Sale, Hold and Held (n) moved into the Cart header row next to the item count, freeing a full row for the cart.

## [1.16.20] - 2026-10-14

- **Hold a sale:** on the New Sale screen, "Hold sale" sets the current cart aside so you can serve the next customer, and "Held (n)" lists parked sales to resume or discard. Parked sales are saved, so they survive the app closing; up to 10 are kept. Stock is not reserved, and prices and stock are re-checked when you resume (items no longer available are left out and you are told).

## [1.16.19] - 2026-10-14

- **Undo for removed cart items:** removing an item from the sale (trash button or reducing it to zero) now shows "Removed … UNDO" for five seconds, and Undo puts it back in the same place. An accidental tap no longer means re-scanning the item.

## [1.16.18] - 2026-10-14

- **Owner-only system settings and lighter background work:** payment (Daraja) credentials, device sync, the cloud link, and backup/restore are now owner-only — checked again at the moment of the action, not just hidden — because a manager could otherwise point STK payments at their own till or restore an old backup to erase sales. A backup is fully integrity-checked before it replaces the live data. Screens stop listening to the database while the app is in the background.

## [1.16.17] - 2026-10-14

- **No more blank screen from Recents:** if Android had ended the app and you reopened it from Recents, it restored the last screen (for example Settings) while nobody was signed in, which showed an empty page. It now goes to the PIN screen, and a screen you are not allowed to see sends you Home instead of staying blank.

## [1.16.16] - 2026-10-14

- **STK Push completes without typing a code:** Safaricom confirms an STK payment but does not send the receipt number, so the till now records a short generated reference and the sale can finish straight away (type the SMS code over it to link the payment record). Daraja timestamps use Nairobi time, and unused M-Pesa number fields were removed from Settings.

## [1.16.15] - 2026-10-14

- **Settings code split:** no visible change; the Settings screen file was 1,080 lines and is now three smaller ones plus shared form pieces, which makes later changes safer.

## [1.16.14] - 2026-10-13

- **Better barcode scanning:** the camera analyses at 1280x720 instead of 640x480, zooms in by itself on small barcodes, focuses where you tap, and picks the barcode nearest the middle when several are in view. Scanned text is checked in one place (no blanks, control characters or over-long codes), the keyboard/Bluetooth scanner buffer is capped, and the scan overlay follows the accent colour.

## [1.16.13] - 2026-10-13

- **Payment tiles and Daraja:** the Paybill/Till/Agent number box is removed from Settings and the Daraja (M-Pesa STK Push) box is back, and the Checkout payment tiles, heading icon and credit card follow the accent colour.

## [1.16.12] - 2026-10-13

- **Settings clean-up:** the Daraja (STK Push) credentials box is gone from Settings, and the Owner badge and M-Pesa save button follow the accent colour instead of a fixed green.

## [1.16.11] - 2026-10-13

- **Start-up and first-open speed:** the release build ships a baseline profile, so the app's own code is compiled ahead of time instead of being interpreted and compiled while you use it. The gain shows after the first launch once Android has processed the profile (a few minutes, or after the phone is idle and charging).

## [1.16.8] - 2026-10-12

- **Theming:** Settings → Appearance now has an accent colour (Teal, Blue, Purple, Orange, Rose) that recolours headers, buttons, the navigation bar, Home and every dark surface at once. Teal is the original look, unchanged. The old Dark Mode switch is gone because it never changed anything (every screen is dark). Menus, dialogs and pickers now use the same dark palette instead of following the phone's light/dark setting and wallpaper colours.

## [1.16.7] - 2026-10-12

- **Faster Products and Inventory:** the catalogue is loaded into memory when the app starts and kept up to date, so both lists show their rows on the first frame instead of opening empty. The 150 ms wait that every open of these screens used to include is gone (it now applies only while typing in the search box).

## [1.16.6] - 2026-10-12

- **Main device stays on:** the phone set as the main device now remembers it, and starts the sync server again by itself after the app or phone restarts (and every 15 minutes if Android stopped it). Before, a restart left the tills unable to connect until someone reopened Settings. The server also refuses a connection that sends an endless header.

## [1.16.5] - 2026-10-12

- **Security:** the main device now signs its sync replies, and a till refuses an unsigned or altered reply, so a device that only answers on the main phone's address cannot feed a till fake prices, stock or customer balances (update both phones together). Sync only connects to private Wi-Fi addresses. Automatic backups are kept in the app's private storage instead of a folder other apps can read on older Android versions (old copies there are deleted after the next good backup). The activity log no longer copies its details into the system log. Known limit: M-Pesa text-message sender names can be faked by a determined fraudster; STK push is the authenticated route.

## Earlier releases

Versions before 1.16.5, and 1.16.9 and 1.16.10, were not written up here. Their downloads are on the
[Releases page](https://github.com/Baker0o7/MiniMart-Pos/releases).

[1.17.0]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.17.0
[1.16.37]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.37
[1.16.36]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.36
[1.16.35]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.35
[1.16.34]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.34
[1.16.33]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.33
[1.16.32]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.32
[1.16.31]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.31
[1.16.30]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.30
[1.16.29]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.29
[1.16.28]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.28
[1.16.27]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.27
[1.16.26]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.26
[1.16.25]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.25
[1.16.24]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.24
[1.16.23]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.23
[1.16.22]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.22
[1.16.21]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.21
[1.16.20]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.20
[1.16.19]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.19
[1.16.18]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.18
[1.16.17]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.17
[1.16.16]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.16
[1.16.15]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.15
[1.16.14]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.14
[1.16.13]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.13
[1.16.12]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.12
[1.16.11]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.11
[1.16.8]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.8
[1.16.7]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.7
[1.16.6]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.6
[1.16.5]: https://github.com/Baker0o7/MiniMart-Pos/releases/tag/v1.16.5
