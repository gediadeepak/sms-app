package com.gediadeepak.smsapp

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gediadeepak.smsapp.core.*
import com.gediadeepak.smsapp.data.*
import com.gediadeepak.smsapp.telephony.*
import com.gediadeepak.smsapp.audio.HeadsetReadout
import com.gediadeepak.smsapp.ai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class InboxState(
    val conversations: List<Conversation> = emptyList(), val preferences: Preferences = Preferences(),
    val folder: Folder = Folder.INBOX, val filterLine: SimLine? = null, val query: String = "",
    val selectedId: String? = null, val draft: String = "", val sendLine: SimLine = SimLine.UNKNOWN,
    val sendSubscriptionId: Int? = null, val subscriptions: List<DeviceSim> = emptyList(),
    val defaultSms: Boolean = false, val permissionsGranted: Boolean = false, val hasTelephony: Boolean = true,
    val loading: Boolean = false, val sending: Boolean = false, val error: String? = null, val pendingMms: Int = 0,
    val notificationStatus: SmsNotificationStatus = SmsNotificationStatus.ENABLED,
    val blockedSenders: List<String> = emptyList(),
    val contactsGranted: Boolean = false, val contacts: List<PhoneContact> = emptyList(),
    val watchReviewRequest: String? = null,
    val inboxIds: List<String>? = null, val inboxNext: SmsPageKey? = null,
    val loadingMore: Boolean = false, val inboxPageError: String? = null,
    val historyLoading: Boolean = false, val historyPageError: String? = null,
) {
    val selected get() = conversations.find { it.id == selectedId }
    val visible get() = inboxIds?.let { ids -> val byId = conversations.associateBy { it.id }; ids.mapNotNull(byId::get) }
        ?: filterConversations(conversations, folder, filterLine, query)
    val unreadCount get() = conversations.count { !it.archived && !it.spam && !it.blocked && it.unread > 0 }
    val ready get() = defaultSms && permissionsGranted
    val canSend get() = ready && !sending && selected?.spam != true && selected?.blocked != true && sendSubscriptionId != null && subscriptions.any { it.subscriptionId == sendSubscriptionId }
}

class SandeshViewModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>().applicationContext
    private val preferences = PreferenceStore(context)
    private val repository = DeviceSmsRepository(context)
    private val database = SandeshDatabase.get(context)
    private val contactsRepository = ContactsRepository(context)
    private val paging = SmsPagingRepository(context, contactsRepository)
    private val mutableState = MutableStateFlow(InboxState(preferences = preferences.load()))
    val state = mutableState.asStateFlow()
    private val assistant = FirebaseSmsAssistant(context)
    private val mutableAi = MutableStateFlow(AiUiState())
    val ai = mutableAi.asStateFlow()
    private val mutableAiAccess = MutableStateFlow(AiAccessState())
    val aiAccess = mutableAiAccess.asStateFlow()
    private var aiJob: Job? = null
    private var aiGeneration = 0

    fun checkAiAccess() {
        if (mutableAiAccess.value.checking) return
        mutableAiAccess.value = AiAccessState(checking = true)
        viewModelScope.launch {
            try {
                assistant.verifyAccess()
                mutableAiAccess.value = AiAccessState(message = "Device verification succeeded. No SMS was shared and no Gemini request was made. Model access and quota are checked when you generate an answer.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutableAiAccess.value = AiAccessState(message = if (e is AiRequestException) e.message else "Device verification could not complete. Try again later.")
            }
        }
    }

    fun cancelAi() {
        aiGeneration++
        aiJob?.cancel()
        mutableAi.value = AiUiState()
    }

    private fun invalidateAiIfChanged() {
        val current = mutableAi.value
        val selected = state.value.selected
        if (current.conversationId != null && (selected == null || selected.id != current.conversationId || selected.spam || selected.blocked ||
                !state.value.ready || !SmsAccess.ready(context) || !state.value.preferences.aiEnabled || AiContextPolicy.prepare(selected).fingerprint != current.fingerprint)) {
            cancelAi()
        }
    }

    fun generateAi(task: AiTask, tone: Tone, expectedFingerprint: String, insertIntoDraft: Boolean = false) {
        val snapshot = state.value
        val conversation = snapshot.selected ?: return
        if (!snapshot.ready || !conversation.historyReady || snapshot.historyLoading || !SmsAccess.ready(context) || conversation.spam || conversation.blocked || !snapshot.preferences.aiEnabled ||
            (task == AiTask.SUMMARY && !snapshot.preferences.aiSummaries) || (task == AiTask.REPLIES && !snapshot.preferences.aiReplies)) return
        if (mutableAi.value.loading) return
        val input = AiContextPolicy.prepare(conversation)
        if (input.fingerprint != expectedFingerprint) {
            mutableAi.value = AiUiState(error = "The conversation changed. Review the updated text preview before generating.")
            return
        }
        cancelAi()
        val generation = aiGeneration
        mutableAi.value = AiUiState(conversation.id, input.fingerprint, loading = true)
        aiJob = viewModelScope.launch {
            try {
                val answer = assistant.generate(input, task, tone)
                invalidateAiIfChanged()
                if (generation == aiGeneration) {
                    mutableAi.value = AiUiState(conversation.id, input.fingerprint, summary = answer.summary, replies = answer.replies)
                    if (insertIntoDraft && task == AiTask.REPLIES && answer.replies.isNotEmpty()) {
                        if (state.value.draft == snapshot.draft) insertAiReply(answer.replies.first())
                        else mutableAi.update { it.copy(error = "Your draft changed while AI was working. Choose a suggestion below to preserve your edits.") }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                invalidateAiIfChanged()
                if (generation == aiGeneration) mutableAi.value = AiUiState(conversation.id, input.fingerprint, error =
                    if (e is AiRequestException) e.message else "AI is unavailable. Try again later.")
            }
        }
    }

    fun insertAiReply(text: String): Boolean {
        invalidateAiIfChanged()
        val result = mutableAi.value
        val snapshot = state.value
        if (result.conversationId != snapshot.selectedId || text !in result.replies || !snapshot.preferences.aiEnabled || !snapshot.preferences.aiReplies) return false
        val base = if (snapshot.draft == result.insertedDraft) result.draftBeforeInsertion.orEmpty() else snapshot.draft
        val combined = ComposerRules.append(base, text)
        if (combined == null) {
            mutableAi.update { it.copy(error = "Your draft is too long to add this suggestion. Shorten it first.") }
            return false
        }
        draft(combined)
        mutableAi.update { it.copy(draftBeforeInsertion = base, insertedDraft = combined, insertedReply = text, error = null) }
        return true
    }
    private val drafts = mutableMapOf<String, Pair<String, Int?>>()
    private val emptyThreads = mutableMapOf<String, Conversation>()
    private var refreshJob: Job? = null
    private var inboxMoreJob: Job? = null
    private var historyJob: Job? = null
    private var inboxWindow = SmsPaging.SIZE
    private var historyVersion = 0
    private val historyWindows = mutableMapOf<String, Int>()
    private val historyKeys = mutableMapOf<String, SmsPageKey?>()

    private suspend fun <T> providerLoad(block: (android.os.CancellationSignal) -> T): T = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val signal = android.os.CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try { val result = block(signal); if (continuation.isActive) continuation.resume(result) }
            catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
        }
    }
    private var openingConversation: Job? = null
    private var refreshVersion = 0
    private var restoring = false
    private var completedRestore = 0
    fun setRestoring(value: Boolean, revision: Int) {
        restoring = value
        if (value) cancelAi()
        if (value) { refreshJob?.cancel(); inboxMoreJob?.cancel(); historyJob?.cancel(); historyVersion++ }
        else if (revision != completedRestore) {
            completedRestore = revision
            mutableState.update { it.copy(preferences = preferences.load(), sendSubscriptionId = null, sendLine = SimLine.UNKNOWN) }
            refresh()
        }
    }
    private var subscriptionListenerRegistered = false
    private var observerRegistered = false
    private var contactsObserverRegistered = false
    private var foreground = false
    private var windowFocused = false
    private var contentOverlayVisible = false
    private var accountVisible = false
    private fun conversationVisible() = foreground && windowFocused && !accountVisible && !contentOverlayVisible
    fun setWindowFocused(value: Boolean) { windowFocused = value; if (value) refresh() }
    fun setContentOverlayVisible(value: Boolean) { contentOverlayVisible = value; if (!value) refresh() }
    fun setAccountVisible(value: Boolean) { accountVisible = value; if (!value) refresh() }
    fun setForeground(value: Boolean) { foreground = value; if (value) refresh() }
    private val subscriptionListener = object : SubscriptionManager.OnSubscriptionsChangedListener() { override fun onSubscriptionsChanged() { refresh() } }
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { refresh() } }
    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { refreshContacts() } }
    fun refreshContacts() { contactsRepository.invalidate(); refresh() }
    init {
        // Some OEMs restrict provider observers before the SMS role is granted.
        observerRegistered = runCatching { context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer) }.isSuccess
        refresh()
    }

    fun refresh() {
        if (restoring) return
        val version = ++refreshVersion
        refreshJob?.cancel()
        inboxMoreJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(120)
            val default = SmsAccess.isDefault(context)
            val ready = SmsAccess.ready(context)
            val sims = SmsAccess.subscriptions(context)
            val notificationStatus = SmsNotifications.status(context)
            val contactsAllowed = contactsRepository.allowed()
            if (contactsAllowed && !contactsObserverRegistered) {
                contactsObserverRegistered = runCatching { context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactsObserver) }.isSuccess
            } else if (!contactsAllowed && contactsObserverRegistered) {
                runCatching { context.contentResolver.unregisterContentObserver(contactsObserver) }; contactsObserverRegistered = false
                contactsRepository.invalidate()
            }
            mutableState.update { current ->
                val id = current.sendSubscriptionId?.takeIf { id -> sims.any { it.subscriptionId == id } }
                current.copy(defaultSms = default, permissionsGranted = ready, subscriptions = sims, notificationStatus = notificationStatus,
                    hasTelephony = SmsAccess.hasTelephony(context), loading = ready, loadingMore = false, sendSubscriptionId = id,
                    sendLine = sims.find { it.subscriptionId == id }?.line ?: SimLine.UNKNOWN,
                    contactsGranted = contactsAllowed, contacts = if (contactsAllowed) current.contacts else emptyList(),
                    conversations = if (!ready) emptyList() else if (contactsAllowed) current.conversations else current.conversations.map { it.copy(contactName = null) })
            }
            if (!subscriptionListenerRegistered && SmsAccess.allowed(context, android.Manifest.permission.READ_PHONE_STATE)) {
                runCatching { context.getSystemService(SubscriptionManager::class.java).addOnSubscriptionsChangedListener(subscriptionListener); subscriptionListenerRegistered = true }
            }
            invalidateAiIfChanged()
            if (!ready) { historyJob?.cancel(); historyVersion++; return@launch }
            if (!observerRegistered) observerRegistered = runCatching { context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer) }.isSuccess
            try {
                val snapshot = state.value
                val result = providerLoad { signal ->
                    repository.drainPendingIncoming()
                    val contactList = contactsRepository.load()
                    val loaded = paging.inbox(sims, snapshot.folder, snapshot.filterLine, snapshot.query, null, inboxWindow, signal)
                    val selected = snapshot.selectedId?.let { id -> loaded.items.find { it.id == id } ?: paging.thread(id, sims, signal) }
                    Triple(loaded, selected, contactList)
                }
                val (blocked, pendingMms) = withContext(Dispatchers.IO) { database.blockedSenders().values.toList() to database.pendingMmsCount() }
                if (version != refreshVersion || !SmsAccess.ready(context)) return@launch
                val visibleThread = state.value.selectedId.takeIf { conversationVisible() }
                if ((result.second?.takeIf { it.id == visibleThread }?.unread ?: 0) > 0) withContext(Dispatchers.IO) { repository.markRead(visibleThread!!) }
                val contactsStillAllowed = contactsRepository.allowed()
                mutableState.update { current ->
                    val summaries = (result.first.items + listOfNotNull(result.second).filter { selected -> result.first.items.none { it.id == selected.id } } +
                        listOfNotNull(current.selected).filter { old -> old.id != snapshot.selectedId } + emptyThreads.values).distinctBy { it.id }
                    current.copy(conversations = summaries.map { summary ->
                        val old = current.selected?.takeIf { it.id == summary.id && it.historyReady }
                        summary.copy(messages = old?.messages ?: summary.messages, historyReady = old != null || summary.historyReady,
                            hasOlderMessages = old?.hasOlderMessages ?: summary.hasOlderMessages,
                            unread = if (summary.id == visibleThread) 0 else summary.unread, contactName = summary.contactName.takeIf { contactsStillAllowed })
                    }, inboxIds = result.first.items.map { it.id }, inboxNext = result.first.next, inboxPageError = null,
                        contactsGranted = contactsStillAllowed, contacts = if (contactsStillAllowed) result.third else emptyList(), loading = false, pendingMms = pendingMms, blockedSenders = blocked)
                }
                state.value.selectedId?.let { loadHistory(it) }
                invalidateAiIfChanged()
            } catch (e: CancellationException) { throw e }
            catch (_: SecurityException) { mutableState.update { it.copy(loading = false, permissionsGranted = false, conversations = emptyList(), error = "SMS access changed. Check Sandesh’s permissions.") } }
            catch (_: Exception) { mutableState.update { it.copy(loading = false, error = "Could not load the SMS inbox. Check access and try Refresh.") } }
        }
    }
    fun loadMoreInbox() {
        val snapshot = state.value
        val next = snapshot.inboxNext ?: return
        if (!snapshot.ready || snapshot.loading || snapshot.loadingMore || restoring) return
        val version = refreshVersion
        mutableState.update { it.copy(loadingMore = true, inboxPageError = null) }
        inboxMoreJob = viewModelScope.launch {
            try {
                val page = providerLoad { paging.inbox(snapshot.subscriptions, snapshot.folder, snapshot.filterLine, snapshot.query, next, SmsPaging.SIZE, it) }
                if (version != refreshVersion || !SmsAccess.ready(context)) return@launch
                mutableState.update { current -> current.copy(
                    conversations = current.conversations + page.items.filter { row -> current.conversations.none { it.id == row.id } },
                    inboxIds = (current.inboxIds.orEmpty() + page.items.map { it.id }).distinct(), inboxNext = page.next, loadingMore = false) }
                inboxWindow = maxOf(SmsPaging.SIZE, state.value.inboxIds.orEmpty().size)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (version == refreshVersion) mutableState.update { it.copy(loadingMore = false, inboxPageError = "Could not load more conversations. Tap to retry.") } }
        }
    }
    private fun loadHistory(id: String, older: Boolean = false) {
        historyJob?.cancel()
        val version = ++historyVersion
        val snapshot = state.value
        val before = if (older) historyKeys[id] ?: return else null
        mutableState.update { it.copy(historyLoading = true, historyPageError = null) }
        historyJob = viewModelScope.launch {
            try {
                val page = providerLoad { paging.history(id, snapshot.subscriptions, before, if (older) SmsPaging.SIZE else historyWindows[id] ?: SmsPaging.SIZE, it) }
                if (version != historyVersion || state.value.selectedId != id || !SmsAccess.ready(context)) return@launch
                historyKeys[id] = page.next
                mutableState.update { current -> current.copy(historyLoading = false, conversations = current.conversations.map { c ->
                    if (c.id != id) c else c.copy(messages = if (older) SmsPaging.merge(page.items, c.messages) else page.items, historyReady = true, hasOlderMessages = page.hasMore)
                }) }
                historyWindows[id] = maxOf(SmsPaging.SIZE, state.value.selected?.messages?.size ?: SmsPaging.SIZE)
                invalidateAiIfChanged()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (version == historyVersion) mutableState.update { it.copy(historyLoading = false, historyPageError = "Could not load these messages. Tap to retry.") } }
        }
    }
    fun loadMoreMessages() {
        val snapshot = state.value
        if (snapshot.historyLoading || !snapshot.ready || restoring) return
        val c = snapshot.selected ?: return
        loadHistory(c.id, older = c.historyReady && c.hasOlderMessages)
    }
    private fun restartInbox() {
        inboxWindow = SmsPaging.SIZE
        mutableState.update { it.copy(inboxIds = emptyList(), inboxNext = null, inboxPageError = null) }
        refresh()
    }
    private suspend fun ensureThread(id: String): Boolean {
        if (id.toLongOrNull() == null || !SmsAccess.ready(context)) return false
        if (state.value.conversations.any { it.id == id }) return true
        val row = providerLoad { paging.thread(id, state.value.subscriptions, it) } ?: return false
        mutableState.update { it.copy(conversations = it.conversations + row) }
        return true
    }
    private fun rememberDraft() { state.value.selectedId?.let { drafts[it] = state.value.draft to state.value.sendSubscriptionId } }
    private fun releaseHistory(id: String?) {
        if (id == null) return
        historyWindows.remove(id); historyKeys.remove(id)
        mutableState.update { current -> current.copy(conversations = current.conversations.map { c ->
            if (c.id != id) c else c.copy(messages = c.messages.takeLast(1), historyReady = c.messages.isEmpty(), hasOlderMessages = c.messages.isNotEmpty())
        }) }
    }
    fun select(id: String) {
        if (state.value.selectedId != id) cancelAi()
        rememberDraft()
        if (state.value.conversations.none { it.id == id }) return
        if (state.value.selectedId != id) releaseHistory(state.value.selectedId)
        val visible = conversationVisible()
        val stored = drafts[id]
        val ids = state.value.subscriptions.map { it.subscriptionId }
        val subscription = if (stored != null) stored.second?.takeIf { it in ids } else resolveSendingSubscription(state.value.preferences.defaultSubscriptionId, ids)
        mutableState.update { it.copy(selectedId = id, draft = stored?.first.orEmpty(), sendSubscriptionId = subscription,
            sendLine = it.subscriptions.find { sim -> sim.subscriptionId == subscription }?.line ?: SimLine.UNKNOWN,
            conversations = it.conversations.map { item -> if (item.id == id && visible) item.copy(unread = 0) else item }) }
        if (visible) viewModelScope.launch(Dispatchers.IO) { runCatching { repository.markRead(id) } }
        loadHistory(id)
    }
    fun back() { cancelAi(); historyJob?.cancel(); historyVersion++; rememberDraft(); releaseHistory(state.value.selectedId); mutableState.update { it.copy(selectedId = null, draft = "", sendSubscriptionId = null, historyLoading = false, historyPageError = null) } }
    fun folder(folder: Folder) { back(); mutableState.update { it.copy(folder = folder) }; restartInbox() }
    fun filter(line: SimLine?) { back(); mutableState.update { it.copy(filterLine = line) }; restartInbox() }
    fun search(query: String) { mutableState.update { it.copy(query = query) }; restartInbox() }
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
    fun moveToSpam(block: Boolean) {
        val selected = state.value.selected ?: return
        viewModelScope.launch {
            try {
                HeadsetReadout.stop(context)
                val affected = if (block) providerLoad { paging.senderThreads(selected.number, it) } + selected.id else listOf(selected.id)
                withContext(Dispatchers.IO) {
                    if (block) database.changeBlockedSender(selected.number, true, affected)
                    else database.updateMetadata(selected.id) { it.copy(spam = true, archived = false) }
                    affected.forEach { SmsNotifications.cancel(context, it) }
                }
                emptyThreads.remove(selected.id); back(); refresh()
            } catch (_: Exception) { reportError("Could not update the spam list. Your messages were kept.") }
        }
    }
    fun restoreFromSpam() {
        val selected = state.value.selected ?: return
        if (selected.blocked) { unblockSender(selected.number); return }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { database.updateMetadata(selected.id) { it.copy(spam = false, archived = false) } }; back(); refresh() }
            catch (_: Exception) { reportError("Could not restore this conversation. Your messages were kept.") }
        }
    }
    fun unblockSender(address: String) {
        viewModelScope.launch {
            try {
                val affected = (providerLoad { paging.senderThreads(address, it) } + state.value.conversations.filter { spamSenderKey(it.number) == spamSenderKey(address) }.map { it.id }).distinct()
                withContext(Dispatchers.IO) {
                    database.changeBlockedSender(address, false, affected)
                }
                affected.forEach { emptyThreads.remove(it) }
                if (state.value.selectedId in affected) back()
                refresh()
            } catch (_: Exception) { reportError("Could not unblock this sender. Try again.") }
        }
    }
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
    fun newConversation(name: String, number: String, subscription: Int?, body: String? = null, appendBody: Boolean = false): String? {
        val destination = number.trim()
        if (!validNumber(destination)) return "Enter a valid phone number with 6–15 digits."
        if (!state.value.ready) return "Complete SMS setup first."
        if (openingConversation?.isActive == true) return "A conversation is opening. Please wait a moment."
        openingConversation = viewModelScope.launch {
            try {
                val id = withContext(Dispatchers.IO) { repository.threadId(destination).also { id -> if (name.isNotBlank()) repository.updateMetadata(id) { it.copy(alias = name.trim().take(60)) } } }
                val (blocked, spam) = withContext(Dispatchers.IO) { database.isSenderBlocked(destination) to (database.threadMetadata(id)?.spam == true) }
                if (appendBody && (blocked || spam)) { reportError("Unblock or restore this recipient before forwarding."); return@launch }
                val contactName = withContext(Dispatchers.IO) { contactsRepository.name(destination) }
                val existingDraft = if (state.value.selectedId == id) state.value.draft else drafts[id]?.first.orEmpty()
                val preparedBody = if (body != null && appendBody) ComposerRules.append(existingDraft, body) else body
                if (body != null && (preparedBody == null || preparedBody.length > ComposerRules.LIMIT)) { reportError("The forwarded message and existing draft exceed 1,600 characters. Nothing was replaced."); return@launch }
                val line = state.value.subscriptions.find { it.subscriptionId == subscription }?.line ?: SimLine.UNKNOWN
                if (state.value.conversations.none { it.id == id }) {
                    val c = Conversation(id, name.trim().ifBlank { destination }, destination, line, subscriptionId = subscription, blocked = blocked, spam = spam, contactName = contactName)
                    emptyThreads[id] = c; mutableState.update { it.copy(conversations = it.conversations + c) }
                } else mutableState.update { it.copy(conversations = it.conversations.map { c -> if (c.id == id) c.copy(
                    name = name.trim().take(60).ifBlank { c.name }, contactName = contactName, spam = spam, blocked = blocked) else c }) }
                select(id); sendSubscription(subscription); if (preparedBody != null) draft(preparedBody)
                mutableState.update { it.copy(folder = Folder.INBOX, query = "", filterLine = null) }
                restartInbox()
            } catch (_: Exception) { mutableState.update { it.copy(error = "Could not open that conversation. Check SMS access.") } }
        }
        return null
    }

    fun forwardMessage(messageId: String, number: String, subscription: Int?): String? {
        val source = state.value.conversations.asSequence().flatMap { it.messages.asSequence() }.firstOrNull { it.id == messageId }
            ?: return "This message is no longer available."
        if (source.text.length > ComposerRules.LIMIT) return "This message is longer than the 1,600-character composer limit. You can copy or share it in full instead."
        return newConversation("", number, subscription, source.text, appendBody = true)
    }
    fun savePreferences(value: Preferences) {
        if (!value.aiEnabled || value.aiSummaries != state.value.preferences.aiSummaries || value.aiReplies != state.value.preferences.aiReplies) cancelAi()
        val previous = state.value.preferences.defaultSubscriptionId
        preferences.save(value); mutableState.update { it.copy(preferences = value) }
        if (previous != value.defaultSubscriptionId) sendSubscription(resolveSendingSubscription(value.defaultSubscriptionId, state.value.subscriptions.map { it.subscriptionId }))
    }
    fun openWatchDraft(threadId: String?, body: String?) {
        viewModelScope.launch {
            refreshJob?.join()
            if (threadId != null) try { ensureThread(threadId) } catch (_: Exception) { reportError("Could not load the watch conversation. Nothing was sent."); return@launch }
            val conversation = state.value.conversations.find { it.id == threadId }
            if (!state.value.ready || conversation == null || conversation.spam || conversation.blocked || !validNumber(conversation.number)) {
                reportError("This watch reply can no longer be opened. Check SMS access and the conversation in Sandesh. Nothing was sent."); return@launch
            }
            val existing = if (state.value.selectedId == threadId) state.value.draft else drafts[threadId]?.first.orEmpty()
            val combined = WatchReplyRules.merge(existing, body)
            if (combined == null) { reportError("The watch reply and existing draft exceed 1,600 characters or the reply is empty. Your existing draft was kept. Nothing was sent."); return@launch }
            select(conversation.id)
            draft(combined)
            mutableState.update { it.copy(folder = Folder.INBOX, query = "", filterLine = null, watchReviewRequest = java.util.UUID.randomUUID().toString()) }
        }
    }
    fun openWatchConversation(threadId: String?) {
        viewModelScope.launch {
            refreshJob?.join()
            try {
                if (threadId == null || !ensureThread(threadId)) { reportError("This watch conversation is unavailable."); return@launch }
                select(threadId)
                mutableState.update { it.copy(watchReviewRequest = java.util.UUID.randomUUID().toString()) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { reportError("Could not open the watch conversation. Nothing was sent.") }
        }
    }
    fun openExternal(number: String?, body: String?, threadId: String?) {
        viewModelScope.launch {
            refreshJob?.join(); if (!state.value.ready) return@launch
            if (threadId != null) {
                try { if (ensureThread(threadId)) select(threadId) else reportError("This conversation is no longer available.") }
                catch (_: Exception) { reportError("Could not open this conversation. Check SMS access and try again.") }
            }
            else if (!number.isNullOrBlank()) {
                val existing = state.value.conversations.find { android.telephony.PhoneNumberUtils.compare(it.number, number) }
                if (existing != null) { select(existing.id); if (body != null) draft(body) }
                else newConversation("", number, resolveSendingSubscription(state.value.preferences.defaultSubscriptionId, state.value.subscriptions.map { it.subscriptionId }), body)
            }
        }
    }
    override fun onCleared() {
        if (observerRegistered) runCatching { context.contentResolver.unregisterContentObserver(observer) }
        if (contactsObserverRegistered) runCatching { context.contentResolver.unregisterContentObserver(contactsObserver) }
        if (subscriptionListenerRegistered) runCatching { context.getSystemService(SubscriptionManager::class.java).removeOnSubscriptionsChangedListener(subscriptionListener) }
        super.onCleared()
    }
}
