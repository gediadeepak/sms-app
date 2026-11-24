package com.gediadeepak.smsapp.data

import android.content.Context
import com.gediadeepak.smsapp.core.*
import org.json.JSONArray
import org.json.JSONObject

data class StoredInbox(val conversations: List<Conversation>, val preferences: Preferences)

interface MessageStore {
    fun load(): StoredInbox
    fun save(inbox: StoredInbox)
}

/** Small demo inbox only; replace with Room before integrating the device SMS provider. */
class LocalMessageStore(context: Context) : MessageStore {
    private val storage = context.getSharedPreferences("relay_demo_v1", Context.MODE_PRIVATE)

    override fun load(): StoredInbox {
        val fallback = { StoredInbox(DemoData.conversations(System.currentTimeMillis()), Preferences()) }
        val raw = storage.getString("inbox", null) ?: return fallback()
        return runCatching {
            val root = JSONObject(raw)
            require(root.getInt("version") == 1)
            val settings = root.getJSONObject("preferences")
            val preferences = Preferences(
                settings.getString("personalLabel"), settings.getString("workLabel"),
                SimLine.valueOf(settings.getString("defaultLine")), settings.getBoolean("suggestions"),
            )
            val conversations = root.getJSONArray("conversations").objects().map { c ->
                Conversation(
                    id = c.getString("id"), name = c.getString("name"), number = c.getString("number"),
                    line = SimLine.valueOf(c.getString("line")), unread = c.getInt("unread"),
                    starred = c.getBoolean("starred"), archived = c.getBoolean("archived"), topic = c.getString("topic"),
                    messages = c.getJSONArray("messages").objects().map { m ->
                        val delivery = Delivery.valueOf(m.getString("delivery"))
                        Message(m.getString("id"), m.getString("text"), m.getBoolean("outgoing"),
                            m.getLong("timestamp"), SimLine.valueOf(m.getString("line")),
                            if (delivery == Delivery.SENDING || delivery == Delivery.SENT) Delivery.DELIVERED else delivery)
                    },
                )
            }
            StoredInbox(conversations, preferences)
        }.getOrElse { fallback() }
    }

    override fun save(inbox: StoredInbox) {
        val p = inbox.preferences
        val root = JSONObject().put("version", 1).put("preferences", JSONObject()
            .put("personalLabel", p.personalLabel).put("workLabel", p.workLabel)
            .put("defaultLine", p.defaultLine.name).put("suggestions", p.suggestions))
        root.put("conversations", JSONArray().apply {
            inbox.conversations.forEach { c -> put(JSONObject()
                .put("id", c.id).put("name", c.name).put("number", c.number).put("line", c.line.name)
                .put("unread", c.unread).put("starred", c.starred).put("archived", c.archived).put("topic", c.topic)
                .put("messages", JSONArray().apply { c.messages.forEach { m -> put(JSONObject()
                    .put("id", m.id).put("text", m.text).put("outgoing", m.outgoing)
                    .put("timestamp", m.timestamp).put("line", m.line.name).put("delivery", m.delivery.name)) } }))
            }
        })
        storage.edit().putString("inbox", root.toString()).apply()
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
}
