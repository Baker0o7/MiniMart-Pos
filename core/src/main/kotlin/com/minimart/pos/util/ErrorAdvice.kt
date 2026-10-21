package com.minimart.pos.util

/**
 * Turns a failure into "what happened, and what to do now" in plain words. Matches on class names, so
 * it works for Android exceptions (SQLite, security) without :core depending on Android.
 * Messages the app wrote itself (IllegalStateException / IllegalArgumentException) are already
 * readable and are passed through.
 */
object ErrorAdvice {
    fun explain(error: Throwable, doing: String): String = "$doing didn't work. ${reason(error)}"

    fun reason(error: Throwable): String {
        val chain = generateSequence(error) { it.cause?.takeIf { c -> c !== it } }.take(5).toList()
        val names = chain.map { it::class.java.name }
        fun has(part: String) = names.any { it.contains(part, ignoreCase = true) }
        val msg = chain.firstNotNullOfOrNull { it.message?.takeIf { m -> m.isNotBlank() } }.orEmpty()
        val lower = msg.lowercase()

        return when {
            has("SQLiteFull") || "database or disk is full" in lower || "no space left" in lower ->
                "The phone's storage is full. Free up some space (delete old photos or videos), then try again."
            has("SQLiteDatabaseCorrupt") || "malformed" in lower ->
                "The shop database looks damaged. Don't keep selling: restore your latest backup from Settings."
            has("SQLiteDiskIO") ->
                "The phone could not write to its storage. Restart the phone and try again; if it keeps happening, make a backup."
            has("SQLiteConstraint") || "unique constraint" in lower ->
                "That already exists (for example a barcode or code that is already used). Check it and try again."
            has("SQLiteDatabaseLock") || "database is locked" in lower ->
                "The app was busy with something else. Wait a few seconds and try again."
            has("WrongPassphrase") ->
                "Wrong passphrase, or the file is damaged. Check the passphrase and try again."
            has("UnknownHost") || has("ConnectException") || has("NoRouteToHost") ->
                "Could not reach the other device or the internet. Check the Wi-Fi or mobile data and try again."
            has("SocketTimeout") || has("TimeoutException") ->
                "It took too long to answer. Check the connection and try again."
            has("javax.net.ssl") || has("CertificateException") ->
                "A secure connection could not be set up. Check the phone's date and time are correct, then try again."
            has("SecurityException") ->
                "The app doesn't have permission for this. Allow it in the phone's Settings > Apps > MiniMart POS > Permissions."
            has("FileNotFound") ->
                "A file the app needed was not found. It may have been moved or deleted."
            has("OutOfMemory") ->
                "The phone ran out of memory. Close other apps and try again."
            has("IOException") ->
                "Could not read or write the file. Check there is free space and try again."
            has("CancellationException") -> "It was cancelled."
            (error is IllegalStateException || error is IllegalArgumentException) && msg.isNotBlank() -> msg.trimEnd('.') + "."
            else -> "Something unexpected went wrong. Try again; if it repeats, restart the app and share the crash note when it offers."
        }
    }
}
