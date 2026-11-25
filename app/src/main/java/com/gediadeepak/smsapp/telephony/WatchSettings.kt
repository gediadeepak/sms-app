package com.gediadeepak.smsapp.telephony

import android.content.Context

data class WatchOptions(val actions: Boolean = false, val previews: Boolean = false)

/** Device-specific opt-ins, deliberately outside the SMS backup format. */
class WatchSettings(context: Context) {
    private val storage = context.getSharedPreferences("sandesh_watch", Context.MODE_PRIVATE)
    fun load() = WatchOptions(storage.getBoolean("actions", false), storage.getBoolean("previews", false))
    fun save(options: WatchOptions) {
        storage.edit().putBoolean("actions", options.actions).putBoolean("previews", options.previews).apply()
    }
}
