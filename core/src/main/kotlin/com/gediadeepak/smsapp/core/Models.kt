package com.gediadeepak.smsapp.core

enum class SimLine(val defaultLabel: String, val slot: Int) { PERSONAL("Personal", 1), WORK("Work", 2) }
enum class Folder(val label: String) { INBOX("Inbox"), UNREAD("Unread"), STARRED("Starred"), ARCHIVED("Archived") }
enum class Delivery { RECEIVED, SENDING, SENT, DELIVERED }
enum class Tone(val label: String) { FRIENDLY("Friendly"), CONCISE("Short"), PROFESSIONAL("Professional") }

data class Message(
    val id: String,
    val text: String,
    val outgoing: Boolean,
    val timestamp: Long,
    val line: SimLine,
    val delivery: Delivery = if (outgoing) Delivery.DELIVERED else Delivery.RECEIVED,
)

data class Conversation(
    val id: String,
    val name: String,
    val number: String,
    val line: SimLine,
    val messages: List<Message> = emptyList(),
    val unread: Int = 0,
    val starred: Boolean = false,
    val archived: Boolean = false,
    val topic: String = "general",
) {
    val initials: String get() = name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase()
}

data class Preferences(
    val personalLabel: String = "Personal",
    val workLabel: String = "Work",
    val defaultLine: SimLine = SimLine.PERSONAL,
    val suggestions: Boolean = true,
) {
    fun label(line: SimLine): String = if (line == SimLine.PERSONAL) personalLabel else workLabel
}

fun filterConversations(items: List<Conversation>, folder: Folder, line: SimLine?, query: String): List<Conversation> {
    val search = query.trim()
    return items.filter { c ->
        val folderMatch = when (folder) {
            Folder.ARCHIVED -> c.archived
            Folder.INBOX -> !c.archived
            Folder.UNREAD -> !c.archived && c.unread > 0
            Folder.STARRED -> !c.archived && c.starred
        }
        folderMatch && (line == null || c.line == line) &&
            (search.isEmpty() || c.name.contains(search, true) || c.number.contains(search, true) || c.messages.any { it.text.contains(search, true) })
    }.sortedByDescending { it.messages.lastOrNull()?.timestamp ?: Long.MAX_VALUE }
}

fun normaliseNumber(number: String): String = number.filter(Char::isDigit)
fun validNumber(number: String): Boolean = number.matches(Regex("\\+?[0-9 ()-]{6,22}")) && normaliseNumber(number).length in 6..15

interface ReplyAssistant { fun suggest(conversation: Conversation, tone: Tone): List<String> }

/** Offline examples only. A future API-backed assistant can implement the same interface. */
class DemoReplyAssistant : ReplyAssistant {
    override fun suggest(conversation: Conversation, tone: Tone): List<String> {
        if (tone == Tone.PROFESSIONAL) return listOf("Thank you for your message. I’ll get back to you shortly.", "That works well for me. Thank you.", "Thanks for the update. I’ll review this and follow up.")
        if (tone == Tone.CONCISE) return listOf("Sounds good!", "Thanks for letting me know.", "Perfect, see you then!")
        return when (conversation.topic) {
            "coffee" -> listOf("Sounds perfect! See you at 3. ☕", "Count me in! Looking forward to a catch-up.", "3 works for me! Shall I bring anything?")
            "family" -> listOf("That sounds lovely! Wouldn’t miss it 😊", "Yes please! Can I bring anything?", "Looking forward to Sunday!")
            "project" -> listOf("Thanks for the update! I’ll take a look.", "That works for me. Let’s put it in the calendar.", "Looking forward to seeing it!")
            else -> listOf("Great to hear from you!", "Sounds good to me. Let’s catch up soon.", "Of course, happy to help 😊")
        }
    }
}
