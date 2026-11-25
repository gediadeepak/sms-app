package com.gediadeepak.smsapp.core

import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

enum class AiTask { SUMMARY, REPLIES }

data class AiMessage(val id: String, val outgoing: Boolean, val text: String)
data class AiContext(val messages: List<AiMessage>, val excluded: Int, val limited: Boolean) {
    val fingerprint: String get() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(messages.size)
            messages.forEach { message ->
                output.writeUTF(message.id)
                output.writeBoolean(message.outgoing)
                val text = message.text.toByteArray(Charsets.UTF_8)
                output.writeInt(text.size)
                output.write(text)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** Conservative heuristic, not a guarantee that all sensitive text can be detected. */
object AiContextPolicy {
    private val sensitiveWords = Regex("\\b(otp|one[ -]?time|verification|verify|passcode|password|pin|cvv|credit|debit|bank|account|payment|transaction|balance|security code|authentication|login code|sign[ -]?in code)\\b|ओटीपी|पासवर्ड|सत्यापन", RegexOption.IGNORE_CASE)
    private val digitSequence = Regex("(?:\\p{Nd}[ \\-]?){4,}")
    fun sensitive(text: String): Boolean = sensitiveWords.containsMatchIn(text) || digitSequence.containsMatchIn(text)

    fun prepare(conversation: Conversation): AiContext {
        if (conversation.spam || conversation.blocked) return AiContext(emptyList(), 0, false)
        val window = conversation.messages.sortedBy { it.timestamp }.takeLast(50)
        val eligible = window.filter { it.text.isNotBlank() && !sensitive(it.text) }
        var remaining = 6_000
        var shortened = false
        val messages = eligible.takeLast(20).asReversed().mapNotNull { message ->
            if (remaining == 0) return@mapNotNull null
            val text = message.text.take(minOf(1_000, remaining))
            if (text.length != message.text.length) shortened = true
            remaining -= text.length
            AiMessage(message.id, message.outgoing, text)
        }.asReversed()
        return AiContext(messages, window.count { it.text.isNotBlank() && sensitive(it.text) },
            shortened || messages.size < eligible.size || window.size < conversation.messages.size || conversation.hasOlderMessages)
    }
}
