package com.gediadeepak.smsapp.core

enum class SimLine(val defaultLabel: String, val slot: Int) { PERSONAL("Personal", 1), WORK("Work", 2), UNKNOWN("Unknown SIM", 0) }
enum class Folder(val label: String) { INBOX("Inbox"), UNREAD("Unread"), STARRED("Starred"), ARCHIVED("Archived"), SPAM("Spam") }
enum class Delivery { RECEIVED, SENDING, SENT, DELIVERED, FAILED, UNDELIVERED, UNKNOWN }
enum class ThemeMode(val label: String) { BLUE("Blue & white"), GREEN("Green & white"), DARK("Black & white"), PURPLE("Purple & white"), ROSE("Rose & white"), AMBER("Amber & white") }
data class DeviceSim(val subscriptionId: Int, val slotIndex: Int, val displayName: String, val carrier: String) {
    val line get() = when (slotIndex) { 0 -> SimLine.PERSONAL; 1 -> SimLine.WORK; else -> SimLine.UNKNOWN }
    val label get() = "SIM ${slotIndex + 1} · $displayName"
}
enum class Tone(val label: String) { FRIENDLY("Friendly"), CONCISE("Short"), PROFESSIONAL("Professional") }

data class Message(
    val id: String,
    val text: String,
    val outgoing: Boolean,
    val timestamp: Long,
    val line: SimLine,
    val delivery: Delivery = if (outgoing) Delivery.DELIVERED else Delivery.RECEIVED,
    val subscriptionId: Int? = null,
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
    val subscriptionId: Int? = null,
    val spam: Boolean = false,
    val blocked: Boolean = false,
    val contactName: String? = null,
) {
    val displayName get() = contactName ?: name
    val initials: String get() = displayName.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase()
}

data class Preferences(
    val personalLabel: String = "Personal",
    val workLabel: String = "Work",
    val suggestions: Boolean = true,
    val theme: ThemeMode = ThemeMode.BLUE,
    val defaultSubscriptionId: Int? = null,
    val aiEnabled: Boolean = false,
    val aiSummaries: Boolean = true,
    val aiReplies: Boolean = true,
) {
    fun label(line: SimLine): String = when (line) { SimLine.PERSONAL -> personalLabel; SimLine.WORK -> workLabel; SimLine.UNKNOWN -> "Unknown SIM" }
}

/** Never substitute a different SIM when a saved subscription disappears. */
fun resolveSendingSubscription(defaultId: Int?, activeIds: List<Int>): Int? =
    if (defaultId != null) defaultId.takeIf { it in activeIds } else activeIds.singleOrNull()

data class PartResult(val sent: Int = 0, val delivered: Int = 0)
fun aggregateDelivery(parts: List<PartResult>): Delivery = when {
    parts.isEmpty() -> Delivery.UNKNOWN
    parts.any { it.sent < 0 } -> Delivery.FAILED
    parts.all { it.delivered > 0 } -> Delivery.DELIVERED
    parts.any { it.delivered < 0 } -> Delivery.UNDELIVERED
    parts.all { it.sent > 0 } -> Delivery.SENT
    else -> Delivery.SENDING
}

fun filterConversations(items: List<Conversation>, folder: Folder, line: SimLine?, query: String): List<Conversation> {
    val search = query.trim()
    return items.filter { c ->
        val unwanted = c.spam || c.blocked
        val folderMatch = if (folder == Folder.SPAM) unwanted else !unwanted && when (folder) {
            Folder.ARCHIVED -> c.archived
            Folder.INBOX -> !c.archived
            Folder.UNREAD -> !c.archived && c.unread > 0
            Folder.STARRED -> !c.archived && c.starred
            Folder.SPAM -> false
        }
        folderMatch && (line == null || c.line == line) &&
            (search.isEmpty() || c.displayName.contains(search, true) || c.name.contains(search, true) || c.number.contains(search, true) || c.messages.any { it.text.contains(search, true) })
    }.sortedByDescending { it.messages.lastOrNull()?.timestamp ?: Long.MAX_VALUE }
}

fun normaliseNumber(number: String): String = number.filter(Char::isDigit)
fun validNumber(number: String): Boolean = number.matches(Regex("\\+?[0-9 ()-]{6,22}")) && normaliseNumber(number).length in 6..15
