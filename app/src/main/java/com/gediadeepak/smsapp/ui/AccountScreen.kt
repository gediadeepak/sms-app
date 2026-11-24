@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gediadeepak.smsapp.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import com.gediadeepak.smsapp.account.AccountState
import com.gediadeepak.smsapp.account.AccountViewModel

/** Each destination displays one focused page; cloud sign-in returns to its originating page. */
enum class AccountDestination(val title: String) {
    ACCOUNT("Account"), LOGIN("Log in"), REGISTER("Create account"), RESET("Reset password"),
    BACKUP("Backup & restore"), FILES("File backup & restore"), CLOUD("Cloud backup & restore")
}

@Composable
fun AccountBackupFlow(model: AccountViewModel, startDestination: AccountDestination, smsReady: Boolean,
                      onPickRestore: () -> Unit, onDismiss: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(startDestination) }
    var accountReturn by rememberSaveable { mutableStateOf<AccountDestination?>(null) }
    var email by rememberSaveable { mutableStateOf("") }
    var backupAction by rememberSaveable { mutableStateOf<BackupAction?>(null) }
    val busy = state.busy != null
    val activity = LocalActivity.current
    fun navigate(target: AccountDestination) { model.clearMessage(); destination = target }
    fun back() {
        if (busy) return
        model.clearRestore()
        when (destination) {
            AccountDestination.REGISTER, AccountDestination.RESET -> navigate(AccountDestination.LOGIN)
            AccountDestination.LOGIN -> navigate(accountReturn ?: AccountDestination.ACCOUNT)
            AccountDestination.FILES, AccountDestination.CLOUD -> navigate(AccountDestination.BACKUP)
            AccountDestination.ACCOUNT -> if (accountReturn != null) navigate(accountReturn!!) else onDismiss()
            AccountDestination.BACKUP -> onDismiss()
        }
    }
    LaunchedEffect(state.profile.uid, destination) {
        if (state.profile.uid != null && destination in setOf(AccountDestination.LOGIN, AccountDestination.REGISTER)) {
            destination = if (accountReturn == AccountDestination.CLOUD && state.profile.verified) AccountDestination.CLOUD else AccountDestination.ACCOUNT
        }
    }
    LaunchedEffect(state.restorePasswordRequested) {
        if (state.restorePasswordRequested) {
            destination = AccountDestination.FILES
            backupAction = BackupAction.FILE_RESTORE
            model.restorePasswordHandled()
        }
    }
    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(containerColor = Canvas, topBar = {
            TopAppBar(title = { Text(destination.title, fontWeight = FontWeight.SemiBold) }, navigationIcon = {
                IconButton(onClick = ::back, enabled = !busy) {
                    if (destination == startDestination) Icon(Icons.Outlined.Close, "Close ${destination.title}")
                    else Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas))
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                key(destination) {
                    Column(Modifier.widthIn(max = 560.dp).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(state.busy.orEmpty(), color = Forest, fontSize = 14.sp) }
                        state.notice?.let { AccountNotice(it, false, model::clearMessage) }
                        state.error?.let { AccountNotice(it, true, model::clearMessage) }
                        when (destination) {
                            AccountDestination.ACCOUNT -> AccountHome(state, model,
                                onLogin = { navigate(AccountDestination.LOGIN) }, onRegister = { navigate(AccountDestination.REGISTER) },
                                onGoogle = { activity?.let(model::google) }, googleAvailable = activity != null)
                            AccountDestination.LOGIN, AccountDestination.REGISTER, AccountDestination.RESET -> AccountForm(destination, state, model,
                                email, { email = it }, ::navigate, onGoogle = { activity?.let(model::google) }, googleAvailable = activity != null)
                            AccountDestination.BACKUP -> BackupHome(::navigate)
                            AccountDestination.FILES -> FileBackupPage(smsReady, busy, onExport = { backupAction = BackupAction.EXPORT }, onRestore = onPickRestore)
                            AccountDestination.CLOUD -> CloudBackupPage(state, smsReady,
                                onBackup = { backupAction = BackupAction.CLOUD_BACKUP }, onRestore = { backupAction = BackupAction.CLOUD_RESTORE },
                                onAccount = { accountReturn = AccountDestination.CLOUD; navigate(if (state.profile.uid == null) AccountDestination.LOGIN else AccountDestination.ACCOUNT) })
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }
        backupAction?.let { action ->
            BackupPasswordDialog(action == BackupAction.EXPORT || action == BackupAction.CLOUD_BACKUP,
                onDismiss = { backupAction = null; model.clearRestore() }, onConfirm = { pass ->
                    backupAction = null
                    when (action) {
                        BackupAction.EXPORT -> model.export(pass)
                        BackupAction.CLOUD_BACKUP -> model.backupCloud(pass)
                        BackupAction.FILE_RESTORE -> model.inspectRestore(pass, false)
                        BackupAction.CLOUD_RESTORE -> model.inspectRestore(pass, true)
                    }
                })
        }
        RestoreConfirmation(state, model)
    }
}

@Composable
private fun AccountHome(state: AccountState, model: AccountViewModel, onLogin: () -> Unit, onRegister: () -> Unit,
                        onGoogle: () -> Unit, googleAvailable: Boolean) {
    val busy = state.busy != null
    SandeshLogo(Modifier.size(64.dp))
    if (state.profile.uid == null) {
        PageHeading("Your space in Sandesh.", "Sign in to keep an encrypted cloud backup. You can keep using SMS and file backups without an account.")
        GoogleSignInButton(state, googleAvailable, onGoogle)
        Button(onClick = onLogin, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Log in with email") }
        OutlinedButton(onClick = onRegister, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Create an account") }
    } else {
        PageHeading(state.profile.displayName?.takeIf { it.isNotBlank() } ?: "Your Sandesh account", state.profile.email.orEmpty())
        Surface(color = Sage, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (state.profile.verified) Icons.Outlined.VerifiedUser else Icons.Outlined.MarkEmailUnread, null, tint = Forest)
                Text(if (state.profile.verified) "Email verified" else "Verify your email", fontWeight = FontWeight.SemiBold)
                Text(if (state.profile.verified) "Your account is ready for cloud backup." else "Open the verification email, then refresh your account to enable cloud backups.", color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
                if (!state.profile.verified) OutlinedButton(onClick = model::verifyEmail, enabled = !busy) { Text("Resend verification email") }
                TextButton(onClick = model::refreshAccount, enabled = !busy) { Text("Refresh account") }
            }
        }
        OutlinedButton(onClick = model::signOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        Text("Signing out keeps your messages and backups.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun AccountForm(destination: AccountDestination, state: AccountState, model: AccountViewModel,
                        email: String, onEmail: (String) -> Unit, onNavigate: (AccountDestination) -> Unit,
                        onGoogle: () -> Unit, googleAvailable: Boolean) {
    val registering = destination == AccountDestination.REGISTER
    val resetting = destination == AccountDestination.RESET
    val busy = state.busy != null
    var name by remember { mutableStateOf("") }
    // Credentials never enter saved state; changing pages disposes these fields.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }
    PageHeading(when { resetting -> "Let’s get you back in."; registering -> "A space of your own."; else -> "Welcome back." },
        if (resetting) "We’ll email you a link to reset your account password." else "Your messages stay yours.")
    if (!resetting) {
        GoogleSignInButton(state, googleAvailable, onGoogle)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(Modifier.weight(1f)); Text("or use email", color = Muted, fontSize = 13.sp); HorizontalDivider(Modifier.weight(1f))
        }
    }
    if (!state.profile.configured) Text("Online accounts are unavailable in this build. SMS and file backups still work.", color = Muted, fontSize = 14.sp)
    if (registering) OutlinedTextField(name, { name = it.take(60) }, label = { Text("Name (optional)") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(email, { onEmail(it); validation = null }, label = { Text("Email") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !busy, modifier = Modifier.fillMaxWidth())
    if (!resetting) {
        OutlinedTextField(password, { password = it; validation = null }, label = { Text("Password") }, singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Hide password" else "Show password") } },
            enabled = !busy, modifier = Modifier.fillMaxWidth())
        if (registering) {
            Text("Use at least 8 characters.", color = Muted, fontSize = 12.sp)
            OutlinedTextField(confirm, { confirm = it; validation = null }, label = { Text("Confirm password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !busy, modifier = Modifier.fillMaxWidth())
        }
    }
    validation?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
    Button(onClick = {
        when {
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> validation = "Enter a valid email address."
            registering && password.length < 8 -> validation = "Use at least 8 characters for your account password."
            registering && password != confirm -> validation = "The account passwords do not match."
            !resetting && password.isEmpty() -> validation = "Enter your password."
            else -> {
                when { resetting -> model.forgot(email); registering -> model.register(name, email, password); else -> model.login(email, password) }
                password = ""; confirm = ""
            }
        }
    }, enabled = !busy && state.profile.configured, modifier = Modifier.fillMaxWidth()) { Text(when { resetting -> "Send reset link"; registering -> "Create account"; else -> "Log in" }) }
    if (destination == AccountDestination.LOGIN) {
        TextButton(onClick = { onNavigate(AccountDestination.RESET) }, enabled = !busy) { Text("Forgot password?") }
        TextButton(onClick = { onNavigate(AccountDestination.REGISTER) }, enabled = !busy) { Text("New to Sandesh? Create an account") }
    } else TextButton(onClick = { onNavigate(AccountDestination.LOGIN) }, enabled = !busy) { Text("Back to login") }
    if (resetting) Text("This resets your account password. Your backup password stays the same.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
}

@Composable
private fun GoogleSignInButton(state: AccountState, available: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = state.busy == null && state.profile.googleConfigured && available, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }
    if (!state.profile.googleConfigured) Text("Google sign-in is unavailable in this build.", fontSize = 12.sp, color = Muted)
}

@Composable
internal fun PageHeading(title: String, subtitle: String) {
    Text(title, fontSize = 28.sp, lineHeight = 35.sp, fontWeight = FontWeight.Medium, color = Ink)
    Text(subtitle, fontSize = 14.sp, lineHeight = 23.sp, color = Muted)
}

@Composable
private fun AccountNotice(text: String, error: Boolean, onDismiss: () -> Unit) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else Sage, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text, color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp, lineHeight = 23.sp)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
