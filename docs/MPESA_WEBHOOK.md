# Instant M-Pesa payments (Daraja C2B webhook)

Without this, a payment is noticed when its SMS arrives on the till phone. With it, Safaricom also tells your own
cloud project about every payment to your till or paybill, and the phone picks it up within about 15 minutes (or at
once when the M-Pesa list is opened). It works if the phone was off, has no SIM, or the SMS was deleted.
SMS tracking and the webhook can run together; each M-Pesa code is stored once.

```
customer pays -> Safaricom -> Edge Function "till-payments" -> mpesa_inbox (your Supabase) -> app fetches -> mpesa_payments
```

## Set up (about 10 minutes, once)

1. **Cloud dashboard first.** In the app: Settings -> Cloud dashboard -> connect (see the README). The webhook reuses
   that connection.
2. **SQL.** In Supabase -> SQL editor, run `cloud/mpesa_webhook.sql` (after `cloud/supabase_setup.sql`).
3. **Function.** Deploy `cloud/functions/till-payments` with JWT checking off, because Safaricom cannot send a Supabase key:
   `supabase functions deploy till-payments --no-verify-jwt`
4. **Turn it on.** Settings -> Instant M-Pesa (webhook) -> Turn on. The app shows your private address,
   `https://<project>.supabase.co/functions/v1/till-payments?t=<token>`. Tap Copy.
5. **Register it with Safaricom.** On the Daraja portal, use *C2B Register URL* for your shortcode with that address as
   both Confirmation and Validation URL (response type `Completed`). Production shortcodes must be registered by
   Safaricom; the sandbox lets you try it with the simulate call.

## Good to know

* The address contains a secret token. Anyone who has it can add payments to your list. If it leaks, tap **New address**
  and register the new one; the old one stops working. Only a hash of the token is stored in the cloud.
* The function is named `till-payments` because Safaricom refuses addresses that contain words like "mpesa",
  "safaricom", "sql" or "exe".
* Safaricom now sends the payer's phone number hashed; the app then shows the name only.
* The app ignores anything that is not a believable payment (bad code, zero/negative/over 1,000,000 amount).
* A payment is confirmed to the cloud only after the phone stored it, so a failure part-way just means it is fetched again.
* Confirmed payments are removed from the cloud inbox after 30 days.
* This was written against Safaricom's documented C2B confirmation format and tested with unit tests and
  recorded-style payloads only; try it in the Daraja sandbox before relying on it in production.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| "The cloud project is missing the M-Pesa setup" | `cloud/mpesa_webhook.sql` has not been run |
| Turn on says connect the cloud dashboard | Settings -> Cloud dashboard is not connected |
| Nothing arrives | Address not registered at Daraja, function deployed without `--no-verify-jwt`, or phone offline. Check the function logs in Supabase |
| "N ignored as invalid" | The payload had a malformed code or amount; check the function logs |
