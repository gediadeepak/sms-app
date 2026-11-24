package com.gediadeepak.smsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gediadeepak.smsapp.account.AccountState
import com.gediadeepak.smsapp.account.AccountViewModel
import java.text.DateFormat
import java.util.Date

internal enum class BackupAction { EXPORT, CLOUD_BACKUP, FILE_RESTORE, CLOUD_RESTORE }

@Composable
internal fun BackupHome(onNavigate: (AccountDestination) -> Unit) {
    PageHeading("Keep your messages close.", "Choose where to keep an encrypted copy of your SMS history.")
    BackupDestinationCard(Icons.Outlined.SaveAlt, "File backup & restore", "Save a password-protected file or restore one. No account needed.") { onNavigate(AccountDestination.FILES) }
    BackupDestinationCard(Icons.Outlined.CloudUpload, "Cloud backup & restore", "Keep an encrypted backup with your verified Sandesh account.") { onNavigate(AccountDestination.CLOUD) }
    BackupLimits()
}

@Composable
private fun BackupDestinationCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = Forest, modifier = Modifier.size(30.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
            Text(subtitle, color = Muted, fontSize = 14.sp, lineHeight = 23.sp)
            Text("Open →", color = Forest, fontSize = 14.sp)
        }
    }
}

@Composable
internal fun FileBackupPage(smsReady: Boolean, busy: Boolean, onExport: () -> Unit, onRestore: () -> Unit) {
    PageHeading("A copy you can take with you.", "Save an encrypted backup wherever you choose. Restore it on this phone or another Android phone with your backup password.")
    SmsBackupAccess(smsReady)
    Button(onClick = onExport, enabled = !busy && smsReady, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.SaveAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(9.dp)); Text("Export encrypted file")
    }
    OutlinedButton(onClick = onRestore, enabled = !busy && smsReady, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(9.dp)); Text("Restore from file")
    }
    HorizontalDivider()
    Text("No account needed", fontWeight = FontWeight.SemiBold)
    Text("Choose a separate backup password and keep it safe. It cannot be recovered through account password reset.", color = Muted, fontSize = 14.sp, lineHeight = 23.sp)
    BackupLimits()
}

@Composable
internal fun CloudBackupPage(state: AccountState, smsReady: Boolean, onBackup: () -> Unit, onRestore: () -> Unit, onAccount: () -> Unit) {
    val busy = state.busy != null
    val ready = state.profile.uid != null && state.profile.verified && state.profile.configured
    PageHeading("Your backup, with you.", "Your SMS backup is encrypted on your phone before it is uploaded to your account.")
    when {
        state.profile.uid == null -> {
            Text("Sign in to use cloud backup. File backups are available without an account.", color = Muted, fontSize = 14.sp, lineHeight = 23.sp)
            Button(onClick = onAccount, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Sign in to continue") }
        }
        !state.profile.verified -> {
            Text("Verify your email before using cloud backup.", fontWeight = FontWeight.SemiBold)
            Text(state.profile.email.orEmpty(), color = Muted)
            Button(onClick = onAccount, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Open account to verify email") }
        }
        else -> {
            Text("Signed in as ${state.profile.email.orEmpty()}", color = Muted, fontSize = 14.sp)
            TextButton(onClick = onAccount, enabled = !busy) { Text("Manage account") }
        }
    }
    SmsBackupAccess(smsReady)
    Button(onClick = onBackup, enabled = !busy && smsReady && ready, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.CloudUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(9.dp)); Text("Back up to cloud")
    }
    OutlinedButton(onClick = onRestore, enabled = !busy && smsReady && ready, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.CloudDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(9.dp)); Text("Restore latest cloud backup")
    }
    HorizontalDivider()
    Text("Keep your backup password safe", fontWeight = FontWeight.SemiBold)
    Text("It is separate from your account password and cannot be reset. A completed cloud upload replaces your previous backup.", color = Muted, fontSize = 14.sp, lineHeight = 23.sp)
    BackupLimits()
}

@Composable
private fun SmsBackupAccess(ready: Boolean) {
    if (!ready) Text("Complete the default SMS app and permission setup before backing up or restoring.", color = MaterialTheme.colorScheme.error, fontSize = 14.sp, lineHeight = 23.sp)
}

@Composable
private fun BackupLimits() {
    Text("Includes SMS history, stars, archives, aliases, spam flags, blocked senders and appearance. SMS only, up to 16 MiB / 100,000 messages. Restore keeps your inbox, merges blocked senders, skips duplicates and never sends restored messages. Historical SIMs show Unknown SIM.", color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
}

@Composable
internal fun RestoreConfirmation(state: AccountState, model: AccountViewModel) {
    val busy = state.busy != null
    state.preview?.let { preview ->
        AlertDialog(onDismissRequest = { if (!busy) model.clearRestore() }, title = { Text("Restore this backup?") },
            text = { Text("${preview.messageCount} SMS messages\nCreated ${DateFormat.getDateTimeInstance().format(Date(preview.createdAt))}\n\nExisting messages will be kept, duplicates skipped, and saved messages will not be sent. Saved spam flags and blocked senders will also be restored. The default sending SIM will return to Ask every time.") },
            confirmButton = { Button(onClick = model::confirmRestore, enabled = !busy) { Text(if (busy) "Restoring…" else "Restore messages") } },
            dismissButton = { TextButton(onClick = model::clearRestore, enabled = !busy) { Text("Cancel") } })
    }
}

@Composable
internal fun BackupPasswordDialog(creating: Boolean, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (creating) "Protect your backup." else "Unlock your backup.") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (creating) "Choose a backup password of at least 12 characters. Keep it safe: your account password reset cannot recover this backup." else "Enter the password used when this backup was created. Your inbox will not change until verification and confirmation are complete.", fontSize = 14.sp, lineHeight = 23.sp)
            OutlinedTextField(password, { password = it; error = null }, label = { Text("Backup password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            if (creating) OutlinedTextField(confirm, { confirm = it; error = null }, label = { Text("Confirm backup password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }, confirmButton = { Button(onClick = {
        when {
            creating && password.length < 12 -> error = "Use at least 12 characters."
            creating && password != confirm -> error = "The backup passwords do not match."
            password.isEmpty() -> error = "Enter the backup password."
            else -> { val chars = password.toCharArray(); password = ""; confirm = ""; onConfirm(chars) }
        }
    }) { Text(if (creating) "Create backup" else "Verify backup") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
