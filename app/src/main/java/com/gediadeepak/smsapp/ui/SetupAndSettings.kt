@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gediadeepak.smsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gediadeepak.smsapp.InboxState
import com.gediadeepak.smsapp.core.*

@Composable
internal fun SmsSetup(state: InboxState, onSetup: () -> Unit, onAppSettings: () -> Unit, onPreferences: (Preferences) -> Unit) {
    Surface(color = Canvas, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.height(24.dp)); SandeshLogo(Modifier.size(100.dp))
            Text("sandesh.", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Your messages.\nYour two lines.", fontSize = 30.sp, lineHeight = 40.sp, color = Ink)
            Text("Connect your phone’s SMS inbox to Sandesh. Your existing messages stay in Android’s message store.", fontSize = 16.sp, lineHeight = 26.sp, color = Muted)
            Surface(color = Sage, shape = MaterialTheme.shapes.medium) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Before you switch", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("This build handles SMS only. MMS attachments, group MMS and RCS are not supported. Making Sandesh your default can affect those messages. Keep your previous messaging app installed.", fontSize = 14.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Messages stay on this device. AI reply ideas are optional offline samples; no messages are sent to an AI service.", fontSize = 13.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            } }
            if (!state.hasTelephony) Text("No cellular messaging hardware was detected. Use a phone with SMS support.", color = MaterialTheme.colorScheme.error)
            Button(onClick = onSetup, enabled = state.hasTelephony, modifier = Modifier.fillMaxWidth()) { Text(if (state.defaultSms) "Allow SMS and SIM permissions" else "Choose Sandesh as default SMS app") }
            TextButton(onClick = onAppSettings, modifier = Modifier.fillMaxWidth()) { Text("Open Android app settings") }
            Text("Appearance", fontWeight = FontWeight.Medium)
            ThemeOptions(state.preferences.theme) { onPreferences(state.preferences.copy(theme = it)) }
            Text("You can change your default messaging app in Android Settings at any time.", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
internal fun LinePicker(selected: Int?, subscriptions: List<DeviceSim>, preferences: Preferences, onSelect: (Int?) -> Unit, showAsk: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val sim = subscriptions.find { it.subscriptionId == selected }
    val label = sim?.let { "${preferences.label(it.line)} · ${it.label}" }
        ?: if (selected != null) "Saved SIM unavailable" else if (showAsk) "Ask every time" else "Choose SIM"
    Box {
        FilledTonalButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp), modifier = Modifier.widthIn(max = 220.dp)) {
            Icon(Icons.Outlined.SimCard, null, Modifier.size(15.dp)); Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Outlined.ExpandMore, "Choose sending SIM", Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (showAsk) DropdownMenuItem(text = { Text("Ask every time") }, onClick = { onSelect(null); expanded = false })
            subscriptions.forEach { item -> DropdownMenuItem(text = { Column { Text("${preferences.label(item.line)} · SIM ${item.slotIndex + 1}"); Text(item.displayName, fontSize = 12.sp, color = Muted) } }, onClick = { onSelect(item.subscriptionId); expanded = false }) }
            if (subscriptions.isEmpty()) DropdownMenuItem(text = { Text("No active SIMs detected") }, enabled = false, onClick = {})
        }
    }
}

@Composable
internal fun NewConversationDialog(state: InboxState, onCreate: (String, String, Int?) -> String?, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }; var number by rememberSaveable { mutableStateOf("") }
    var subscription by rememberSaveable { mutableStateOf(resolveSendingSubscription(state.preferences.defaultSubscriptionId, state.subscriptions.map { it.subscriptionId })) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("A simple hello.", fontWeight = FontWeight.SemiBold) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("START A CONVERSATION", fontSize = 11.sp, color = Muted)
            OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Name (optional)") }, singleLine = true)
            OutlinedTextField(value = number, onValueChange = { number = it.take(22); error = null }, label = { Text("Phone number") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), isError = error != null)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            Text("Send from", fontSize = 14.sp, color = Muted)
            LinePicker(subscription, state.subscriptions, state.preferences, { subscription = it })
            Text("Choose a SIM here or in the composer before sending.", fontSize = 12.sp, color = Muted)
        }
    }, confirmButton = { Button(onClick = { error = onCreate(name, number, subscription); if (error == null) onDismiss() }) { Text("Start conversation") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun SettingsDialog(state: InboxState, onSave: (Preferences) -> Unit, onNotifications: () -> Unit, onAppSettings: () -> Unit, onDismiss: () -> Unit) {
    val preferences = state.preferences
    var personal by rememberSaveable { mutableStateOf(preferences.personalLabel) }; var work by rememberSaveable { mutableStateOf(preferences.workLabel) }
    var defaultId by rememberSaveable { mutableStateOf(preferences.defaultSubscriptionId) }; var suggestions by rememberSaveable { mutableStateOf(preferences.suggestions) }
    var theme by rememberSaveable { mutableStateOf(preferences.theme) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Your workspace.", fontWeight = FontWeight.SemiBold) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("MAKE YOURSELF AT HOME", fontSize = 11.sp, color = Muted)
            Text("Appearance", fontWeight = FontWeight.Medium); ThemeOptions(theme) { theme = it }
            OutlinedTextField(value = personal, onValueChange = { personal = it.take(18) }, label = { Text("SIM 1 label") }, singleLine = true)
            OutlinedTextField(value = work, onValueChange = { work = it.take(18) }, label = { Text("SIM 2 label") }, singleLine = true)
            Text("Default sending SIM", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            LinePicker(defaultId, state.subscriptions, preferences.copy(personalLabel = personal, workLabel = work), { defaultId = it }, showAsk = true)
            Text("With multiple SIMs, Ask every time leaves sending disabled until you choose a line in the composer. If a saved SIM disappears, choose again.", fontSize = 12.sp, lineHeight = 21.sp, color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Reply ideas", fontSize = 14.sp); Text("Offline samples · no AI connection", fontSize = 12.sp, color = Muted) }; Switch(checked = suggestions, onCheckedChange = { suggestions = it }) }
            OutlinedButton(onClick = onNotifications, modifier = Modifier.fillMaxWidth()) { Text("Enable message notifications", fontSize = 12.sp) }
            TextButton(onClick = onAppSettings) { Text("Android permissions and settings", fontSize = 12.sp) }
            HorizontalDivider()
            Text("SMS only · MMS/RCS not supported. No export or cloud backup yet. App flags and send receipts use a private SQLite database; SMS messages stay in Android’s system store.", fontSize = 12.sp, lineHeight = 21.sp, color = Muted)
            if (state.pendingMms > 0) Text("${state.pendingMms} MMS notification(s) received. Raw notification data is retained, but attachments cannot be downloaded or displayed in this build. Switch back to a full messaging app for future MMS.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }, confirmButton = { Button(onClick = { onSave(preferences.copy(personalLabel = personal.trim(), workLabel = work.trim(), defaultSubscriptionId = defaultId, suggestions = suggestions, theme = theme)) }, enabled = personal.isNotBlank() && work.isNotBlank()) { Text("Save preferences") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun ThemeOptions(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column { ThemeMode.entries.forEach { mode ->
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selected == mode, onClick = { onSelect(mode) }); TextButton(onClick = { onSelect(mode) }) { Text(mode.label) } }
    } }
}
