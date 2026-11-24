@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gediadeepak.smsapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gediadeepak.smsapp.InboxState
import com.gediadeepak.smsapp.SandeshViewModel
import com.gediadeepak.smsapp.core.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SandeshApp(model: SandeshViewModel, onSetup: () -> Unit = {}, onAppSettings: () -> Unit = {}, onNotifications: () -> Unit = {}, onAccount: () -> Unit = {}, onBackup: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var newMessage by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var assistant by rememberSaveable { mutableStateOf(false) }
    if (state.error != null) AlertDialog(onDismissRequest = model::clearError, title = { Text("Sandesh needs attention") }, text = { Text(state.error.orEmpty()) }, confirmButton = { TextButton(onClick = model::clearError) { Text("OK") } })
    if (!state.ready) {
        SmsSetup(state, onSetup, onAppSettings, model::savePreferences, onAccount, onBackup)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val desktop = maxWidth >= 1250.dp
        LaunchedEffect(wide) { if (wide && state.selected == null) state.visible.firstOrNull()?.let { model.select(it.id) } }
        val openSettings = { settings = true; scope.launch { drawer.close() }; Unit }
        val startMessage = { newMessage = true; scope.launch { drawer.close() }; Unit }
        BackHandler(enabled = state.selectedId != null && !drawer.isOpen && !newMessage && !settings && !assistant) { model.back() }
        val navigation: @Composable () -> Unit = {
            Sidebar(state, onFolder = { model.folder(it); scope.launch { drawer.close() } },
                onLine = { model.filter(it); scope.launch { drawer.close() } }, onNew = startMessage, onSettings = openSettings)
        }
        val workspace: @Composable () -> Unit = {
            Scaffold(
                modifier = Modifier.fillMaxSize().imePadding(), containerColor = Canvas,
                topBar = {
                    if (!wide && state.selected != null) {
                        ConversationHeader(state.selected!!, model::back, model::star, model::archive,
                            if (state.preferences.suggestions) ({ assistant = true }) else null)
                    } else {
                        TopAppBar(title = {
                            Column {
                                if (wide) Text("A LITTLE LESS NOISE. A LOT MORE CONNECTION.", style = MaterialTheme.typography.labelSmall, color = Muted, letterSpacing = 1.sp)
                                Text("Messages.", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
                            }
                        }, navigationIcon = {
                            if (!desktop) IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, "Open navigation") }
                        }, actions = {
                            IconButton(onClick = model::refresh) { Icon(Icons.Outlined.Refresh, "Refresh inbox", tint = Muted) }
                            IconButton(onClick = openSettings) { Icon(Icons.Outlined.Settings, "Settings", tint = Muted) }
                        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas))
                    }
                },
                bottomBar = {
                    if (wide) Row(Modifier.fillMaxWidth().background(Canvas).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Made for two lines.", fontSize = 11.sp, color = Muted)
                        Text(if (state.loading) "Updating inbox…" else "SMS · on your device", fontSize = 11.sp, color = Muted)
                    }
                },
            ) { padding ->
                Row(Modifier.padding(padding).padding(horizontal = if (wide) 20.dp else 12.dp).padding(top = 8.dp).fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))) {
                    if (wide || state.selectedId == null) {
                        Inbox(state, Modifier.then(if (wide) Modifier.width(330.dp) else Modifier.weight(1f)), model::select,
                            model::search, model::filter, startMessage, compact = !wide)
                    }
                    if (wide) VerticalDivider()
                    if (wide || state.selectedId != null) {
                        Thread(state, Modifier.weight(1f), model, showHeader = wide, onAssistant = { assistant = true })
                    }
                    if (desktop && state.preferences.suggestions) {
                        VerticalDivider()
                        Assistant(state, model, Modifier.width(300.dp))
                    }
                }
            }
        }
        if (desktop) {
            Row(Modifier.fillMaxSize().background(Canvas)) { navigation(); Box(Modifier.weight(1f)) { workspace() } }
        } else {
            ModalNavigationDrawer(drawerState = drawer, drawerContent = { ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) { navigation() } }) { workspace() }
        }
    }
    if (newMessage) NewConversationDialog(state, { name, number, id -> model.newConversation(name, number, id) }) { newMessage = false }
    if (settings) SettingsDialog(state, onSave = { model.savePreferences(it); settings = false }, onNotifications = onNotifications, onAppSettings = onAppSettings,
        onAccount = { settings = false; onAccount() }, onBackup = { settings = false; onBackup() }, onDismiss = { settings = false })
    if (assistant && state.selected != null && state.preferences.suggestions) Dialog(onDismissRequest = { assistant = false }) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Assistant(state, model, Modifier.fillMaxWidth().heightIn(max = 650.dp), onDismiss = { assistant = false }, onInsert = { assistant = false })
        }
    }
}

@Composable
private fun Sidebar(state: InboxState, onFolder: (Folder) -> Unit, onLine: (SimLine?) -> Unit, onNew: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.width(235.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow).statusBarsPadding().navigationBarsPadding()
        .padding(20.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            SandeshLogo(Modifier.size(42.dp))
            Spacer(Modifier.width(10.dp)); Text("sandesh.", fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        }
        Text("YOUR EVERYDAY, CONNECTED", fontSize = 9.sp, letterSpacing = 1.sp, color = Muted, modifier = Modifier.padding(top = 16.dp, bottom = 25.dp))
        Button(onClick = onNew, shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("New message") }
        Spacer(Modifier.height(20.dp))
        Folder.entries.forEach { folder ->
            val selected = state.folder == folder
            val vector = when (folder) { Folder.INBOX -> Icons.Outlined.Inbox; Folder.UNREAD -> Icons.Outlined.MarkEmailUnread; Folder.STARRED -> Icons.Outlined.StarOutline; Folder.ARCHIVED -> Icons.Outlined.Archive }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(if (selected) Sage else Color.Transparent, RoundedCornerShape(8.dp))
                .clickable { onFolder(folder) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(vector, null, tint = if (selected) Forest else Muted, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                Text(folder.label, fontSize = 14.sp, color = if (selected) Forest else Muted, modifier = Modifier.weight(1f))
                if (folder == Folder.UNREAD && state.unreadCount > 0) Badge(containerColor = Forest) { Text(state.unreadCount.toString()) }
            }
        }
        Text("YOUR LINES", fontSize = 11.sp, color = Muted, letterSpacing = 1.sp, modifier = Modifier.padding(top = 30.dp, bottom = 14.dp))
        state.subscriptions.forEach { sim ->
            val line = sim.line
            Surface(onClick = { onLine(line) }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (state.filterLine == line) Forest else MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.padding(bottom = 10.dp).fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SimCard, null, tint = lineColor(line), modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                    Column { Text(state.preferences.label(line), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(sim.label, fontSize = 11.sp, color = Muted, maxLines = 2) }
                }
            }
        }
        if (state.subscriptions.isEmpty()) Text("No active SIM detected", fontSize = 13.sp, color = Muted)
        TextButton(onClick = { onLine(null) }) { Text("Show both lines", fontSize = 12.sp, color = Muted) }
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Lock, null, Modifier.size(15.dp), tint = Muted); Spacer(Modifier.width(8.dp)); Text("Your conversations stay\non this device.", color = Muted, fontSize = 11.sp) }
        TextButton(onClick = onSettings, modifier = Modifier.padding(top = 16.dp)) { Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text("Settings") }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Sms, null, tint = Forest); Spacer(Modifier.width(10.dp)); Column { Text("Sandesh", fontSize = 14.sp, fontWeight = FontWeight.Medium); Text("On-device SMS", fontSize = 11.sp, color = Muted) } }
    }
}

@Composable
private fun Inbox(state: InboxState, modifier: Modifier, onSelect: (String) -> Unit, onSearch: (String) -> Unit, onFilter: (SimLine?) -> Unit, onNew: () -> Unit, compact: Boolean = true) {
    Column(modifier.fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (state.folder == Folder.INBOX) "All messages" else "${state.folder.label} messages", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                if (!compact) Text("Two lines. One happy inbox.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 5.dp))
            }
            IconButton(onClick = onNew) { Icon(Icons.Outlined.Edit, "New conversation", tint = Muted, modifier = Modifier.size(20.dp)) }
        }
        OutlinedTextField(value = state.query, onValueChange = onSearch, placeholder = { Text("Search conversations", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(9.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LineFilter("All lines", state.filterLine == null) { onFilter(null) }
            state.subscriptions.map { it.line }.distinct().forEach { line -> LineFilter(state.preferences.label(line), state.filterLine == line, line) { onFilter(line) } }
        }
        Row(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("CONVERSATIONS", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f)); Text(state.visible.size.toString().padStart(2, '0'), color = Muted, fontSize = 11.sp) }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.pendingMms > 0) Text("MMS received: attachments are unavailable. See Settings.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        if (state.visible.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(if (state.query.isNotBlank()) "No matching conversations." else "A little space for new conversations.", Modifier.padding(24.dp), color = Muted, fontSize = 14.sp) }
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 9.dp)) {
            items(state.visible, key = { it.id }) { c ->
                val last = c.messages.lastOrNull()
                Row(Modifier.fillMaxWidth().background(if (c.id == state.selectedId) Sage else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(c.id) }.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                    Avatar(c.initials, c.name); Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, fontSize = 15.sp, fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(8.dp))
                            CompactSimIcon(last?.line ?: c.line, state.preferences)
                            Spacer(Modifier.width(5.dp))
                            Text(last?.let { listTime(it.timestamp) } ?: "New", fontSize = 10.sp, color = Muted)
                        }
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text((if (last?.outgoing == true) "You: " else "") + (last?.text ?: "Say hello. Start a conversation."), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
                            if (c.unread > 0) { Spacer(Modifier.width(8.dp)); Badge(containerColor = Forest) { Text(c.unread.toString()) } }
                            else if (c.starred) { Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.Star, "Starred", Modifier.size(13.dp), tint = Work) }
                        }
                    }
                }
            }
        }
        if (!compact) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("A little space for every conversation.", Modifier.fillMaxWidth().padding(15.dp), fontSize = 11.sp, color = Muted)
        }
    }
}

@Composable
private fun LineFilter(label: String, selected: Boolean, line: SimLine? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) Sage else Color.Transparent, shape = RoundedCornerShape(6.dp)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (line != null) { Box(Modifier.size(5.dp).background(lineColor(line), CircleShape)); Spacer(Modifier.width(5.dp)) }
            Text(label.take(14), fontSize = 12.sp, color = if (selected) Forest else Muted, maxLines = 1)
        }
    }
}

@Composable
private fun ConversationHeader(c: Conversation, onBack: (() -> Unit)?, onStar: () -> Unit, onArchive: () -> Unit, onAssistant: (() -> Unit)?) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().then(if (onBack != null) Modifier.statusBarsPadding() else Modifier).padding(horizontal = 6.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to inbox", tint = Muted) }
            else Spacer(Modifier.width(14.dp))
            Avatar(c.initials, c.name, small = true); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(c.number, fontSize = 10.sp, color = Muted, maxLines = 1) }
            IconButton(onClick = onStar, modifier = Modifier.size(36.dp)) { Icon(if (c.starred) Icons.Outlined.Star else Icons.Outlined.StarOutline, if (c.starred) "Unstar conversation" else "Star conversation", tint = if (c.starred) Work else Muted, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = onArchive, modifier = Modifier.size(36.dp)) { Icon(if (c.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, if (c.archived) "Restore conversation" else "Archive conversation", tint = Muted, modifier = Modifier.size(20.dp)) }
            if (onAssistant != null) IconButton(onClick = onAssistant, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.AutoAwesome, "Open demo assistant", tint = Muted, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun Thread(state: InboxState, modifier: Modifier, model: SandeshViewModel, showHeader: Boolean, onAssistant: () -> Unit) {
    val c = state.selected
    if (c == null) {
        Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(30.dp)) { Icon(Icons.Outlined.Forum, null, Modifier.size(48.dp), tint = Forest); Text("Make a little connection.", fontSize = 22.sp, modifier = Modifier.padding(top = 20.dp)); Text("Choose a conversation to get started.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)) } }
        return
    }
    val scroll = rememberLazyListState()
    LaunchedEffect(c.id, c.messages.size) { if (c.messages.isNotEmpty()) scroll.scrollToItem(c.messages.lastIndex) }
    Column(modifier.fillMaxHeight()) {
        if (showHeader) ConversationHeader(c, null, model::star, model::archive, if (state.preferences.suggestions) onAssistant else null)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SimCard, null, Modifier.size(14.dp), tint = Muted); Spacer(Modifier.width(7.dp)); Text("Conversation on", color = Muted, fontSize = 11.sp); Spacer(Modifier.width(8.dp)); LineBadge(c.line, state.preferences)
        }
        LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(19.dp)) {
            if (c.messages.isEmpty()) item { Text("A new conversation, a fresh start.\nWrite your first message below.", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(20.dp)) }
            items(c.messages, key = { it.id }) { m ->
                val index = c.messages.indexOfFirst { it.id == m.id }
                val date = day(m.timestamp)
                if (index == 0 || day(c.messages[index - 1].timestamp) != date) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        Text(date, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(horizontal = 15.dp))
                        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                MessageBubble(m, state.preferences)
            }
        }
        Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp)) {
            if (state.preferences.suggestions) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp), tint = Muted); Spacer(Modifier.width(6.dp)); Text("A little help replying", fontSize = 11.sp, color = Muted) }
                TextButton(onClick = onAssistant) { Text("Show suggestions", fontSize = 11.sp) }
            }
            Surface(shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(value = state.draft, onValueChange = model::draft, placeholder = { Text("Write something thoughtful…", fontSize = 15.sp) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent),
                        textStyle = MaterialTheme.typography.bodyLarge)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        LinePicker(state.sendSubscriptionId, state.subscriptions, state.preferences, model::sendSubscription)
                        Spacer(Modifier.weight(1f))
                        if (state.preferences.suggestions) IconButton(onClick = model::polishDraft, enabled = state.draft.isNotBlank(), modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.AutoAwesome, "Tidy draft with local rules", modifier = Modifier.size(18.dp), tint = Muted)
                        }
                        Button(onClick = model::send, enabled = state.draft.isNotBlank() && state.canSend, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
                            Text("Send", fontSize = 14.sp); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(16.dp))
                        }
                    }
                }
            }
            Text("SMS · ${state.draft.length} characters", fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
            if (state.sendSubscriptionId == null) Text(if (state.subscriptions.isEmpty()) "Insert or enable a SIM to send SMS." else "Choose a sending SIM above. Set a default in Settings.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun MessageBubble(message: Message, preferences: Preferences) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start) {
        Surface(shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = if (message.outgoing) 3.dp else 14.dp, bottomStart = if (message.outgoing) 14.dp else 3.dp),
            color = if (message.outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(.9f)) {
            Text(message.text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp), fontSize = 16.sp, lineHeight = 26.sp, color = if (message.outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${clock(message.timestamp)} · ${preferences.label(message.line)}", fontSize = 10.sp, color = Muted)
            if (message.outgoing) { Spacer(Modifier.width(5.dp)); Icon(if (message.delivery == Delivery.DELIVERED) Icons.Outlined.DoneAll else if (message.delivery == Delivery.FAILED || message.delivery == Delivery.UNDELIVERED) Icons.Outlined.ErrorOutline else Icons.Outlined.Done, null, Modifier.size(12.dp), tint = Muted); Spacer(Modifier.width(3.dp)); Text(when (message.delivery) { Delivery.SENDING -> "Sending…"; Delivery.SENT -> "Sent"; Delivery.DELIVERED -> "Delivered"; Delivery.FAILED -> "Send failed · check before retrying"; Delivery.UNDELIVERED -> "Not delivered"; else -> "Status unknown" }, fontSize = 10.sp, color = if (message.delivery == Delivery.FAILED || message.delivery == Delivery.UNDELIVERED) MaterialTheme.colorScheme.error else Muted) }
        }
    }
}

@Composable
private fun Assistant(state: InboxState, model: SandeshViewModel, modifier: Modifier, onDismiss: (() -> Unit)? = null, onInsert: () -> Unit = {}) {
    var tone by rememberSaveable { mutableStateOf(Tone.FRIENDLY) }
    var summary by rememberSaveable { mutableStateOf(false) }
    var ideaOffset by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(22.dp), tint = Forest); Spacer(Modifier.width(10.dp))
            Text("A little assistance", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (onDismiss != null) IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close assistant") }
        }
        Text("Find the words. Keep your voice.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
        Text("DEMO ASSISTANT", color = Forest, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.background(Sage, RoundedCornerShape(4.dp)).padding(6.dp))
        Text("Good conversations\nstart with you.", fontSize = 25.sp, lineHeight = 35.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp), fontWeight = FontWeight.Medium)
        Text("A starting point for your next reply, whenever you need one.", fontSize = 14.sp, lineHeight = 23.sp, color = Muted)
        Row(Modifier.padding(top = 16.dp)) { TextButton(onClick = { summary = false }) { Text("Reply ideas", color = if (!summary) Forest else Muted) }; TextButton(onClick = { summary = true }) { Text("Summary", color = if (summary) Forest else Muted) } }
        HorizontalDivider(Modifier.padding(bottom = 16.dp))
        val c = state.selected
        if (c == null) Text("Select a conversation to explore sample replies.", fontSize = 14.sp, color = Muted)
        else if (summary) {
            Surface(shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Conversation at a glance", fontWeight = FontWeight.Medium)
                    Text("${c.name} · ${c.messages.size} messages", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                    Text("Latest from ${c.name.split(' ').first()}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(c.messages.lastOrNull { !it.outgoing }?.text ?: "No incoming messages yet.", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                    Text("Your latest reply", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(c.messages.lastOrNull { it.outgoing }?.text ?: "You haven’t replied yet.", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    Text("Local recap · no AI service connected", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 16.dp))
                }
            }
        } else {
            Text("Make it sound like you", fontSize = 13.sp, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 10.dp)) { Tone.entries.forEach { value -> FilterChip(selected = value == tone, onClick = { tone = value }, label = { Text(value.label, fontSize = 11.sp) }) } }
            val replies = model.assistant.suggest(c, tone)
            val rotated = replies.indices.map { replies[(it + ideaOffset) % replies.size] }
            rotated.forEachIndexed { index, text ->
                Surface(onClick = { model.draft(text); onInsert() }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column(Modifier.padding(14.dp)) { Text(listOf("KEEP IT NATURAL", "ANOTHER WAY TO SAY IT", "A LITTLE DIFFERENT")[index], fontSize = 9.sp, letterSpacing = 1.sp, color = Muted); Text(text, fontSize = 15.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 8.dp)); Text("Use this reply", color = Forest, fontSize = 12.sp) }
                }
            }
            OutlinedButton(onClick = { ideaOffset = (ideaOffset + 1) % replies.size }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text("Refresh ideas", fontSize = 13.sp)
            }
        }
        Text("A suggestion, never a send.", fontSize = 12.sp, color = Forest, modifier = Modifier.padding(top = 18.dp))
        Text("Tap an idea to add it to your draft. Make it yours before sending.", fontSize = 12.sp, lineHeight = 21.sp, color = Muted, modifier = Modifier.padding(top = 5.dp, bottom = 20.dp))
        HorizontalDivider(); Text("Sample suggestions · no AI service connected", fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun Avatar(initials: String, name: String, small: Boolean = false) {
    val palettes = listOf(Color(0xFFE8D6C9) to Color(0xFF96705A), Color(0xFFD9E2EE) to Color(0xFF6B82A1), Color(0xFFE9DCE8) to Color(0xFFA17F9D), Color(0xFFD5E5DC) to Color(0xFF779B84))
    val index = when (name) { "Maya Patel" -> 0; "Alex Morgan" -> 1; "Mum" -> 2; "Studio North" -> 3; else -> (name.hashCode().toLong().let { if (it < 0) -it else it } % palettes.size).toInt() }
    val colors = if (MaterialTheme.colorScheme.background == Color.Black) Color(0xFF303030) to Color.White else palettes[index]
    Box(Modifier.size(if (small) 36.dp else 42.dp).background(colors.first, CircleShape), contentAlignment = Alignment.Center) { Text(initials.take(2), color = colors.second, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun CompactSimIcon(line: SimLine, preferences: Preferences) {
    val description = if (line == SimLine.UNKNOWN) "Unknown SIM" else "SIM ${line.slot}, ${preferences.label(line)}"
    Row(Modifier.clearAndSetSemantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.SimCard, null, Modifier.size(12.dp), tint = lineColor(line))
        Text(if (line == SimLine.UNKNOWN) "?" else line.slot.toString(), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = lineColor(line))
    }
}

@Composable
private fun LineBadge(line: SimLine, preferences: Preferences) {
    Row(Modifier.background(if (line == SimLine.WORK) WorkBackground else Sage, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.SimCard, null, Modifier.size(10.dp), tint = lineColor(line)); Spacer(Modifier.width(3.dp)); Text(preferences.label(line).take(18), fontSize = 10.sp, color = lineColor(line), maxLines = 1)
    }
}

@Composable
private fun lineColor(line: SimLine) = if (line == SimLine.WORK) Work else if (line == SimLine.UNKNOWN) Muted else Forest
private fun clock(timestamp: Long): String = SimpleDateFormat("HH:mm", Locale.UK).format(Date(timestamp))
private fun listTime(timestamp: Long): String {
    val date = SimpleDateFormat("yyyyMMdd", Locale.UK)
    return if (date.format(Date(timestamp)) == date.format(Date())) clock(timestamp) else SimpleDateFormat("d MMM", Locale.UK).format(Date(timestamp))
}

private fun day(timestamp: Long): String {
    val format = SimpleDateFormat("yyyyMMdd", Locale.UK)
    val today = format.format(Date())
    val actual = format.format(Date(timestamp))
    return when (actual) { today -> "Today"; format.format(Date(System.currentTimeMillis() - 86_400_000)) -> "Yesterday"; else -> SimpleDateFormat("d MMM", Locale.UK).format(Date(timestamp)) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun InboxPreview() {
    SandeshTheme { Inbox(InboxState(DemoData.conversations(System.currentTimeMillis()), Preferences()), Modifier.fillMaxWidth(), {}, {}, {}, {}) }
}
