package com.minimart.pos.util

/**
 * How two devices' edits to the same record are combined, field by field, so neither shop loses work and both
 * devices end up with the same answer whichever one merges first.
 *
 *  * Plain fields (name, price, phone…): the **newest edit wins**; equal times are settled by device id so both
 *    sides pick the same winner. When the two sides disagreed, the loser is reported as a [Conflict] so the owner can see it.
 *  * Counts and balances (stock, credit): never overwritten, the **changes are added up** ([mergeCounter]).
 *  * A sale that was refunded on one till and voided on another: the first one applied stands and the other is
 *    flagged ([resolveReversal]); money is never reversed twice.
 */
object ConflictMerge {
    /** When and where an edit happened. Orders by time, then device id (an arbitrary but shared tie-break). */
    data class Stamp(val at: Long, val deviceId: String) : Comparable<Stamp> {
        override fun compareTo(other: Stamp): Int = compareValuesBy(this, other, { it.at }, { it.deviceId })
    }

    data class Field<T>(val value: T, val stamp: Stamp)

    /** A disagreement that was settled by keeping [kept]; [dropped] is what the other device had. */
    data class Conflict(val field: String, val kept: Field<String>, val dropped: Field<String>)

    data class Merged(val fields: Map<String, Field<String>>, val conflicts: List<Conflict>)

    fun <T> newest(a: Field<T>, b: Field<T>): Field<T> = if (a.stamp >= b.stamp) a else b

    /** Combines two versions of a record. Fields present on only one side are kept as they are. */
    fun merge(local: Map<String, Field<String>>, remote: Map<String, Field<String>>): Merged {
        val out = linkedMapOf<String, Field<String>>()
        val conflicts = mutableListOf<Conflict>()
        for (name in (local.keys + remote.keys).sorted()) {
            val l = local[name]; val r = remote[name]
            when {
                l == null -> out[name] = r!!
                r == null -> out[name] = l
                l.value == r.value -> out[name] = newest(l, r)
                else -> {
                    val winner = newest(l, r)
                    out[name] = winner
                    conflicts += Conflict(name, kept = winner, dropped = if (winner === l) r else l)
                }
            }
        }
        return Merged(out, conflicts)
    }

    /** Both devices started from [base] and moved to [localNow] / [remoteNow]: the result has both movements. */
    fun mergeCounter(base: Long, localNow: Long, remoteNow: Long): Long = base + (localNow - base) + (remoteNow - base)

    enum class ReversalOutcome { APPLY, ALREADY_DONE, CONFLICT }

    /** What to do when a till says a sale was refunded or voided. */
    fun resolveReversal(localStillCompleted: Boolean, localStatus: String, incomingStatus: String): ReversalOutcome = when {
        localStillCompleted -> ReversalOutcome.APPLY
        localStatus == incomingStatus -> ReversalOutcome.ALREADY_DONE
        else -> ReversalOutcome.CONFLICT
    }
}
