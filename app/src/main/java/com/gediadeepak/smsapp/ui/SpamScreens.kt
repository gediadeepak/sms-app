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
internal fun BlockedSendersScreen(senders: List<String>, onUnblock: (String) -> Unit, onDismiss: () -> Unit) {
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    pending?.let { sender ->
        SettingsPage("Unblock sender?", { pending = null }) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("$sender will return to your inbox and new SMS can notify you again.")
                Button(onClick = { onUnblock(sender); pending = null }, modifier = Modifier.fillMaxWidth()) { Text("Unblock") }
                TextButton(onClick = { pending = null }) { Text("Cancel") }
            }
        }
        return
    }
    SettingsPage("Blocked senders", onDismiss) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Their SMS stays in Spam without alerts. Unblocking restores conversations to your inbox. Calls and other apps are unaffected.", fontSize = 13.sp, color = Muted)
            if (senders.isEmpty()) Text("No blocked senders.", color = Muted)
            else LazyColumn(Modifier.weight(1f)) {
                items(senders, key = { it }) { sender -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sender, Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { pending = sender }) { Text("Unblock") }
                } }
            }
        }
    }

}
