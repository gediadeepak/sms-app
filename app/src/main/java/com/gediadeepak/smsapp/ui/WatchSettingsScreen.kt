package com.gediadeepak.smsapp.ui

import android.widget.Toast
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
import com.gediadeepak.smsapp.telephony.SmsNotificationStatus
import com.gediadeepak.smsapp.telephony.SmsNotifications
import com.gediadeepak.smsapp.telephony.WatchOptions
import com.gediadeepak.smsapp.telephony.WatchSettings

@Composable
internal fun WatchSettingsScreen(status: SmsNotificationStatus, onNotifications: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { WatchSettings(context) }
    var options by remember { mutableStateOf(store.load()) }
    fun save(value: WatchOptions) {
        store.save(value)
        options = value
        SmsNotifications.clearWatchNotifications(context)
    }
    SettingsPage("Smartwatch", onBack) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Sony SmartWatch 3", style = MaterialTheme.typography.titleLarge)
            Text("Phone notification support for Android Wear 1.5. Pair the watch with your phone using its Android Wear / Wear OS companion app, allow notification access there, and make sure Sandesh is not blocked.", color = Muted, lineHeight = 23.sp)
            Text("This version adds actions to mirrored notifications. It does not install a separate watch app, inbox or tile. Pairing, mirroring and voice entry depend on the old watch software and companion app.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    WatchToggle("Watch notification actions", "Mark read and draft a reply from an incoming SMS alert. Off by default.", options.actions) { save(options.copy(actions = it)) }
                    HorizontalDivider()
                    WatchToggle("Show sender and message previews", "Shows message text and matched contact names in phone notifications and on paired watches. Off keeps generic SMS alerts.", options.previews) { save(options.copy(previews = it)) }
                }
            }
            Text("Previews can reveal sensitive messages, including verification codes, to your paired watch. Android lock-screen and companion-app settings also control visibility. Changing these options clears current incoming alerts and watch-review notifications; it does not delete SMS.", color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
            Text("How watch replies work", style = MaterialTheme.typography.titleMedium)
            Text("On an incoming alert, choose Draft reply and enter text using the watch. Sandesh posts a private ‘Watch reply ready to review’ notification on the phone. Open it to add the text to that conversation’s draft, review it, choose a SIM and tap Send. Existing draft text is preserved. New watch replies replace earlier pending watch-review alerts for that conversation.", color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
            Text("Nothing is sent from the watch automatically. Drafts are limited to 1,600 characters. Mark read applies only through the message on that alert; later incoming messages stay unread. Spam and blocked senders have no watch alerts. Watch settings are device-specific and excluded from backups.", color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
            if (status != SmsNotificationStatus.ENABLED) Text("Enable Sandesh notifications before testing the watch.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onNotifications, modifier = Modifier.fillMaxWidth()) { Text("Phone notification settings") }
            Button(onClick = {
                if (SmsNotifications.watchTest(context)) Toast.makeText(context, "Test alert posted. Check your phone and paired watch; no SMS was sent.", Toast.LENGTH_LONG).show()
                else Toast.makeText(context, "Android blocked the test alert. Check phone notification settings.", Toast.LENGTH_LONG).show()
            }, enabled = status == SmsNotificationStatus.ENABLED, modifier = Modifier.fillMaxWidth()) { Text("Send watch test alert") }
            Text("The test alert checks notification mirroring only. Test Mark read and Draft reply using a real incoming SMS after pairing. There is no AI processing in this watch feature.", color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun WatchToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(detail, color = Muted, fontSize = 12.sp, lineHeight = 20.sp) }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
