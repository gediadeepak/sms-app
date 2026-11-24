package com.gediadeepak.smsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun BlockedSendersDialog(senders: List<String>, onUnblock: (String) -> Unit, onDismiss: () -> Unit) {
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Blocked senders") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Their SMS stays in Spam without alerts. Unblocking restores conversations to your inbox. Calls and other apps are unaffected.", fontSize = 13.sp, color = Muted)
            if (senders.isEmpty()) Text("No blocked senders.", color = Muted)
            else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(senders, key = { it }) { sender -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sender, Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { pending = sender }) { Text("Unblock") }
                } }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
    pending?.let { sender -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("Unblock sender?") },
        text = { Text("$sender will return to your inbox and new SMS can notify you again.") },
        confirmButton = { Button(onClick = { onUnblock(sender); pending = null }) { Text("Unblock") } },
        dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }) }
}
