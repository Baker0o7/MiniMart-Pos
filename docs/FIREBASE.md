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

## Owner account: Sign in with Google (optional, no database)

The same `google-services.json` also switches on *Settings > Owner account*. Only Firebase **Authentication** is used: no Firestore, no database, no shop data is uploaded.

1. In the Firebase console: **Authentication > Sign-in method > Google**: enable. Add your app's **SHA-1** (debug and release keys) under *Project settings > Your apps*, then download `google-services.json` again; it must now contain a web client (`default_web_client_id`). Without it the app shows "Google sign-in is not set up in this build".
2. Consider turning on a **budget alert** in Google Cloud.
3. The app's **Delete my account** button removes the sign-in account.
