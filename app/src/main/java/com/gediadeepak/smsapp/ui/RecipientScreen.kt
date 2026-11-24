package com.gediadeepak.smsapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gediadeepak.smsapp.InboxState
import com.gediadeepak.smsapp.core.*

@Composable
internal fun RecipientScreen(state: InboxState, forwarded: Message?, forwarding: Boolean, onContacts: () -> Unit,
                             onChoose: (String, String, Int?) -> String?, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var alias by rememberSaveable { mutableStateOf("") }
    var showAlias by rememberSaveable { mutableStateOf(false) }
    var subscription by rememberSaveable { mutableStateOf(resolveSendingSubscription(state.preferences.defaultSubscriptionId, state.subscriptions.map { it.subscriptionId })) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val contacts = remember(state.contacts, query) { searchContacts(state.contacts, query) }
    val recent = remember(state.conversations, query) { state.conversations.filter { !it.spam && !it.blocked && validNumber(it.number) &&
        (query.isBlank() || it.displayName.contains(query, true) || it.number.contains(query, true)) }.take(20) }
    SettingsPage(if (forwarding) "Forward message" else "New message", onBack) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (forwarding) Text(if (forwarded != null) "Choose a recipient. The message will be added to a draft for review." else "The original message is no longer available.", color = Muted, fontSize = 13.sp)
            OutlinedTextField(query, { query = it }, label = { Text("Search contacts or conversations") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!state.contactsGranted) OutlinedButton(onClick = onContacts, modifier = Modifier.fillMaxWidth()) { Text("Allow contacts for names and search") }
            OutlinedTextField(number, { number = it.take(22); selectedName = null; error = null }, label = { Text(selectedName ?: "Recipient phone number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!forwarding && selectedName == null) {
                if (showAlias) OutlinedTextField(alias, { alias = it.take(60) }, label = { Text("Conversation name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                else TextButton(onClick = { showAlias = true }) { Text("Add a conversation name") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LinePicker(subscription, state.subscriptions, state.preferences, { subscription = it }, showAsk = true)
                Button(onClick = { error = onChoose(if (selectedName == null) alias else "", number, subscription) }, enabled = number.isNotBlank() && (!forwarding || forwarded != null)) { Text("Continue") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (forwarded != null) item {
                    OutlinedCard(Modifier.fillMaxWidth()) { Text(forwarded.text, Modifier.padding(14.dp), maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 13.sp) }
                }
                if (recent.isNotEmpty()) {
                    item { Text("Recent conversations", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                    items(recent, key = { "thread:${it.id}" }) { c ->
                        RecipientRow(c.displayName, if (c.contactName == null) c.number else "Conversation", { number = c.number; selectedName = c.displayName; error = null })
                    }
                }
                if (state.contactsGranted) {
                    item { Text("Contacts", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp)) }
                    if (contacts.isEmpty()) item { Text("No matching contacts with an SMS number.", color = Muted, fontSize = 13.sp) }
                    items(contacts, key = { "contact:${it.id}" }) { contact ->
                        RecipientRow(contact.name, "${contact.label} · ${contact.number}", { number = contact.number; selectedName = contact.name; error = null })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipientRow(title: String, detail: String, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = Muted, fontSize = 12.sp)
        }
    }
}
