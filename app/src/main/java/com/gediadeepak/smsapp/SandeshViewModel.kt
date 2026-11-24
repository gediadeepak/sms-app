package com.gediadeepak.smsapp

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gediadeepak.smsapp.core.*
import com.gediadeepak.smsapp.data.*
import com.gediadeepak.smsapp.telephony.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class InboxState(
    val conversations: List<Conversation> = emptyList(), val preferences: Preferences = Preferences(),
    val folder: Folder = Folder.INBOX, val filterLine: SimLine? = null, val query: String = "",
    val selectedId: String? = null, val draft: String = "", val sendLine: SimLine = SimLine.UNKNOWN,
    val sendSubscriptionId: Int? = null, val subscriptions: List<DeviceSim> = emptyList(),
    val defaultSms: Boolean = false, val permissionsGranted: Boolean = false, val hasTelephony: Boolean = true,
    val loading: Boolean = false, val sending: Boolean = false, val error: String? = null, val pendingMms: Int = 0,
) {
    val selected get() = conversations.find { it.id == selectedId }
    val visible get() = filterConversations(conversations, folder, filterLine, query)
    val unreadCount get() = conversations.count { !it.archived && it.unread > 0 }
    val ready get() = defaultSms && permissionsGranted
    val canSend get() = ready && !sending && sendSubscriptionId != null && subscriptions.any { it.subscriptionId == sendSubscriptionId }
}

class SandeshViewModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>().applicationContext
    private val preferences = PreferenceStore(context)
    private val repository = DeviceSmsRepository(context)
    private val database = SandeshDatabase.get(context)
    private val mutableState = MutableStateFlow(InboxState(preferences = preferences.load()))
    val state = mutableState.asStateFlow()
    val assistant: ReplyAssistant = DemoReplyAssistant()
    private val drafts = mutableMapOf<String, Pair<String, Int?>>()
    private val emptyThreads = mutableMapOf<String, Conversation>()
    private var refreshJob: Job? = null
    private var refreshVersion = 0
    private var restoring = false
    private var completedRestore = 0
    fun setRestoring(value: Boolean, revision: Int) {
        restoring = value
        if (value) refreshJob?.cancel()
        else if (revision != completedRestore) {
            completedRestore = revision
            mutableState.update { it.copy(preferences = preferences.load(), sendSubscriptionId = null, sendLine = SimLine.UNKNOWN) }
            refresh()
        }
    }
    private var subscriptionListenerRegistered = false
    private var observerRegistered = false
    private var foreground = false
    private var accountVisible = false
    fun setAccountVisible(value: Boolean) { accountVisible = value; if (!value) refresh() }
    fun setForeground(value: Boolean) { foreground = value; if (value) refresh() }
    private val subscriptionListener = object : SubscriptionManager.OnSubscriptionsChangedListener() { override fun onSubscriptionsChanged() { refresh() } }
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { refresh() } }
    init {
        // Some OEMs restrict provider observers before the SMS role is granted.
        observerRegistered = runCatching { context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer) }.isSuccess
        refresh()
    }

    fun refresh() {
        if (restoring) return
        val version = ++refreshVersion
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(120)
            val default = SmsAccess.isDefault(context)
            val ready = SmsAccess.ready(context)
            val sims = SmsAccess.subscriptions(context)
            mutableState.update { current ->
                val id = current.sendSubscriptionId?.takeIf { id -> sims.any { it.subscriptionId == id } }
                current.copy(defaultSms = default, permissionsGranted = ready, subscriptions = sims,
                    hasTelephony = SmsAccess.hasTelephony(context), loading = ready, sendSubscriptionId = id,
                    sendLine = sims.find { it.subscriptionId == id }?.line ?: SimLine.UNKNOWN,
                    conversations = if (ready) current.conversations else emptyList())
            }
            if (!subscriptionListenerRegistered && SmsAccess.allowed(context, android.Manifest.permission.READ_PHONE_STATE)) {
                runCatching { context.getSystemService(SubscriptionManager::class.java).addOnSubscriptionsChangedListener(subscriptionListener); subscriptionListenerRegistered = true }
            }
            if (!ready) return@launch
            if (!observerRegistered) observerRegistered = runCatching { context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer) }.isSuccess
            try {
                val result = withContext(Dispatchers.IO) { repository.drainPendingIncoming(); repository.load(sims) to database.pendingMmsCount() }
                if (version != refreshVersion || !SmsAccess.ready(context)) return@launch
                val visibleThread = state.value.selectedId.takeIf { foreground && !accountVisible }
                if (result.first.any { it.id == visibleThread && it.unread > 0 }) withContext(Dispatchers.IO) { repository.markRead(visibleThread!!) }
                mutableState.update { it.copy(conversations = result.first.map { c -> if (c.id == visibleThread) c.copy(unread = 0) else c } + emptyThreads.values.filter { draft -> result.first.none { loaded -> loaded.id == draft.id } }, loading = false, pendingMms = result.second) }
            } catch (e: CancellationException) { throw e }
            catch (_: SecurityException) { mutableState.update { it.copy(loading = false, permissionsGranted = false, conversations = emptyList(), error = "SMS access changed. Check Sandesh’s permissions.") } }
            catch (_: Exception) { mutableState.update { it.copy(loading = false, error = "Could not load the SMS inbox. Check access and try Refresh.") } }
        }
    }
    private fun rememberDraft() { state.value.selectedId?.let { drafts[it] = state.value.draft to state.value.sendSubscriptionId } }
    fun select(id: String) {
        rememberDraft()
        if (state.value.conversations.none { it.id == id }) return
        val stored = drafts[id]
        val ids = state.value.subscriptions.map { it.subscriptionId }
        val subscription = if (stored != null) stored.second?.takeIf { it in ids } else resolveSendingSubscription(state.value.preferences.defaultSubscriptionId, ids)
        mutableState.update { it.copy(selectedId = id, draft = stored?.first.orEmpty(), sendSubscriptionId = subscription,
            sendLine = it.subscriptions.find { sim -> sim.subscriptionId == subscription }?.line ?: SimLine.UNKNOWN,
            conversations = it.conversations.map { item -> if (item.id == id) item.copy(unread = 0) else item }) }
        viewModelScope.launch(Dispatchers.IO) { runCatching { repository.markRead(id) } }
    }
    fun back() { rememberDraft(); mutableState.update { it.copy(selectedId = null, draft = "", sendSubscriptionId = null) } }
    fun folder(folder: Folder) { back(); mutableState.update { it.copy(folder = folder) } }
    fun filter(line: SimLine?) { back(); mutableState.update { it.copy(filterLine = line) } }
    fun search(query: String) { mutableState.update { it.copy(query = query) } }
    fun draft(value: String) { mutableState.update { it.copy(draft = value.take(1600)) } }
    fun clearError() { mutableState.update { it.copy(error = null) } }
    fun reportError(message: String) { mutableState.update { it.copy(error = message) } }
    fun polishDraft() {
        val original = state.value.draft.trim(); if (original.isEmpty()) return
        var polished = original.replace(Regex("\\s+"), " ").replace(Regex("\\bi\\b"), "I")
            .replace(Regex("\\bim\\b", RegexOption.IGNORE_CASE), "I'm").replace(Regex("\\bthanks\\b", RegexOption.IGNORE_CASE), "Thank you")
        polished = polished.replaceFirstChar { it.uppercase() }
        if (polished.last() !in ".!?…☕") polished += "."
        draft(polished)
    }
    fun sendSubscription(id: Int?) { mutableState.update { it.copy(sendSubscriptionId = id, sendLine = it.subscriptions.find { sim -> sim.subscriptionId == id }?.line ?: SimLine.UNKNOWN) } }
    fun star() { editMetadata { it.copy(starred = !it.starred) } }
    fun archive() { editMetadata { it.copy(archived = !it.archived) }; back() }
    private fun editMetadata(edit: (ThreadMetadata) -> ThreadMetadata) {
        val id = state.value.selectedId ?: return
        viewModelScope.launch { withContext(Dispatchers.IO) { repository.updateMetadata(id, edit) }; refresh() }
    }
    fun send() {
        val s = state.value; val c = s.selected ?: return
        val text = s.draft.trim().takeIf { it.isNotBlank() } ?: return
        if (!s.canSend) { mutableState.update { it.copy(error = "Choose an active SIM and complete SMS setup first.") }; return }
        mutableState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { SmsTransport(context).send(c.number, text, s.sendSubscriptionId!!) }
                drafts.remove(c.id)
                mutableState.update {
                    val askAgain = it.preferences.defaultSubscriptionId == null && it.subscriptions.size > 1
                    it.copy(draft = if (it.selectedId == c.id && it.draft == s.draft) "" else it.draft, folder = Folder.INBOX, filterLine = null,
                        sendSubscriptionId = if (askAgain) null else it.sendSubscriptionId, sendLine = if (askAgain) SimLine.UNKNOWN else it.sendLine)
                }
                withContext(Dispatchers.IO) { repository.updateMetadata(c.id) { it.copy(archived = false) } }; refresh()
            } catch (e: Exception) { mutableState.update { it.copy(error = e.message ?: "Could not submit the SMS. Check permissions and the selected SIM.") } }
            finally { mutableState.update { it.copy(sending = false) } }
        }
    }
    fun newConversation(name: String, number: String, subscription: Int?, body: String? = null): String? {
        val destination = number.trim()
        if (!validNumber(destination)) return "Enter a valid phone number with 6–15 digits."
        if (!state.value.ready) return "Complete SMS setup first."
        viewModelScope.launch {
            try {
                val id = withContext(Dispatchers.IO) { repository.threadId(destination).also { id -> if (name.isNotBlank()) repository.updateMetadata(id) { it.copy(alias = name.trim().take(60)) } } }
                val line = state.value.subscriptions.find { it.subscriptionId == subscription }?.line ?: SimLine.UNKNOWN
                if (state.value.conversations.none { it.id == id }) {
                    val c = Conversation(id, name.trim().ifBlank { destination }, destination, line, subscriptionId = subscription)
                    emptyThreads[id] = c; mutableState.update { it.copy(conversations = it.conversations + c) }
                }
                select(id); sendSubscription(subscription); if (body != null) draft(body)
                mutableState.update { it.copy(folder = Folder.INBOX, query = "", filterLine = null) }
            } catch (_: Exception) { mutableState.update { it.copy(error = "Could not open that conversation. Check SMS access.") } }
        }
        return null
    }
    fun savePreferences(value: Preferences) {
        val previous = state.value.preferences.defaultSubscriptionId
        preferences.save(value); mutableState.update { it.copy(preferences = value) }
        if (previous != value.defaultSubscriptionId) sendSubscription(resolveSendingSubscription(value.defaultSubscriptionId, state.value.subscriptions.map { it.subscriptionId }))
    }
    fun openExternal(number: String?, body: String?, threadId: String?) {
        viewModelScope.launch {
            refreshJob?.join(); if (!state.value.ready) return@launch
            if (threadId != null && state.value.conversations.any { it.id == threadId }) select(threadId)
            else if (!number.isNullOrBlank()) {
                val existing = state.value.conversations.find { android.telephony.PhoneNumberUtils.compare(it.number, number) }
                if (existing != null) { select(existing.id); if (body != null) draft(body) }
                else newConversation("", number, resolveSendingSubscription(state.value.preferences.defaultSubscriptionId, state.value.subscriptions.map { it.subscriptionId }), body)
            }
        }
    }
    override fun onCleared() {
        if (observerRegistered) runCatching { context.contentResolver.unregisterContentObserver(observer) }
        if (subscriptionListenerRegistered) runCatching { context.getSystemService(SubscriptionManager::class.java).removeOnSubscriptionsChangedListener(subscriptionListener) }
        super.onCleared()
    }
}
