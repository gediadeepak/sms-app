package com.gediadeepak.smsapp.core

data class SmsPageKey(val date: Long, val id: Long)
data class SmsPage<T>(val items: List<T>, val next: SmsPageKey?) {
    val hasMore get() = next != null
}

object SmsPaging {
    const val SIZE = 50
    const val ORDER = "date DESC, _id DESC"
    const val BEFORE = "(date<? OR (date=? AND _id<?))"
    // Aggregate in Android's provider, not by reading SMS bodies into the app.
    // A second MAX resolves equal timestamps deterministically, including restored old SMS.
    val LATEST = """_id IN (SELECT MAX(candidate._id) FROM sms AS candidate
        JOIN (SELECT thread_id, MAX(date) AS latest_date FROM sms WHERE type<>3 GROUP BY thread_id) AS latest
        ON candidate.thread_id=latest.thread_id AND candidate.date=latest.latest_date
        WHERE candidate.type<>3 GROUP BY candidate.thread_id)""".trimIndent().replace('\n', ' ')

    fun before(key: SmsPageKey) = arrayOf(key.date.toString(), key.date.toString(), key.id.toString())
    fun key(message: Message) = SmsPageKey(message.timestamp, message.id.toLong())
    fun merge(older: List<Message>, current: List<Message>): List<Message> =
        (older + current).associateBy { it.id }.values.sortedWith(compareBy<Message> { it.timestamp }.thenBy { it.id.toLong() })
}
