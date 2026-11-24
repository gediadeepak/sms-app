package com.gediadeepak.smsapp.core

import java.text.Normalizer
import java.util.Locale

data class PhoneContact(val id: String, val name: String, val number: String, val label: String)

/** Full numbers only: two country-qualified numbers must agree, never just a suffix. */
fun sameContactNumber(incoming: String, incomingE164: String?, saved: String, savedE164: String?): Boolean {
    if (incoming.any(Char::isLetter)) return false
    if (incomingE164 != null && savedE164 != null) return incomingE164 == savedE164
    val digits = normaliseNumber(incoming)
    return digits.isNotEmpty() && digits == normaliseNumber(saved)
}

fun searchContacts(contacts: List<PhoneContact>, query: String): List<PhoneContact> {
    fun folded(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    val text = folded(query.trim())
    if (text.isEmpty()) return contacts
    val digits = normaliseNumber(query).takeIf { it.isNotEmpty() && query.none(Char::isLetter) }
    return contacts.filter { folded(it.name).contains(text) || (digits != null && normaliseNumber(it.number).contains(digits)) }
}

/** Never silently truncate a copied/forwarded/AI draft or discard existing text. */
object ComposerRules {
    const val LIMIT = 1600
    fun insert(original: String, inserted: String, start: Int = original.length, end: Int = start): String? {
        if (start !in 0..original.length || end !in start..original.length) return null
        if (original.length - (end - start) + inserted.length > LIMIT) return null
        return original.substring(0, start) + inserted + original.substring(end)
    }
    fun append(original: String, inserted: String): String? = insert(original, if (original.isBlank()) inserted else "\n$inserted")
}
