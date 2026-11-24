package com.gediadeepak.smsapp.core

object DemoData {
    fun conversations(now: Long): List<Conversation> {
        fun msg(id: String, text: String, out: Boolean, minutesAgo: Int, line: SimLine = SimLine.PERSONAL) =
            Message(id, text, out, now - minutesAgo * 60_000L, line)
        return listOf(
            Conversation("maya", "Maya Patel", "+44 7700 900142", SimLine.PERSONAL, listOf(
                msg("maya-1", "Hey! How has your morning been? ☀️", false, 45),
                msg("maya-2", "Pretty good! Finally got through my to-do list.", true, 42),
                msg("maya-3", "I think we’ve earned a coffee break.", false, 38),
                msg("maya-4", "Absolutely. The little place on the corner?", true, 35),
                msg("maya-5", "Yes! Coffee at 3? I’ll grab us a table by the window ☕", false, 30),
            ), starred = true, topic = "coffee"),
            Conversation("alex", "Alex Morgan", "+44 7700 900215", SimLine.WORK, listOf(
                msg("alex-1", "I’ve added the latest mockups to the shared folder.", false, 85, SimLine.WORK),
                msg("alex-2", "Thanks, Alex. I’ll take a look.", true, 80, SimLine.WORK),
                msg("alex-3", "Can we go through the designs tomorrow at 10?", false, 55, SimLine.WORK),
            ), unread = 1, topic = "project"),
            Conversation("mum", "Mum", "+44 7700 900331", SimLine.PERSONAL, listOf(
                msg("mum-1", "Sunday lunch at ours? I’m making your favourite 😊", false, 90),
            ), unread = 1, starred = true, topic = "family"),
            Conversation("studio", "Studio North", "+44 7700 900407", SimLine.WORK, listOf(
                msg("studio-1", "The first round of concepts will be ready on Friday.", false, 130, SimLine.WORK),
            ), unread = 1, topic = "project"),
            Conversation("jamie", "Jamie Wilson", "+44 7700 900512", SimLine.PERSONAL, listOf(
                msg("jamie-1", "Made it home! Thanks for a lovely evening.", false, 960),
                msg("jamie-2", "Such a good catch-up. Let’s do it again soon!", true, 950),
            )),
            Conversation("sam", "Sam Taylor", "+44 7700 900723", SimLine.WORK, listOf(
                msg("sam-1", "The invoice is all sorted. Thanks for your help!", false, 1500, SimLine.WORK),
            )),
            Conversation("old", "Old appointment", "+44 7700 900841", SimLine.PERSONAL, listOf(
                msg("old-1", "Your appointment is confirmed. See you Tuesday.", false, 4000),
            ), archived = true),
        )
    }
}
