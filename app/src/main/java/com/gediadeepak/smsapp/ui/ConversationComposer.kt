package com.gediadeepak.smsapp.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gediadeepak.smsapp.InboxState
import com.gediadeepak.smsapp.SandeshViewModel
import com.gediadeepak.smsapp.core.*

@Composable
internal fun ConversationComposer(state: InboxState, model: SandeshViewModel, onAssistant: (AiTask) -> Unit) {
    val conversation = state.selected ?: return
    val context = LocalContext.current
    val answer by model.ai.collectAsStateWithLifecycle()
    var field by remember(conversation.id) { mutableStateOf(TextFieldValue(state.draft, TextRange(state.draft.length))) }
    var pasteError by remember(conversation.id) { mutableStateOf<String?>(null) }
    var tone by rememberSaveable { mutableStateOf(Tone.FRIENDLY) }
    var tones by remember { mutableStateOf(false) }
    LaunchedEffect(state.draft, conversation.id) {
        if (field.text != state.draft) field = TextFieldValue(state.draft, TextRange(state.draft.length))
    }
    Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp)) {
        if (state.preferences.aiEnabled && (state.preferences.aiReplies || state.preferences.aiSummaries)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (state.preferences.aiSummaries) IconButton(onClick = { onAssistant(AiTask.SUMMARY) }) { Icon(Icons.Outlined.Summarize, "Summarize conversation", Modifier.size(20.dp), tint = Forest) }
                if (state.preferences.aiReplies) Box {
                    TextButton(onClick = { tones = true }, enabled = !answer.loading) { Text(tone.label, fontSize = 12.sp); Icon(Icons.Outlined.ExpandMore, "Choose AI reply tone", Modifier.size(16.dp)) }
                    DropdownMenu(expanded = tones, onDismissRequest = { tones = false }) { Tone.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { tone = option; tones = false; model.cancelAi() })
                    } }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onAssistant(if (state.preferences.aiReplies) AiTask.REPLIES else AiTask.SUMMARY) }) { Text("Preview", fontSize = 12.sp) }
                if (state.preferences.aiReplies) {
                    if (answer.loading) TextButton(onClick = model::cancelAi) { Text("Cancel") }
                    else FilledTonalButton(onClick = {
                        model.generateAi(AiTask.REPLIES, tone, AiContextPolicy.prepare(conversation).fingerprint, insertIntoDraft = true)
                    }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text("AI reply", fontSize = 12.sp) }
                }
            }
            Text("AI shares recent eligible chat text with Google when requested. Preview first; charges may apply.", fontSize = 10.sp, color = Muted, lineHeight = 15.sp)
            if (answer.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 5.dp))
            answer.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            if (answer.replies.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    answer.replies.forEach { reply -> FilterChip(selected = answer.insertedReply == reply,
                        onClick = { model.insertAiReply(reply) }, label = { Text(reply, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) }, modifier = Modifier.widthIn(max = 220.dp)) }
                }
                Text(if (answer.insertedDraft == state.draft) "AI draft added · review and edit before sending" else "Choose a suggestion to add to your draft", fontSize = 10.sp, color = Muted)
            }
        }
        Surface(shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(10.dp)) {
                OutlinedTextField(value = field, onValueChange = {
                    if (it.text.length <= ComposerRules.LIMIT) { field = it; model.draft(it.text); pasteError = null }
                    else pasteError = "The draft limit is 1,600 characters. Shorten the text before adding more."
                }, placeholder = { Text("Write a message…", fontSize = 15.sp) }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent), textStyle = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { LinePicker(state.sendSubscriptionId, state.subscriptions, state.preferences, model::sendSubscription) }
                    IconButton(onClick = {
                        val text = runCatching { clipboardText(context) }.getOrNull()
                        if (text == null) pasteError = "No clipboard text is available."
                        else {
                            val start = minOf(field.selection.start, field.selection.end)
                            val end = maxOf(field.selection.start, field.selection.end)
                            val updated = ComposerRules.insert(field.text, text, start, end)
                            if (updated == null) pasteError = "Pasting would exceed 1,600 characters. Nothing was replaced."
                            else { field = TextFieldValue(updated, TextRange(start + text.length)); model.draft(updated); pasteError = null }
                        }
                    }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.ContentPaste, "Paste text at cursor", Modifier.size(18.dp), tint = Muted) }
                    IconButton(onClick = model::polishDraft, enabled = state.draft.isNotBlank(), modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Spellcheck, "Tidy draft with local rules", Modifier.size(18.dp), tint = Muted) }
                    Button(onClick = model::send, enabled = state.draft.isNotBlank() && state.canSend, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
                        Text("Send", fontSize = 14.sp); Spacer(Modifier.width(5.dp)); Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(16.dp))
                    }
                }
            }
        }
        pasteError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Text("SMS · ${state.draft.length} / ${ComposerRules.LIMIT}", fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
        if (state.sendSubscriptionId == null) Text(if (state.subscriptions.isEmpty()) "Insert or enable a SIM to send SMS." else "Choose a sending SIM above. Set a default in Settings.", fontSize = 12.sp, color = Muted)
    }
}
