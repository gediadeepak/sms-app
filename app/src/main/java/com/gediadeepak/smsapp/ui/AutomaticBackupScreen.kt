package com.gediadeepak.smsapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gediadeepak.smsapp.AppCheckSetup
import com.gediadeepak.smsapp.FirebaseVerification
import com.gediadeepak.smsapp.backup.AutomaticBackups
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@Composable
internal fun FirebaseVerificationPanel() {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val activity=androidx.activity.compose.LocalActivity.current
    var message by remember {mutableStateOf<String?>(null)};var busy by remember {mutableStateOf(false)}
    var token by remember {mutableStateOf<String?>(null)}
    DisposableEffect(token!=null,activity) {
        val window=activity?.window;val wasSecure=window?.attributes?.flags?.and(android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0
        if(token!=null)window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {if(token!=null && !wasSecure)window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
    }
    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Device verification help",style=MaterialTheme.typography.titleMedium)
        Text(if(AppCheckSetup.isDevelopment) "Account email verification and device verification are different. A development APK needs this phone's own Firebase App Check registration. File backup works without either check." else "Device verification checks this installation. Use Sandesh from its configured release channel. File backup works without device verification.",style=MaterialTheme.typography.bodySmall)
        Button(enabled=!busy,onClick={scope.launch {busy=true;try {FirebaseVerification.check();message="This device passed verification. If cloud backup still fails, refresh your account and check Firestore access."}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message}finally{busy=false}}}){Text(if(busy)"Checking…" else "Check this device")}
        message?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
        if(AppCheckSetup.isDevelopment) { TextButton(onClick={token=runCatching {AppCheckSetup.debugToken(context)}.getOrNull();if(token==null)message="No development token is available. Try Check this device first. Release builds use Play Integrity; confirm the release certificate and distribution settings in Firebase."}){Text("Show this development device's token")}
        token?.let {secret ->
            Text("Developer registration only. Keep this token private. Firebase → Security → App Check → Apps → com.sandesh.app → Manage debug tokens.",style=MaterialTheme.typography.bodySmall)
            Text(secret)
            TextButton(onClick={
                val clip=ClipData.newPlainText("Sandesh development device token",secret)
                if(Build.VERSION.SDK_INT>=33)clip.description.extras=PersistableBundle().apply {putBoolean("android.content.extra.IS_SENSITIVE",true)}
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                message="Token copied. Register it privately in the Sandesh Firebase project, then retry."
            }){Text("Copy token")}
        } }
    } }
}

@Composable
internal fun AutomaticBackupPage() {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val store=remember {AutomaticBackups(context)}
    var revision by remember {mutableIntStateOf(0)}
    var mode by remember {mutableStateOf(store.prefs.getString("mode","file")!!)}
    var folder by remember {mutableStateOf(store.prefs.getString("folder","")!!)}
    var weekly by remember {mutableStateOf(store.prefs.getBoolean("weekly",false))}
    var password by remember {mutableStateOf("")};var confirmation by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){while(isActive){delay(3000);revision++}}
    revision
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri -> if(uri!=null)try {
        context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION);folder=uri.toString()
    } catch(e:Exception){error="This folder cannot grant persistent access. Choose another location."} }
    val enabled=store.prefs.getBoolean("enabled",false)
    val stamp=store.prefs.getLong("last_success",0)
    Text("Automatic encrypted backups",style=MaterialTheme.typography.titleLarge)
    Text(if(enabled)"Schedule enabled · "+store.prefs.getString("mode","")+if(store.prefs.getBoolean("weekly",false))" · weekly" else " · daily" else "Schedule is off")
    Text(if(stamp==0L)"No automatic backup has completed yet." else "Last successful automatic backup: "+DateFormat.getDateTimeInstance().format(Date(stamp)))
    store.prefs.getString("error","")?.takeIf {it.isNotEmpty()}?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    Text("Backups run daily or weekly when Android allows background work. For automatic encryption only, Sandesh keeps your backup password encrypted with Android Keystore on this phone. Keep the password yourself to restore on another phone; account password reset cannot recover it.")
    Row {FilterChip(selected=mode=="file",onClick={mode="file"},enabled=!busy,label={Text("Folder")});Spacer(Modifier.width(8.dp));FilterChip(selected=mode=="cloud",onClick={mode="cloud"},enabled=!busy,label={Text("Cloud")})}
    if(mode=="file") {OutlinedButton(onClick={picker.launch(null)},enabled=!busy){Text("Choose backup folder")};Text(if(folder.isBlank())"Choose a folder with persistent access." else "Backup folder selected.");Text("Keeps seven completed automatic copies for this configuration. The newest copy is written before an older copy is removed.",style=MaterialTheme.typography.bodySmall)}
    else {Text("Automatic cloud copies are kept separately for each phone. Choose the phone on the cloud restore page. Uses the account signed in when enabled; signing out disables cloud automation.");FirebaseVerificationPanel()}
    Row {Text("Weekly (otherwise daily)",Modifier.weight(1f));Switch(checked=weekly,onCheckedChange={weekly=it},enabled=!busy)}
    OutlinedTextField(value=password,onValueChange={password=it},label={Text("Backup password · 12+ characters")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,enabled=!busy)
    OutlinedTextField(value=confirmation,onValueChange={confirmation=it},label={Text("Confirm backup password")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,enabled=!busy)
    Button(enabled=!busy,onClick={
        if(password!=confirmation){error="Backup passwords do not match.";return@Button}
        val secret=password.toCharArray();password="";confirmation="";busy=true;error=null
        scope.launch {try {if(mode=="cloud"){com.gediadeepak.smsapp.account.FirebaseAccounts(context).verifiedUid();FirebaseVerification.check()};withContext(Dispatchers.IO){store.enable(secret,mode,folder,weekly)};revision++}catch(e:CancellationException){secret.fill('\u0000');throw e}catch(e:Exception){error=e.message}finally{busy=false;secret.fill('\u0000')}}
    }){Text(if(enabled)"Replace schedule" else "Enable automatic backups")}
    if(enabled) {
        OutlinedButton(enabled=!busy,onClick={runCatching {store.runNow()}.onFailure {error=it.message}}){Text("Run backup now")}
        TextButton(enabled=!busy,onClick={store.disable();revision++}){Text("Disable & forget stored password")}
    }
    Text("Queued SMS jobs, development tokens and this schedule's stored password are excluded from message backups. Changing phones requires enabling automation again.",style=MaterialTheme.typography.bodySmall)
}
