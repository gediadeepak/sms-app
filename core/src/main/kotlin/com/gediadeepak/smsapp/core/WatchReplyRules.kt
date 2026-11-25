package com.gediadeepak.smsapp.core

/** Watch input is always a draft; reject overflow rather than silently changing the reply. */
object WatchReplyRules {
    fun reply(text: CharSequence?): String? = text?.toString()?.takeIf {
        it.isNotBlank() && it.length <= ComposerRules.LIMIT
    }

    fun merge(existing: String, reply: CharSequence?): String? =
        reply(reply)?.let { ComposerRules.append(existing, it) }
}
