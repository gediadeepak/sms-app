package com.gediadeepak.smsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.gediadeepak.smsapp.*
import com.gediadeepak.smsapp.core.*
import com.gediadeepak.smsapp.scheduling.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.text.DateFormat
import java.text.ParsePosition
import java.util.*

@Composable
private fun queueRows():List<QueuedSms> {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var rows by remember { mutableStateOf(emptyList<QueuedSms>()) }
    LaunchedEffect(context,lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while(isActive) {
        rows=withContext(Dispatchers.IO) {SendQueue(context).use {it.list()}};delay(1000)
    } } }
    return rows
}
private fun queueDate(due:Long)=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(due))

@Composable
internal fun PendingSendUndo(number:String) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val simNames=runCatching {com.gediadeepak.smsapp.telephony.SmsAccess.subscriptions(context).associate {it.subscriptionId to it.label}}.getOrDefault(emptyMap())
    var cancelError by remember(number) {mutableStateOf<String?>(null)}
    val pending=queueRows().filter {it.number==number && it.state=="WAITING"}
    pending.take(3).forEach { row -> Row(Modifier.fillMaxWidth()) {
        Text("Queued · "+(simNames[row.sim] ?: "Saved SIM unavailable")+" · "+queueDate(row.due),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={scope.launch {val ok=withContext(Dispatchers.IO){runCatching {SendQueue.cancel(context,row.id)}.getOrDefault(false)};if(!ok)cancelError="Cancellation was not confirmed. Sending may have started; check the conversation."}}){Text("Undo")}
    } }
    cancelError?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
}

@Composable
internal fun SendQueueScreen(onBack:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val rows=queueRows()
    var error by remember {mutableStateOf<String?>(null)}
    SettingsPage("Scheduled & Undo",onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Upcoming messages keep the SIM selected when queued. Android may delay background work. Cancel works until a message is handed to Android; delivered SMS cannot be recalled.")
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if(rows.isEmpty()) Text("No queued messages. Use the clock beside Send to schedule one.")
            rows.forEach {row -> OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(row.number,style=MaterialTheme.typography.titleMedium)
                androidx.compose.foundation.text.selection.SelectionContainer {Text(row.body)};Text(queueDate(row.due));Text("Saved subscription ${row.sim} · ${row.state}")
                if(row.detail.isNotEmpty()) Text(row.detail,style=MaterialTheme.typography.bodySmall)
                if(row.state=="WAITING") TextButton(onClick={scope.launch {val ok=withContext(Dispatchers.IO){runCatching {SendQueue.cancel(context,row.id)}.getOrDefault(false)};if(!ok)error="This message has already started sending. Check the conversation."}}){Text("Cancel / Undo")}
                else if(row.state!="CLAIMED") TextButton(onClick={scope.launch(Dispatchers.IO){SendQueue(context).use {it.remove(row.id)}}}){Text("Remove record")}
            } } }
            Text("Messages over 24 hours late require review. Interrupted handoffs are not automatically retried, to avoid sending duplicates. Queued jobs are not restored from backups.",style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun ScheduleSmsScreen(state:InboxState,model:SandeshViewModel,onBack:()->Unit) {
    val format=remember {SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT).apply {isLenient=false}}
    var time by remember {mutableStateOf(format.format(Date(System.currentTimeMillis()+3600000)))}
    var error by remember {mutableStateOf<String?>(null)}
    SettingsPage("Schedule message",onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(state.selected?.displayName.orEmpty(),style=MaterialTheme.typography.titleLarge)
            Text(state.draft)
            Text(state.subscriptions.firstOrNull {it.subscriptionId==state.sendSubscriptionId}?.label ?: "Choose a SIM in the composer first.")
            OutlinedTextField(value=time,onValueChange={time=it},label={Text("Local time: yyyy-MM-dd HH:mm")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("Timezone: ${TimeZone.getDefault().id}. The phone must retain SMS access and this SIM. Android may send after the chosen time when background work is delayed.")
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            Button(enabled=state.canSend && state.draft.isNotBlank(),onClick={
                try {val pos=ParsePosition(0);val stamp=format.parse(time,pos);require(stamp!=null && pos.index==time.length){"Enter a valid date and time."};SendPolicy.due(System.currentTimeMillis(),stamp.time,true);model.send(stamp.time);onBack()}
                catch(e:Exception){error=e.message ?: "Choose a valid future time."}
            }) {Text("Schedule SMS")}
        }
    }
}

@Composable
internal fun BusinessGroupScreen(state:InboxState,model:SandeshViewModel,onBack:()->Unit) {
    val conversation=state.selected ?: return
    var label by remember(conversation.id) {mutableStateOf(conversation.vendor)}
    SettingsPage("Business group",onBack) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(conversation.displayName,style=MaterialTheme.typography.titleLarge)
            Text("Group different sender IDs under a business name. Original conversations and SIM details stay separate. Group headings count currently loaded conversations; load more to include older ones. Nothing is shared with AI.")
            OutlinedTextField(value=label,onValueChange={label=it.take(40)},label={Text("Group name (optional)")},modifier=Modifier.fillMaxWidth())
            BusinessGroups.suggestion(conversation.number)?.let { suggested -> TextButton(onClick={label=suggested}) {Text("Use suggestion: $suggested")} }
            Button(onClick={model.vendor(label);model.savePreferences(state.preferences.copy(groupBusinesses=true));onBack()}){Text("Save group & enable grouping")}
            TextButton(onClick={model.vendor("");onBack()}){Text("Clear custom group")}
            Text("Known business sender IDs can be grouped using local rules. Turn grouping off in Settings → Sending & organization.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
