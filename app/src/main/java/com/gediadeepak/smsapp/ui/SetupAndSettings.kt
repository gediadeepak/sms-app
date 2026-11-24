@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gediadeepak.smsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gediadeepak.smsapp.InboxState
import com.gediadeepak.smsapp.core.*
import com.gediadeepak.smsapp.telephony.SmsNotificationStatus

@Composable
internal fun SmsSetup(state: InboxState, onSetup: () -> Unit, onAppSettings: () -> Unit, onPreferences: (Preferences) -> Unit, onAccount: () -> Unit, onBackup: () -> Unit) {
    Surface(color = Canvas, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.height(24.dp)); SandeshLogo(Modifier.size(100.dp))
            Text("sandesh.", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Your messages.\nYour two lines.", fontSize = 30.sp, lineHeight = 40.sp, color = Ink)
            Text("Connect your phone’s SMS inbox to Sandesh. Your existing messages stay in Android’s message store.", fontSize = 16.sp, lineHeight = 26.sp, color = Muted)
            Surface(color = Sage, shape = MaterialTheme.shapes.medium) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Before you switch", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("This build handles SMS only. MMS attachments, group MMS and RCS are not supported. Making Sandesh your default can affect those messages. Keep your previous messaging app installed.", fontSize = 14.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Messages stay on this device unless you choose encrypted backup. AI reply ideas are offline samples; no messages are sent to an AI service.", fontSize = 13.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            } }
            if (!state.hasTelephony) Text("No cellular messaging hardware was detected. Use a phone with SMS support.", color = MaterialTheme.colorScheme.error)
            Button(onClick = onSetup, enabled = state.hasTelephony, modifier = Modifier.fillMaxWidth()) { Text(if (state.defaultSms) "Allow SMS and SIM permissions" else "Choose Sandesh as default SMS app") }
            TextButton(onClick = onAppSettings, modifier = Modifier.fillMaxWidth()) { Text("Open Android app settings") }
            OutlinedButton(onClick = onAccount, modifier = Modifier.fillMaxWidth()) { Text("Account") }
            OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("Backup & restore") }
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
internal fun SettingsScreen(state: InboxState, onSave: (Preferences) -> Unit, onNotifications: () -> Unit, onTestNotifications: () -> Unit, onAppSettings: () -> Unit, onAccount: () -> Unit, onBackup: () -> Unit, onBlockedSenders: () -> Unit, onHeadset: () -> Unit, onDismiss: () -> Unit) {
    val preferences = state.preferences
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var personal by rememberSaveable(preferences.personalLabel) { mutableStateOf(preferences.personalLabel) }
    var work by rememberSaveable(preferences.workLabel) { mutableStateOf(preferences.workLabel) }
    fun toggle(section: String) { expanded = if (expanded == section) null else section }
    SettingsPage("Settings", onDismiss) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Make Sandesh yours.", color = Muted, fontSize = 14.sp)
            SettingsSection("Account & backup", "Sign-in, encrypted backups and restore", expanded == "account", { toggle("account") }) {
                OutlinedButton(onClick = onAccount, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.AccountCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Account") }
                OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Backup, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Backup & restore") }
            }
            SettingsSection("Appearance", preferences.theme.label, expanded == "appearance", { toggle("appearance") }) {
                ThemeOptions(preferences.theme) { onSave(preferences.copy(theme = it)) }
            }
            SettingsSection("SIM cards", "Labels and default sending SIM", expanded == "sim", { toggle("sim") }) {
                OutlinedTextField(value = personal, onValueChange = { personal = it.take(18) }, label = { Text("SIM 1 label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = work, onValueChange = { work = it.take(18) }, label = { Text("SIM 2 label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onSave(preferences.copy(personalLabel = personal.trim(), workLabel = work.trim())) }, enabled = personal.isNotBlank() && work.isNotBlank() && (personal.trim() != preferences.personalLabel || work.trim() != preferences.workLabel)) { Text("Save SIM labels") }
                Text("Default sending SIM", style = MaterialTheme.typography.titleSmall)
                LinePicker(preferences.defaultSubscriptionId, state.subscriptions, preferences, { onSave(preferences.copy(defaultSubscriptionId = it)) }, showAsk = true)
                Text("With multiple SIMs, Ask every time leaves sending disabled until you choose a line in the composer. If a saved SIM disappears, choose again.", fontSize = 12.sp, lineHeight = 21.sp, color = Muted)
            }
            SettingsSection("Reply ideas", "Offline suggestions", expanded == "replies", { toggle("replies") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Show reply ideas"); Text("Offline samples · no AI connection", fontSize = 12.sp, color = Muted) }
                    Switch(checked = preferences.suggestions, onCheckedChange = { onSave(preferences.copy(suggestions = it)) })
                }
            }
            SettingsSection("Notifications", if (state.notificationStatus == SmsNotificationStatus.ENABLED) "Message alerts are on" else "Message alerts need attention", expanded == "notifications", { toggle("notifications") }) {
                Text(when (state.notificationStatus) {
                    SmsNotificationStatus.ENABLED -> "Sound, pop-ups and lock-screen visibility follow your Android settings."
                    SmsNotificationStatus.PERMISSION_REQUIRED -> "Allow notification permission to see new SMS alerts."
                    SmsNotificationStatus.APP_BLOCKED -> "Android has blocked notifications for Sandesh."
                    SmsNotificationStatus.CHANNEL_BLOCKED -> "The Messages notification category is disabled in Android."
                }, fontSize = 13.sp, color = Muted, lineHeight = 21.sp)
                OutlinedButton(onClick = onNotifications, modifier = Modifier.fillMaxWidth()) { Text(if (state.notificationStatus == SmsNotificationStatus.ENABLED) "Notification settings" else "Enable message notifications") }
                OutlinedButton(onClick = onTestNotifications, enabled = state.notificationStatus == SmsNotificationStatus.ENABLED, modifier = Modifier.fillMaxWidth()) { Text("Send test notification") }
                TextButton(onClick = onAppSettings) { Text("Android permissions and settings") }
            }
            SettingsSection("Spam & blocked senders", "${state.blockedSenders.size} blocked senders", expanded == "spam", { toggle("spam") }) {
                Text("Blocked SMS stays in Spam without alerts. Manage individual senders here.", fontSize = 13.sp, color = Muted)
                OutlinedButton(onClick = onBlockedSenders, modifier = Modifier.fillMaxWidth()) { Text("Blocked senders") }
            }
            SettingsSection("Headset readout", "Sender announcements and manual reading", expanded == "headset", { toggle("headset") }) {
                OutlinedButton(onClick = onHeadset, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Headphones, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Headset readout") }
            }
            SettingsSection("About Sandesh", "SMS support and storage", expanded == "about", { toggle("about") }) {
                Text("SMS only · MMS/RCS not supported. App flags and send receipts use a private SQLite database. SMS stays in Android’s system store; encrypted backups are available under Backup & restore.", fontSize = 12.sp, lineHeight = 21.sp, color = Muted)
                if (state.pendingMms > 0) Text("${state.pendingMms} MMS notification(s) received. Attachments cannot be downloaded or displayed. Switch back to a full messaging app for future MMS.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, summary: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }.clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse $title" else "Expand $title", onClick = onToggle).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(summary, fontSize = 12.sp, color = Muted) }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (expanded) Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun ThemeOptions(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column { ThemeMode.entries.forEach { mode ->
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selected == mode, onClick = { onSelect(mode) }); TextButton(onClick = { onSelect(mode) }) {
                Box(Modifier.size(20.dp).background(sandeshColors(mode).primary, CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                Spacer(Modifier.width(10.dp)); Text(mode.label)
            } }
    } }
}
