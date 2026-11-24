@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.gediadeepak.smsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gediadeepak.smsapp.InboxState
import com.gediadeepak.smsapp.SandeshViewModel
import com.gediadeepak.smsapp.core.*

@Composable
internal fun AiScreen(state: InboxState, model: SandeshViewModel, initialTask: AiTask, onBack: () -> Unit) {
    val result by model.ai.collectAsStateWithLifecycle()
    val conversation = state.selected
    val input = remember(conversation) { conversation?.let(AiContextPolicy::prepare) }
    var task by rememberSaveable { mutableStateOf(if (initialTask == AiTask.REPLIES && state.preferences.aiReplies) AiTask.REPLIES else if (state.preferences.aiSummaries) AiTask.SUMMARY else AiTask.REPLIES) }
    var tone by rememberSaveable { mutableStateOf(Tone.FRIENDLY) }
    var preview by rememberSaveable { mutableStateOf(false) }
    val available = state.ready && state.preferences.aiEnabled && conversation != null && !conversation.spam && !conversation.blocked
    val close = { model.cancelAi(); onBack() }
    SettingsPage("AI assistance", close) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("A suggestion, never a send.", style = MaterialTheme.typography.titleLarge)
            if (!available) Text("AI is turned off or this conversation is unavailable. Enable AI under Settings to use it.", color = Muted)
            else {
                Text("Google Gemini processes only the text you choose to share from this conversation. Check the preview below before generating. Charges may apply.", color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.preferences.aiReplies) FilterChip(selected = task == AiTask.REPLIES, onClick = { model.cancelAi(); task = AiTask.REPLIES }, label = { Text("Reply suggestions") }, enabled = !result.loading)
                    if (state.preferences.aiSummaries) FilterChip(selected = task == AiTask.SUMMARY, onClick = { model.cancelAi(); task = AiTask.SUMMARY }, label = { Text("Summary") }, enabled = !result.loading)
                }
                if (task == AiTask.REPLIES) {
                    Text("Reply tone", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Tone.entries.forEach { value ->
                        FilterChip(selected = tone == value, onClick = { model.cancelAi(); tone = value }, label = { Text(value.label) }, enabled = !result.loading)
                    } }
                }
                Text("${input?.messages?.size ?: 0} messages to share · ${input?.excluded ?: 0} filtered out", style = MaterialTheme.typography.titleSmall)
                Text("Likely OTP, verification and financial messages are filtered using local rules. These rules cannot detect every sensitive detail. Names, phone numbers, SIM details and message dates are not added to the request; message bodies may still contain personal information.", fontSize = 12.sp, color = Muted, lineHeight = 20.sp)
                if (input?.limited == true) Text("Uses up to 20 recent eligible messages, 1,000 characters each, and 6,000 characters total. Some older or longer text is omitted.", fontSize = 12.sp, color = Muted)
                OutlinedButton(onClick = { preview = !preview }, modifier = Modifier.fillMaxWidth()) { Text(if (preview) "Hide text preview" else "Preview text shared with Google") }
                if (preview) input?.messages?.forEach { message ->
                    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (message.outgoing) "You" else "Other person", style = MaterialTheme.typography.labelMedium, color = Forest)
                        Text(message.text, fontSize = 14.sp)
                    } }
                }
                if (input?.messages.isNullOrEmpty()) Text("No eligible text remains. Nothing will be shared with Google.", color = Muted)
                if (result.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Generating…", color = Muted)
                    TextButton(onClick = model::cancelAi) { Text("Cancel waiting") }
                } else {
                    Button(onClick = { input?.let { model.generateAi(task, tone, it.fingerprint) } }, enabled = !input?.messages.isNullOrEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(if (task == AiTask.SUMMARY) "Share text & summarize" else "Share text & suggest replies")
                    }
                }
                result.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                result.summary?.let { summary ->
                    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Conversation summary", style = MaterialTheme.typography.titleMedium)
                        Text(summary)
                    } }
                }
                result.replies.forEach { reply ->
                    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(reply)
                        OutlinedButton(onClick = { if (model.insertAiReply(reply)) close() }) { Text(if (state.draft.isBlank()) "Use as draft" else "Add to existing draft") }
                    } }
                }
                Text("AI can make mistakes. Review dates, facts and wording before using a suggestion. You still choose the SIM and tap Send yourself.", fontSize = 12.sp, color = Muted, lineHeight = 20.sp)
                Text("Unused AI results stay in memory and are excluded from backups. Sent drafts become normal SMS. Turning AI off stops future requests; it cannot undo text already shared. A submitted request may still complete after you cancel waiting.", fontSize = 12.sp, color = Muted, lineHeight = 20.sp)
            }
        }
    }
}
