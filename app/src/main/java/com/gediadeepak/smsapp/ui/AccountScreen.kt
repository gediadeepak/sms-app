@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gediadeepak.smsapp.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gediadeepak.smsapp.account.AccountViewModel
import java.text.DateFormat
import java.util.Date

private enum class BackupAction { EXPORT, CLOUD_BACKUP, FILE_RESTORE, CLOUD_RESTORE }

@Composable
fun AccountScreen(model: AccountViewModel, smsReady: Boolean, onPickRestore: () -> Unit, onDismiss: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    var mode by rememberSaveable { mutableStateOf("login") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    // Passwords never enter SavedState, preferences or the backup payload.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }
    var backupAction by rememberSaveable { mutableStateOf<BackupAction?>(null) }
    val busy = state.busy != null
    val cloudReady = state.profile.uid != null && state.profile.verified && state.profile.configured
    LaunchedEffect(state.restorePasswordRequested) {
        if (state.restorePasswordRequested) { backupAction = BackupAction.FILE_RESTORE; model.restorePasswordHandled() }
    }
    Dialog(onDismissRequest = { if (!busy) { model.clearRestore(); onDismiss() } }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            Scaffold(containerColor = Canvas, topBar = {
                TopAppBar(title = { Text("Account & backup", fontWeight = FontWeight.SemiBold) }, navigationIcon = {
                    IconButton(onClick = { model.clearRestore(); onDismiss() }, enabled = !busy) { Icon(Icons.Outlined.Close, "Close account and backup") }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas))
            }) { padding ->
                Column(Modifier.padding(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { SandeshLogo(Modifier.size(52.dp)); Spacer(Modifier.width(14.dp)); Column { Text("Your messages, kept close.", fontSize = 22.sp, fontWeight = FontWeight.Medium); Text("SMS works without an account.", color = Muted, fontSize = 14.sp) } }
                    if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(state.busy.orEmpty(), color = Forest, fontSize = 14.sp) }
                    state.notice?.let { message -> NoticeCard(message, false) { model.clearMessage() } }
                    state.error?.let { message -> NoticeCard(message, true) { model.clearMessage() } }
                    if (state.profile.uid != null) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(state.profile.displayName?.takeIf { it.isNotBlank() } ?: "Your Sandesh account", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                                Text(state.profile.email.orEmpty(), color = Muted)
                                if (!state.profile.verified) {
                                    Text("Verify your email to enable private cloud backups.", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                                    OutlinedButton(onClick = model::verifyEmail, enabled = !busy) { Text("Resend verification") }
                                } else Text("Email verified", color = Forest, fontSize = 14.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TextButton(onClick = model::refreshAccount, enabled = !busy) { Text("Refresh account") }
                                    TextButton(onClick = model::signOut, enabled = !busy) { Text("Sign out") }
                                }
                            }
                        }
                    } else {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = mode == "login", onClick = { mode = "login"; password = ""; confirm = ""; validation = null }, enabled = !busy, label = { Text("Log in") })
                                    FilterChip(selected = mode == "register", onClick = { mode = "register"; password = ""; confirm = ""; validation = null }, enabled = !busy, label = { Text("Register") })
                                }
                                Text(when (mode) { "register" -> "A space of your own."; "forgot" -> "Let’s get you back in."; else -> "Welcome back." }, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                                if (!state.profile.configured) Text("Online accounts are not available in this build yet. You can still use SMS and encrypted file backups.", color = Muted, fontSize = 14.sp)
                                if (mode == "register") OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Name (optional)") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = email, onValueChange = { email = it; validation = null }, label = { Text("Email") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !busy, modifier = Modifier.fillMaxWidth())
                                if (mode != "forgot") {
                                    OutlinedTextField(value = password, onValueChange = { password = it; validation = null }, label = { Text("Password") }, singleLine = true,
                                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Hide password" else "Show password") } }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                                    if (mode == "register") OutlinedTextField(value = confirm, onValueChange = { confirm = it; validation = null }, label = { Text("Confirm password") }, singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !busy, modifier = Modifier.fillMaxWidth())
                                }
                                validation?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                                Button(onClick = {
                                    when {
                                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> validation = "Enter a valid email address."
                                        mode == "register" && password.length < 8 -> validation = "Use at least 8 characters for your account password."
                                        mode == "register" && password != confirm -> validation = "The account passwords do not match."
                                        mode != "forgot" && password.isEmpty() -> validation = "Enter your password."
                                        else -> {
                                            when (mode) { "register" -> model.register(name, email, password); "forgot" -> model.forgot(email); else -> model.login(email, password) }
                                            password = ""; confirm = ""
                                        }
                                    }
                                }, enabled = !busy && state.profile.configured, modifier = Modifier.fillMaxWidth()) { Text(when (mode) { "register" -> "Create account"; "forgot" -> "Send reset link"; else -> "Log in" }) }
                                TextButton(onClick = { mode = if (mode == "forgot") "login" else "forgot"; password = ""; confirm = ""; validation = null }, enabled = !busy) { Text(if (mode == "forgot") "Back to login" else "Forgot password?") }
                                if (mode != "forgot") {
                                    HorizontalDivider()
                                    OutlinedButton(onClick = { activity?.let(model::google) }, enabled = !busy && state.profile.googleConfigured && activity != null, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }
                                    if (!state.profile.googleConfigured) Text("Google sign-in is not available for this build yet.", fontSize = 12.sp, color = Muted)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("A backup you can take with you.", fontSize = 23.sp, fontWeight = FontWeight.Medium)
                    Text("Back up SMS history, stars, archives and appearance. Your backup is encrypted before it leaves your device.", color = Muted, fontSize = 14.sp, lineHeight = 23.sp)
                    if (!smsReady) Text("Complete the default SMS app and permission setup before backing up or restoring.", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    OutlinedButton(onClick = { backupAction = BackupAction.EXPORT }, enabled = !busy && smsReady, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.SaveAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(9.dp)); Text("Export encrypted file") }
                    OutlinedButton(onClick = onPickRestore, enabled = !busy && smsReady, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(9.dp)); Text("Restore from file") }
                    Text("Account cloud backup", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    if (!cloudReady) Text("Sign in and verify your email to use cloud backup.", color = Muted, fontSize = 14.sp)
                    Button(onClick = { backupAction = BackupAction.CLOUD_BACKUP }, enabled = !busy && smsReady && cloudReady, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CloudUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(9.dp)); Text("Back up to cloud") }
                    OutlinedButton(onClick = { backupAction = BackupAction.CLOUD_RESTORE }, enabled = !busy && smsReady && cloudReady, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CloudDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(9.dp)); Text("Restore latest cloud backup") }
                    Text("Cloud backup replaces the previous snapshot after a complete upload. Your backup password is separate from your account password. It cannot be recovered by password reset.", color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
                    Text("SMS only, up to 16 MiB / 100,000 messages. Restore merges messages without deleting your inbox. Restored history uses Unknown SIM; it never sends messages.", color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
        if (backupAction != null) {
            val action = backupAction!!
            val creating = action == BackupAction.EXPORT || action == BackupAction.CLOUD_BACKUP
            BackupPasswordDialog(creating, onDismiss = { backupAction = null }, onConfirm = { pass ->
                backupAction = null
                when (action) {
                    BackupAction.EXPORT -> model.export(pass)
                    BackupAction.CLOUD_BACKUP -> model.backupCloud(pass)
                    BackupAction.FILE_RESTORE -> model.inspectRestore(pass, false)
                    BackupAction.CLOUD_RESTORE -> model.inspectRestore(pass, true)
                }
            })
        }
        state.preview?.let { preview -> AlertDialog(onDismissRequest = { if (!busy) model.clearRestore() }, title = { Text("Restore this backup?") },
            text = { Text("${preview.messageCount} SMS messages\nCreated ${DateFormat.getDateTimeInstance().format(Date(preview.createdAt))}\n\nExisting messages will be kept, duplicates skipped, and saved messages will not be sent. The default sending SIM will return to Ask every time.") },
            confirmButton = { Button(onClick = model::confirmRestore, enabled = !busy) { Text(if (busy) "Restoring…" else "Restore messages") } },
            dismissButton = { TextButton(onClick = model::clearRestore, enabled = !busy) { Text("Cancel") } }) }
    }
}

@Composable
private fun BackupPasswordDialog(creating: Boolean, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (creating) "Protect your backup." else "Unlock your backup.") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (creating) "Choose a backup password of at least 12 characters. Keep it safe: your account password reset cannot recover this backup." else "Enter the password used when this backup was created. Your inbox will not change until verification and confirmation are complete.", fontSize = 14.sp, lineHeight = 23.sp)
            OutlinedTextField(value = password, onValueChange = { password = it; error = null }, label = { Text("Backup password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            if (creating) OutlinedTextField(value = confirm, onValueChange = { confirm = it; error = null }, label = { Text("Confirm backup password") }, singleLine = true,
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

@Composable
private fun NoticeCard(text: String, error: Boolean, onDismiss: () -> Unit) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else Sage, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(text, color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp, lineHeight = 23.sp); TextButton(onClick = onDismiss) { Text("Dismiss") } }
    }
}
