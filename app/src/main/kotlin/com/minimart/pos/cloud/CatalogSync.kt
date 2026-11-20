package com.minimart.pos.cloud

import com.minimart.pos.data.repository.CatalogApplier
import com.minimart.pos.util.CatalogRules
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

sealed class CatalogOutcome {
    /** [detail] is a plain sentence for the owner. */
    data class Done(val detail: String) : CatalogOutcome()
    data class Failed(val message: String, val anotherPublisher: Boolean = false) : CatalogOutcome()
}

/**
 * Shares the product list between an owner's shops over the internet. One shop (the main shop) publishes; the
 * others follow. Only names, prices, tax and supplier details travel: never stock, sales or customers, so a
 * follower's till and shelf counts are never touched. Everything received is checked by `CatalogRules` first.
 */
@Singleton
class CatalogSync @Inject constructor(private val config: CloudConfig, private val auth: OwnerAuth, private val applier: CatalogApplier) {
    private val lock = Mutex()

    private fun doc(uid: String) = FirebaseFirestore.getInstance().collection("owners").document(uid).collection("catalog").document("main")

    /** Sends this shop's list when it changed (or when [force]). A different shop already publishing needs [takeOver]. */
    suspend fun publish(takeOver: Boolean = false, force: Boolean = false): CatalogOutcome = lock.withLock {
        val uid = auth.uid ?: return CatalogOutcome.Failed("Sign in with Google first.")
        try {
            val items = CatalogRules.sanitize(applier.export()).items
            val fp = CatalogRules.fingerprint(items)
            if (!force && !takeOver && fp == config.catalogFingerprint) return CatalogOutcome.Done("The shared product list is already up to date.")
            val json = CatalogJson.toJson(items).toString()
            if (json.length > MAX_LIST) return CatalogOutcome.Failed("The product list is too large to share (limit about 3,000 products).")
            val ref = doc(uid)
            val me = config.storeId
            // One transaction, so two shops cannot both become the main shop at the same moment.
            val version = FirebaseFirestore.getInstance().runTransaction { tx ->
                val snap = tx.get(ref)
                val publisher = snap.getString("publisherId")
                if (publisher != null && publisher != me && !takeOver) return@runTransaction -1L
                val v = (snap.getLong("version") ?: 0L) + 1
                tx.set(ref, mapOf("version" to v, "publisherId" to me, "itemCount" to items.size, "items" to json, "updatedAt" to System.currentTimeMillis()))
                v
            }.awaitResult()
            if (version < 0) return CatalogOutcome.Failed("Another shop already publishes the product list.", anotherPublisher = true)
            config.catalogVersion = version
            config.catalogFingerprint = fp; config.catalogAt = System.currentTimeMillis()
            CatalogOutcome.Done("Shared ${items.size} products with your other shops.")
        } catch (e: Exception) {
            CatalogOutcome.Failed(com.minimart.pos.util.ErrorAdvice.explain(e, "Sharing the product list"))
        }
    }

    /** Gets the main shop's list if there is a newer one and applies it to this shop's products. */
    suspend fun pull(): CatalogOutcome = lock.withLock {
        val uid = auth.uid ?: return CatalogOutcome.Failed("Sign in with Google first.")
        try {
            val snap = doc(uid).get().awaitResult()
            val version = snap.getLong("version")
            if (!snap.exists() || version == null || version <= config.catalogVersion) {
                config.catalogAt = System.currentTimeMillis(); return CatalogOutcome.Done("Already up to date.")
            }
            if (snap.getString("publisherId").equals(config.storeId, ignoreCase = true))
                return CatalogOutcome.Failed("This shop is the one that publishes the list, so it does not need to receive it.")
            val parsed = CatalogJson.parse(JSONArray(snap.getString("items") ?: "[]"))
            val clean = CatalogRules.sanitize(parsed.items)
            val declared = (snap.getLong("itemCount") ?: parsed.items.size.toLong()).toInt()
            val bad = parsed.skipped + clean.rejected
            // A list that is empty, or mostly unreadable, is treated as damaged: change nothing rather than half-apply it.
            if (declared > 0 && (clean.items.isEmpty() || bad * 5 > declared))
                return CatalogOutcome.Failed("The shared list looks damaged ($bad of $declared products unreadable), so nothing was changed.")
            val result = applier.apply(clean.items)
            config.catalogVersion = version; config.catalogAt = System.currentTimeMillis()
            CatalogOutcome.Done("Product list updated: ${result.added} new, ${result.updated} changed, ${result.deactivated} hidden." +
                if (bad > 0) " $bad unreadable products were skipped." else "")
        } catch (e: Exception) {
            CatalogOutcome.Failed(com.minimart.pos.util.ErrorAdvice.explain(e, "Getting the product list"))
        }
    }

    /** What the background upload should do for this shop's role, quietly. */
    suspend fun syncInBackground() {
        when (config.catalogRole) { 1 -> publish(); 2 -> pull() }
    }

    private companion object { const val MAX_LIST = 900_000 }   // a Firestore document holds about 1 MB
}
