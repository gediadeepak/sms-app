package com.gediadeepak.smsapp

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import com.gediadeepak.smsapp.ui.SandeshApp
import com.gediadeepak.smsapp.ui.SandeshTheme
import com.gediadeepak.smsapp.ui.SandeshSplash
import com.gediadeepak.smsapp.telephony.SmsAccess
import com.gediadeepak.smsapp.account.AccountViewModel
import com.gediadeepak.smsapp.ui.AccountBackupFlow
import com.gediadeepak.smsapp.ui.AccountDestination

class MainActivity : ComponentActivity() {
    private lateinit var model: SandeshViewModel
    private lateinit var account: AccountViewModel
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> account.saveExport(uri) }
    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> account.selectRestoreFile(uri) }
    private var pendingIntent by mutableStateOf<Intent?>(null)
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.refresh() }
    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SmsAccess.isDefault(this)) requestSmsPermissions()
        model.refresh()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[SandeshViewModel::class.java]
        account = ViewModelProvider(this)[AccountViewModel::class.java]
        pendingIntent = intent.takeIf { it.action == Intent.ACTION_SENDTO || it.hasExtra("thread_id") }
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val accountState by account.state.collectAsStateWithLifecycle()
            var accountDestination by rememberSaveable { mutableStateOf<AccountDestination?>(null) }
            var showSplash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
            LaunchedEffect(accountState.exportRequest) {
                if (accountState.exportRequest != null) {
                    account.exportRequestHandled()
                    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.UK).format(java.util.Date())
                    exportLauncher.launch("Sandesh-$stamp.sandeshbackup")
                }
            }
            LaunchedEffect(accountState.restoring, accountState.inboxChanged) { model.setRestoring(accountState.restoring, accountState.inboxChanged) }
            LaunchedEffect(accountDestination) { model.setAccountVisible(accountDestination != null) }
            LaunchedEffect(state.ready, pendingIntent) {
                val request = pendingIntent
                if (state.ready && request != null) {
                    pendingIntent = null
                    val number = request.data?.schemeSpecificPart?.substringBefore('?')
                    if (number?.contains(',') == true || number?.contains(';') == true) model.reportError("Group messaging is not supported in this SMS-only build.")
                    else model.openExternal(number, request.getStringExtra("sms_body"), request.getStringExtra("thread_id"))
                }
            }
            SandeshTheme(state.preferences.theme) {
                Box(Modifier.fillMaxSize()) {
                    SandeshApp(model, ::setupSms, ::openAppSettings, ::requestNotifications,
                        onAccount = { accountDestination = AccountDestination.ACCOUNT }, onBackup = { accountDestination = AccountDestination.BACKUP })
                    accountDestination?.let { start -> AccountBackupFlow(account, start, state.ready,
                        onPickRestore = { restoreLauncher.launch(arrayOf("*/*")) }, onDismiss = { accountDestination = null }) }
                    if (showSplash) SandeshSplash { showSplash = false }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); if (::model.isInitialized) model.setForeground(true) }
    override fun onPause() { if (::model.isInitialized) model.setForeground(false); super.onPause() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); pendingIntent = intent }
    private fun setupSms() {
        if (SmsAccess.isDefault(this)) requestSmsPermissions()
        else try { SmsAccess.roleIntent(this)?.let(roleLauncher::launch) ?: model.reportError("The SMS role is not available on this device.") }
        catch (_: Exception) { model.reportError("Unable to open the default SMS picker. Check Android’s Default apps settings.") }
    }
    private fun requestSmsPermissions() {
        val missing = SmsAccess.permissions.filterNot { SmsAccess.allowed(this, it) }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray()) else model.refresh()
    }
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !SmsAccess.allowed(this, Manifest.permission.POST_NOTIFICATIONS)) permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }
    private fun openAppSettings() { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
}
