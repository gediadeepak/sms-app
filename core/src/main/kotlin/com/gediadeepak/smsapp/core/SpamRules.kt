package com.gediadeepak.smsapp.core

import java.util.Locale

/** Match formatting variants, never guess country codes or merge alphanumeric sender IDs by their digits. */
fun spamSenderKey(address: String): String {
    val trimmed = address.trim()
    return if (trimmed.matches(Regex("\\+?[0-9 ()-]+")) && trimmed.any(Char::isDigit)) "number:${normaliseNumber(trimmed)}"
    else "sender:${trimmed.uppercase(Locale.ROOT)}"
}
