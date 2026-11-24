package com.gediadeepak.smsapp.audio

import android.app.KeyguardManager
import android.app.Service
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import android.provider.Telephony
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gediadeepak.smsapp.data.SandeshDatabase
import com.gediadeepak.smsapp.core.ReadoutRouteGuard
import com.gediadeepak.smsapp.core.ReadoutRouteDecision
import com.gediadeepak.smsapp.telephony.SmsAccess
import com.gediadeepak.smsapp.telephony.SmsNotifications
import com.gediadeepak.smsapp.audio.HeadsetReadout.READ
import com.gediadeepak.smsapp.audio.HeadsetReadout.ANNOUNCE
import com.gediadeepak.smsapp.audio.HeadsetReadout.TEST
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

/** Short-lived, user-controlled playback. No restart/replay after process death, no cloud TTS requests. */
@RequiresApi(28)
class HeadsetReadoutService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private var engine: TextToSpeech? = null
    private var engineReady = false
    private var engineInitialized = false
    private var destroyed = false
    private var generation = 0
    private var bodyRequest = false
    private var testRequest = false
    private var pendingText: String? = null
    private var audioFile: File? = null
    private var player: MediaPlayer? = null
    private var routeGuard: ReadoutRouteGuard? = null
    private var focus: AudioFocusRequest? = null
    private var activeAddress: String? = null
    private var activeThread: String? = null
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val noisy = object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) { if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) finishReadout() } }
    private val deviceChanges = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.id == player?.preferredDevice?.id } || HeadsetReadout.selected(this@HeadsetReadoutService) == null) finishReadout()
        }
    }

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        audio.registerAudioDeviceCallback(deviceChanges, handler)
        val directory = File(cacheDir, "headset-readout").apply { mkdirs() }
        directory.listFiles()?.filter { it.isFile && it.name.startsWith("speech-") }?.forEach { it.delete() }
        engine = TextToSpeech(applicationContext) { result -> handler.post {
            if (destroyed) return@post
            engineInitialized = true
            val voices = if (result == TextToSpeech.SUCCESS) engine?.voices.orEmpty().filter { !it.isNetworkConnectionRequired } else emptyList()
            val voice = voices.firstOrNull { it.locale.language == Locale.getDefault().language } ?: voices.firstOrNull { it.locale.language == "en" }
            engineReady = voice != null && engine?.setVoice(voice) == TextToSpeech.SUCCESS
            if (pendingText != null) { if (engineReady) synthesize(generation) else fail("Install an offline text-to-speech voice in Android settings.") }
        } }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { handler.post { if (!destroyed && id == generation.toString()) play(generation) } }
            @Deprecated("Android compatibility callback") override fun onError(id: String?) { handler.post { if (!destroyed && id == generation.toString()) fail("The speech engine could not read this message.") } }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != READ && intent?.action != ANNOUNCE && intent?.action != TEST) { finishReadout(); return START_NOT_STICKY }
        if (intent.action == ANNOUNCE && bodyRequest && player != null) return START_NOT_STICKY
        generation++
        val token = generation
        clearAudio()
        bodyRequest = intent.action == READ
        testRequest = intent.action == TEST
        pendingText = null
        activeAddress = null; activeThread = null
        try {
            SmsNotifications.ensureChannel(this)
            ServiceCompat.startForeground(this, NOTIFICATION_ID, SmsNotifications.readoutForeground(this, bodyRequest, testRequest),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        } catch (_: RuntimeException) { finishReadout(); return START_NOT_STICKY }
        if (testRequest) {
            if (HeadsetReadout.problem(this) != null) { fail(HeadsetReadout.problem(this)!!); return START_NOT_STICKY }
            pendingText = "Sandesh headset readout is ready."
            if (engineReady) synthesize(token) else if (engineInitialized) fail("Install an offline text-to-speech voice in Android settings.")
            handler.postDelayed({ if (!destroyed && token == generation) finishReadout() }, 30_000)
            return START_NOT_STICKY
        }
        val id = intent.getStringExtra("sms_id")?.toLongOrNull()
        if (id == null || id < 0 || HeadsetReadout.problem(this) != null) { fail(HeadsetReadout.problem(this) ?: "That message is unavailable."); return START_NOT_STICKY }
        if (bodyRequest && getSystemService(KeyguardManager::class.java).isKeyguardLocked) { fail("Unlock your phone before reading a message aloud."); return START_NOT_STICKY }
        scope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    check(SmsAccess.ready(this@HeadsetReadoutService)) { "Restore SMS access before using readout." }
                    contentResolver.query(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id), arrayOf("thread_id", "address", "body", "type"), null, null, null)?.use { c ->
                        check(c.moveToFirst() && c.getInt(3) == Telephony.Sms.MESSAGE_TYPE_INBOX) { "That incoming message is unavailable." }
                        val thread = c.getString(0); val address = c.getString(1).orEmpty()
                        val database = SandeshDatabase.get(this@HeadsetReadoutService)
                        val metadata = database.threadMetadata(thread)
                        check(!database.isSenderBlocked(address) && metadata?.spam != true) { "Spam and blocked messages are not read aloud." }
                        arrayOf(thread, address, metadata?.alias?.takeIf { it.isNotBlank() } ?: address, if (bodyRequest) c.getString(2).orEmpty() else "")
                    } ?: error("That message is unavailable.")
                }
                if (token != generation || destroyed) return@launch
                activeThread = message[0]; activeAddress = message[1]
                val limit = minOf(TextToSpeech.getMaxSpeechInputLength() - 300, 3500)
                val sender = message[2].take(120)
                pendingText = if (bodyRequest) "Message from $sender. ${message[3].take(limit)}${if (message[3].length > limit) ". Open Sandesh to read the rest of this long message." else ""}" else "New SMS from $sender."
                if (engineReady) synthesize(token) else if (engineInitialized) fail("Install an offline text-to-speech voice in Android settings.")
                handler.postDelayed({ if (!destroyed && token == generation) finishReadout() }, if (bodyRequest) 300_000 else 30_000)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token == generation) fail(e.message ?: "Unable to read this message.") }
        }
        return START_NOT_STICKY
    }

    private fun synthesize(token: Int) {
        val text = pendingText ?: return
        if (HeadsetReadout.selected(this) == null) { finishReadout(); return }
        try {
            audioFile = File.createTempFile("speech-", ".wav", File(cacheDir, "headset-readout"))
            if (engine?.synthesizeToFile(text, Bundle(), audioFile!!, token.toString()) != TextToSpeech.SUCCESS) fail("The speech engine could not start.")
        } catch (_: Exception) { fail("Unable to prepare speech audio. Check available device storage.") }
    }

    private fun play(token: Int) {
        if (destroyed || token != generation) return
        val output = HeadsetReadout.selected(this) ?: run { finishReadout(); return }
        routeGuard = ReadoutRouteGuard(output.id)
        if (audio.mode != AudioManager.MODE_NORMAL || HeadsetReadout.problem(this) != null) { finishReadout(); return }
        val database = SandeshDatabase.get(this)
        if (!testRequest && (!SmsAccess.ready(this) || activeAddress == null || database.isSenderBlocked(activeAddress!!) || database.threadMetadata(activeThread)?.spam == true)) { finishReadout(); return }
        if (bodyRequest && getSystemService(KeyguardManager::class.java).isKeyguardLocked) { finishReadout(); return }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true).setOnAudioFocusChangeListener({ change -> if (change < 0) finishReadout() }, handler).build()
        focus = request
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { fail("Audio is busy. Try again after the current call or playback."); return }
        try {
            val current = MediaPlayer()
            player = current
            current.setAudioAttributes(attributes)
            current.setVolume(0f, 0f) // Do not reveal any text until the actual route has been verified.
            if (!current.setPreferredDevice(output)) { fail("Android could not select your headset."); return }
            current.addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener {
                if (token == generation && player === current) {
                    if (routeGuard?.inspect(routedOutputs(current).map { it.id }.toSet(), HeadsetReadout.selected(this)?.id) == ReadoutRouteDecision.STOP) finishReadout()
                }
            }, handler)
            current.setOnPreparedListener {
                if (token != generation || HeadsetReadout.selected(this)?.id != output.id) { finishReadout(); return@setOnPreparedListener }
                current.start()
                verifyRoute(token, current, output.id, 0)
            }
            current.setOnCompletionListener { if (token == generation) finishReadout() }
            current.setOnErrorListener { _, _, _ -> if (token == generation) fail("Audio playback failed. Check your headset."); true }
            current.setDataSource(audioFile?.absolutePath ?: error("Speech audio is unavailable."))
            current.prepareAsync()
        } catch (_: Exception) { fail("Unable to play speech through the selected headset.") }
    }

    private fun verifyRoute(token: Int, current: MediaPlayer, outputId: Int, attempts: Int) {
        if (destroyed || token != generation || player !== current) return
        when (routeGuard?.inspect(routedOutputs(current).map { it.id }.toSet(), HeadsetReadout.selected(this)?.id)) {
            ReadoutRouteDecision.PLAY -> current.setVolume(1f, 1f)
            ReadoutRouteDecision.WAIT_MUTED -> if (attempts >= 20) fail("Android could not verify your selected headset.") else handler.postDelayed({ verifyRoute(token, current, outputId, attempts + 1) }, 50)
            else -> fail("Android did not route speech to your selected headset.")
        }
    }
    private fun routedOutputs(current: MediaPlayer): List<AudioDeviceInfo> =
        if (Build.VERSION.SDK_INT >= 36) current.routedDevices else listOfNotNull(current.routedDevice)
    private fun clearAudio() {
        routeGuard = null
        player?.let { runCatching { it.setVolume(0f, 0f); it.stop() }; it.release() }; player = null
        engine?.stop()
        focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null
        audioFile?.delete(); audioFile = null
    }
    private fun fail(message: String) { if (bodyRequest || testRequest) SmsNotifications.failure(this, message); finishReadout() }
    private fun finishReadout() { if (!destroyed) { generation++; pendingText = null; clearAudio(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } }
    override fun onBind(intent: Intent?) = null
    override fun onDestroy() {
        destroyed = true; generation++; scope.cancel(); handler.removeCallbacksAndMessages(null)
        clearAudio(); engine?.shutdown(); engine = null
        audio.unregisterAudioDeviceCallback(deviceChanges); unregisterReceiver(noisy)
        super.onDestroy()
    }
    companion object {
        const val NOTIFICATION_ID = -104
    }
}
