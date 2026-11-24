package com.gediadeepak.smsapp.ui

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gediadeepak.smsapp.audio.HeadsetReadout
import com.gediadeepak.smsapp.telephony.SmsNotificationStatus

@Composable
internal fun HeadsetSettingsDialog(notificationStatus: SmsNotificationStatus, onNotifications: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var outputs by remember { mutableStateOf(HeadsetReadout.outputs(context)) }
    var chosen by remember { mutableStateOf(HeadsetReadout.selectedKey(context)) }
    var announce by remember { mutableStateOf(HeadsetReadout.announceEnabled(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(context) {
        val audio = context.getSystemService(AudioManager::class.java)
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { outputs = HeadsetReadout.outputs(context) }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { outputs = HeadsetReadout.outputs(context) }
        }
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audio.unregisterAudioDeviceCallback(callback) }
    }
    val connected = outputs.any { it.key == chosen }
    val ready = Build.VERSION.SDK_INT >= 28 && connected && notificationStatus == SmsNotificationStatus.ENABLED
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Headset readout") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Announce the sender when new SMS arrives. Message text is spoken only when you choose Read message in a notification or tap its speaker button.", fontSize = 14.sp, lineHeight = 23.sp)
            if (Build.VERSION.SDK_INT < 28) Text("This feature requires Android 9 or newer.", color = Muted)
            Text("Choose your headphones", style = MaterialTheme.typography.titleSmall)
            Text("Select a private audio device, not a Bluetooth speaker or car stereo. Playback stops when the selected route is lost or changes.", fontSize = 12.sp, lineHeight = 21.sp, color = Muted)
            if (outputs.isEmpty()) Text("Connect wired, USB or Bluetooth headphones, then choose them here.", color = Muted, fontSize = 13.sp)
            outputs.forEach { output -> Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = chosen == output.key, onClick = { HeadsetReadout.choose(context, output); chosen = output.key; error = null })
                TextButton(onClick = { HeadsetReadout.choose(context, output); chosen = output.key; error = null }) { Text(output.label) }
            } }
            if (chosen != null && !connected) Text("Saved output: ${HeadsetReadout.selectedName(context)}. Reconnect it or choose it again above.", color = Muted, fontSize = 12.sp)
            if (notificationStatus != SmsNotificationStatus.ENABLED) TextButton(onClick = onNotifications) { Text("Enable message notifications first") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Announce SMS senders"); Text("Sender only · never reads the body automatically", color = Muted, fontSize = 12.sp) }
                Switch(checked = announce, onCheckedChange = { HeadsetReadout.setAnnounce(context, it); announce = it }, enabled = announce || ready)
            }
            OutlinedButton(onClick = { error = if (HeadsetReadout.test(context)) null else HeadsetReadout.problem(context) ?: "Unable to start the headset test." }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("Test headset") }
            OutlinedButton(onClick = { HeadsetReadout.stop(context) }, modifier = Modifier.fillMaxWidth()) { Text("Stop readout") }
            if (chosen != null) TextButton(onClick = { HeadsetReadout.forget(context); chosen = null; announce = false }) { Text("Forget selected headset") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            Text("Uses an installed offline speech voice. Spam and blocked messages are never announced. Very long messages are shortened with a spoken notice. Some unidentified audio devices must be selected again after the app process restarts.", color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
