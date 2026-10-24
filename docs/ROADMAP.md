# Roadmap and good first contributions

What is planned or wanted, roughly in order. Nothing here is a promise: open an issue before starting anything big so we can agree on the approach.

## Good first contributions

Small, self-contained, and each has a clear "done":

* **Swahili strings.** Translate the plain-language messages in `ErrorAdvice`, the Quick Actions names and the receipt footer, behind a language setting. Start with one screen.
* **More M-Pesa message samples.** Add real (anonymised) payment SMS texts to the `MpesaSmsParser` tests, especially ones that fail to parse, and fix the parser for them.
* **Receipt templates.** Optional layouts for 58 mm and 80 mm printers (a shop name block, KRA PIN line, change in words).
* **Accessibility checks.** Walk a screen with TalkBack on, and fix labels, focus order or touch targets. One screen per pull request.
* **Tests.** Anything in `:core` with thin coverage; every new test is welcome.
* **Docs.** Screenshots (with customer details blurred), a "first day in your shop" guide, a troubleshooting page.

## Next

* Light theme (the app is dark-only today by design; this is a large, careful change).
* Keeping `security-crypto` out entirely once the secrets migration has run on most phones (see [DEPENDENCIES.md](DEPENDENCIES.md)).
* A bigger archive and restore flow for very large shops.
* Barcode label sheets in more sizes.

## Later ideas

* Multi-shop dashboard in the cloud summary.
* Supplier price history and reorder suggestions.
* Optional receipt by SMS or WhatsApp link.

Have an idea that is not here? [Open an issue](https://github.com/Baker0o7/MiniMart-Pos/issues/new/choose) and tell us the shop problem it would solve.
