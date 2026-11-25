package com.gediadeepak.smsapp.telephony

import android.content.Context
import android.os.CancellationSignal
import com.gediadeepak.smsapp.core.Folder
import com.gediadeepak.smsapp.data.ContactsRepository
import com.gediadeepak.smsapp.data.PreferenceStore
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.sandesh.watch.WatchProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Google Play services authenticates matching package/signature peers. No DataItems or disk cache. */
class WatchInboxService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchProtocol.REQUEST || event.data.size > WatchProtocol.MAX_REQUEST) return
        val request = runCatching { JSONObject(String(event.data, Charsets.UTF_8)) }.getOrNull() ?: return
        val token = request.optString("id")
        val command = request.optString("command")
        val thread = request.optString("thread")
        if (request.optInt("version") != WatchProtocol.VERSION || !WatchProtocol.validToken(token) || !WatchProtocol.validCommand(command)) return
        if (command != "inbox" && !WatchProtocol.validThread(thread)) return
        val reply = JSONObject().put("version", WatchProtocol.VERSION).put("id", token).put("command", command)
        fun status(value: String) { reply.put("status", value) }
        try {
            val now = android.os.SystemClock.elapsedRealtime()
            val previous = requests.put(event.sourceNodeId, now)
            if (requests.size > 32) requests.keys.filter { it != event.sourceNodeId }.forEach(requests::remove)
            if (previous != null && now - previous < 600) { status("busy") }
            else if (!WatchSettings(this).load().inbox) { status("disabled") }
            else if (!SmsAccess.ready(this)) { status("access") }
            else {
                val sims = SmsAccess.subscriptions(this)
                val repository = SmsPagingRepository(this, ContactsRepository(this))
                val signal = CancellationSignal()
                val prefs = PreferenceStore(this).load()
                reply.put("theme", prefs.theme.name)
                if (command == "inbox") {
                    val rows = repository.inbox(sims, Folder.INBOX, null, "", null, WatchProtocol.CONVERSATIONS, signal).items
                    reply.put("rows", JSONArray().apply { rows.forEach { c ->
                        put(JSONObject().put("thread", c.id).put("sender", WatchProtocol.clip(c.displayName, 60))
                            .put("preview", WatchProtocol.clip(c.messages.lastOrNull()?.text, 140))
                            .put("date", c.messages.lastOrNull()?.timestamp ?: 0).put("unread", c.unread))
                    } })
                    status("ok")
                } else {
                    val c = repository.thread(thread, sims, signal)
                    if (c == null || c.spam || c.blocked || c.archived) status("unavailable")
                    else if (command == "open") status(if (SmsNotifications.watchOpen(this, thread)) "opened" else "notifications")
                    else {
                        val page = repository.history(thread, sims, null, WatchProtocol.HISTORY, signal)
                        reply.put("thread", thread).put("sender", WatchProtocol.clip(c.displayName, 60))
                            .put("messages", JSONArray().apply { page.items.forEach { m ->
                                put(JSONObject().put("text", WatchProtocol.clip(m.text, WatchProtocol.TEXT))
                                    .put("outgoing", m.outgoing).put("date", m.timestamp)
                                    .put("sim", prefs.label(m.line)))
                            } })
                        status("ok")
                    }
                }
                // Disabling the setting while a query runs revokes the response, too.
                if (!WatchSettings(this).load().inbox || !SmsAccess.ready(this)) {
                    reply.remove("rows"); reply.remove("messages"); reply.remove("sender"); status("disabled")
                }
            }
        } catch (_: Exception) { reply.remove("rows"); reply.remove("messages"); reply.remove("sender"); status("error") }
        runCatching {
            android.util.Log.i("SandeshWatch", "Reply: $command / ${reply.optString("status")}")
            Tasks.await(Wearable.getMessageClient(this).sendMessage(event.sourceNodeId, WatchProtocol.RESPONSE,
                WatchProtocol.bytes(reply.toString(), WatchProtocol.MAX_RESPONSE)), 4, TimeUnit.SECONDS)
        }
    }
    companion object {
        private val requests = ConcurrentHashMap<String, Long>()
        fun clearConnected(context: Context) {
            runCatching { Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
                nodes.forEach { node -> Wearable.getMessageClient(context).sendMessage(node.id, WatchProtocol.CLEAR, byteArrayOf()) }
            } }
        }
    }
}
