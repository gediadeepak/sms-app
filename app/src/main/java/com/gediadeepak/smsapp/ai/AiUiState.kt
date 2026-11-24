package com.gediadeepak.smsapp.ai

data class AiUiState(
    val conversationId: String? = null,
    val fingerprint: String? = null,
    val loading: Boolean = false,
    val summary: String? = null,
    val replies: List<String> = emptyList(),
    val error: String? = null,
    val draftBeforeInsertion: String? = null,
    val insertedDraft: String? = null,
    val insertedReply: String? = null,
)

data class AiAccessState(val checking: Boolean = false, val message: String? = null)
