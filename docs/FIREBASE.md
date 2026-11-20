# Crash and performance reports (Firebase), opt-in

MiniMart POS can send crash reports (Crashlytics) and speed timings (Performance Monitoring) to **your own** Firebase project.

* **Off by default, twice over.** The manifest disables collection, and the app only turns it on after the shop owner
  switches on *Settings > Privacy > Send crash and speed reports*. Analytics is disabled outright.
* **Not built in until you add your file.** Create a Firebase project, register the Android apps `com.minimart.pos` **and** `com.minimart.pos.debug` (debug builds use that name; the build fails with "No matching client found" otherwise),
  download `google-services.json` into `app/` and build. Without that file the Firebase plugins are not applied and
  the calls do nothing. (The file holds identifiers, not secrets, but keep it out of public forks if you prefer.)
* **What is sent when on:** the stack trace, phone model, Android version, app version, and named timings such as
  `complete_sale`. Never sales, customers, prices or PINs: do not add any to `Telemetry.breadcrumb`.
* **Release signing and mapping files:** Crashlytics uploads the R8 mapping on release builds when the plugin is applied.
* The local crash note (`CrashRecorder`) still works either way and is never uploaded by the app.

## Online account: Sign in with Google (optional)

The same `google-services.json` also switches on *Settings > Online account*.

1. In the Firebase console: **Authentication > Sign-in method > Google**: enable. Add your app's **SHA-1** (debug and release keys) under *Project settings > Your apps*, then download `google-services.json` again; it must now contain a web client (`default_web_client_id`). Without it the app shows "Google sign-in is not set up in this build".
2. **Firestore Database**: create it (production mode), then deploy `firebase/firestore.rules` (`firebase deploy --only firestore:rules`).
3. Consider turning on **App Check** and a **budget alert** in Google Cloud so a leaked key cannot run up costs.
4. Each owner's data lives under `owners/<their Google account id>/`: `stores/<shop>` (daily summary) and `catalog/main` (shared product list). The app's **Delete my account and cloud copy** button removes both and the sign-in itself.
5. The instant-M-Pesa inbox that used the old Supabase project was removed. Firestore cannot receive Safaricom's callback without server code, so payments arrive by SMS or the Daraja STK push.
