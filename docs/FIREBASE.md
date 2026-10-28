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
