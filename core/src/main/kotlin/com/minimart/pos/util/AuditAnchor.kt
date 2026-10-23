package com.minimart.pos.util

/**
 * The newest audit-log line's hash and the line count, stored outside the log file. The hash chain
 * proves lines were not edited or removed from the middle; the anchor also exposes deletion of the
 * newest lines, which a chain alone cannot see.
 */
data class AuditAnchor(val lines: Int, val hash: String) {
    fun encode(): String = "$lines:$hash"

    companion object {
        fun decode(s: String?): AuditAnchor? {
            val p = s?.split(':') ?: return null
            val n = p.getOrNull(0)?.toIntOrNull() ?: return null
            val h = p.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
            return if (p.size == 2 && n >= 0) AuditAnchor(n, h) else null
        }

        /** Null when the log still reaches the anchor; otherwise a plain-language problem. */
        fun check(anchor: AuditAnchor?, currentLines: Int, hashAtAnchorLine: String?): String? {
            if (anchor == null) return null
            if (currentLines < anchor.lines) return "The activity log has ${anchor.lines - currentLines} fewer entries than it should."
            if (hashAtAnchorLine != anchor.hash) return "The activity log was changed after it was written."
            return null
        }
    }
}
