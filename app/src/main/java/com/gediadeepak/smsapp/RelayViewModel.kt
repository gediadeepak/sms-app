package com.gediadeepak.smsapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gediadeepak.smsapp.core.*
import com.gediadeepak.smsapp.data.*
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxState(
    val conversations: List<Conversation>,
    val preferences: Preferences,
    val folder: Folder = Folder.INBOX,
    val filterLine: SimLine? = null,
    val query: String = "",
    val selectedId: String? = null,
    val draft: String = "",
    val sendLine: SimLine = SimLine.PERSONAL,
) {
    val selected get() = conversations.find { it.id == selectedId }
    val visible get() = filterConversations(conversations, folder, filterLine, query)
    val unreadCount get() = conversations.count { !it.archived && it.unread > 0 }
}

class RelayViewModel(application: Application) : AndroidViewModel(application) {
    private val store: MessageStore = LocalMessageStore(application)
    private val initial = store.load()
    private val mutableState = MutableStateFlow(InboxState(initial.conversations, initial.preferences))
    val state = mutableState.asStateFlow()
    private val drafts = mutableMapOf<String, Pair<String, SimLine>>()
    val assistant: ReplyAssistant = DemoReplyAssistant()

    private fun persist() { state.value.let { store.save(StoredInbox(it.conversations, it.preferences)) } }
    private fun editConversation(id: String, edit: (Conversation) -> Conversation) {
        mutableState.update { s -> s.copy(conversations = s.conversations.map { if (it.id == id) edit(it) else it }) }
        persist()
    }
    private fun rememberDraft() { state.value.selectedId?.let { drafts[it] = state.value.draft to state.value.sendLine } }
    fun select(id: String) {
        rememberDraft()
        val conversation = state.value.conversations.find { it.id == id } ?: return
        val draft = drafts[id]
        mutableState.update { it.copy(selectedId = id, draft = draft?.first.orEmpty(), sendLine = draft?.second ?: conversation.line) }
        editConversation(id) { it.copy(unread = 0) }
    }
    fun back() { rememberDraft(); mutableState.update { it.copy(selectedId = null, draft = "") } }
    fun folder(folder: Folder) { back(); mutableState.update { it.copy(folder = folder) } }
    fun filter(line: SimLine?) { back(); mutableState.update { it.copy(filterLine = line) } }
    fun search(query: String) { mutableState.update { it.copy(query = query) } }
    fun draft(value: String) { mutableState.update { it.copy(draft = value.take(1600)) } }
    fun polishDraft() {
        val original = state.value.draft.trim()
        if (original.isEmpty()) return
        var polished = original.replace(Regex("\\s+"), " ").replace(Regex("\\bi\\b"), "I")
            .replace(Regex("\\bim\\b", RegexOption.IGNORE_CASE), "I'm")
            .replace(Regex("\\bthanks\\b", RegexOption.IGNORE_CASE), "Thank you")
        polished = polished.replaceFirstChar { it.uppercase() }
        if (polished.last() !in ".!?…😊☕") polished += "."
        draft(polished)
    }
    fun sendLine(line: SimLine) { mutableState.update { it.copy(sendLine = line) } }
    fun star() { state.value.selectedId?.let { id -> editConversation(id) { it.copy(starred = !it.starred) } } }
    fun archive() {
        state.value.selectedId?.let { id -> editConversation(id) { it.copy(archived = !it.archived, unread = 0) } }
        back()
    }
    fun send() {
        val snapshot = state.value
        val conversation = snapshot.selected ?: return
        val text = snapshot.draft.trim().takeIf { it.isNotBlank() } ?: return
        val message = Message(UUID.randomUUID().toString(), text, true, System.currentTimeMillis(), snapshot.sendLine, Delivery.SENDING)
        editConversation(conversation.id) { it.copy(messages = it.messages + message, line = snapshot.sendLine, archived = false, unread = 0) }
        drafts.remove(conversation.id)
        mutableState.update { it.copy(draft = "", folder = if (it.folder == Folder.ARCHIVED || it.folder == Folder.UNREAD) Folder.INBOX else it.folder, filterLine = if (it.filterLine == snapshot.sendLine) it.filterLine else null) }
        viewModelScope.launch {
            delay(600)
            editConversation(conversation.id) { c -> c.copy(messages = c.messages.map { if (it.id == message.id) it.copy(delivery = Delivery.SENT) else it }) }
            delay(800)
            editConversation(conversation.id) { c -> c.copy(messages = c.messages.map { if (it.id == message.id) it.copy(delivery = Delivery.DELIVERED) else it }) }
        }
    }
    /** Returns a validation error, or null after opening/creating the conversation. */
    fun newConversation(name: String, number: String, line: SimLine): String? {
        val cleanNumber = number.trim()
        if (!validNumber(cleanNumber)) return "Enter a valid phone number with 6–15 digits."
        val existing = state.value.conversations.find { normaliseNumber(it.number) == normaliseNumber(cleanNumber) }
        val id = existing?.id ?: UUID.randomUUID().toString()
        if (existing == null) {
            val conversation = Conversation(id, name.trim().ifBlank { cleanNumber }.take(60), cleanNumber, line)
            mutableState.update { it.copy(conversations = it.conversations + conversation) }
        } else editConversation(id) { it.copy(archived = false) }
        select(id)
        mutableState.update { it.copy(folder = Folder.INBOX, query = "", filterLine = null, sendLine = line) }
        persist()
        return null
    }
    fun savePreferences(preferences: Preferences) { mutableState.update { it.copy(preferences = preferences) }; persist() }
    fun simulateIncoming() {
        val conversation = state.value.conversations.firstOrNull { it.id == "maya" } ?: state.value.conversations.firstOrNull() ?: return
        editConversation(conversation.id) {
            it.copy(messages = it.messages + Message(UUID.randomUUID().toString(), "Just checking in — hope your day is going well! 😊", false, System.currentTimeMillis(), it.line), unread = if (state.value.selectedId == it.id) 0 else it.unread + 1, archived = false)
        }
    }
    fun reset() {
        drafts.clear()
        mutableState.value = InboxState(DemoData.conversations(System.currentTimeMillis()), Preferences())
        persist()
    }
}
