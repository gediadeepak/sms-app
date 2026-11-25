package com.gediadeepak.smsapp.core

object SendPolicy {
    const val UNDO_MILLIS = 10_000L
    const val MAX_LATE_MILLIS = 24 * 60 * 60 * 1000L
    fun due(now: Long, scheduled: Long?, undo: Boolean): Long {
        require(scheduled == null || scheduled > now) { "Choose a future time." }
        require(scheduled == null || scheduled - now <= 366L * 24 * 60 * 60 * 1000) { "Choose a time within the next year." }
        return scheduled ?: (now + if (undo) UNDO_MILLIS else 0)
    }
    fun expired(now: Long, due: Long) = now - due > MAX_LATE_MILLIS
}

object BusinessGroups {
    fun suggestion(sender: String): String? {
        val key = sender.uppercase().replace(Regex("^[A-Z0-9]{2}-"), "").replace(Regex("-[A-Z]$"), "")
        return when (key) {
            "AMAZON", "AMZN", "AMAZONIN" -> "Amazon"
            "FLIPKT", "FLIPKART" -> "Flipkart"
            "HDFCBK", "HDFCBN" -> "HDFC Bank"
            "ICICIB", "ICICIBANK" -> "ICICI Bank"
            else -> null
        }
    }
    fun label(custom: String, sender: String) = custom.takeIf { it.isNotBlank() } ?: suggestion(sender)
}
