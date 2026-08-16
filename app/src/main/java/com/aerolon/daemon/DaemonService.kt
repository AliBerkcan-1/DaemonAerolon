package com.aerolon.daemon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Duration.Companion.milliseconds

class DaemonService : Service() {

    companion object {
        var isRunning = false
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: CyberOverlayView? = null

    private var voiceAssistant: VoiceAssistant? = null
    private lateinit var brain: Brain
    private lateinit var actionManager: ActionManager

    private var triggerSoundPlayer: MediaPlayer? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            actionManager.invalidateCache()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        actionManager = ActionManager(this)
        brain = Brain(actionManager)

        RootUtils.initShell()

        serviceScope.launch {
            RootUtils.hideNotification()
            actionManager.warmUpCache()
        }

        registerPackageChangeReceiver()
        preloadTriggerSound()
        startForegroundNotification()
    }

    private fun registerPackageChangeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageChangeReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_TRIGGER_ASSISTANT") {
            if (overlayView == null) {
                triggerAssistant()
            }
        }
        return START_STICKY
    }

    private fun triggerAssistant() {
        serviceScope.launch {
            RootUtils.muteSystemBip(this@DaemonService)

            withContext(Dispatchers.Main) {
                launchOverlay()
                initVoiceAssistant()
                playSound {
                    voiceAssistant?.startListening()
                }
            }
        }
    }

    private fun initVoiceAssistant() {
        if (voiceAssistant == null) {
            voiceAssistant = VoiceAssistant(
                context = this,
                onResult = { spokenText ->
                    processCommand(spokenText)
                },
                onErrorCallback = { errorMsg ->
                    overlayView?.updateText(errorMsg)
                    closeOverlay()
                },
                onVolumeChanged = { rmsdB ->
                    overlayView?.updateVolume(rmsdB)
                }
            )
        }
    }

    private fun destroyVoiceAssistant() {
        voiceAssistant?.destroy()
        voiceAssistant = null
    }

    @Suppress("DEPRECATION")
    private fun launchOverlay() {
        overlayView = CyberOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(overlayView, params)
        overlayView?.startAnimation()
    }

    private fun preloadTriggerSound() {
        triggerSoundPlayer = try {
            MediaPlayer.create(this, R.raw.trigger_sound).apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun playSound(onSoundComplete: () -> Unit) {
        val player = triggerSoundPlayer
        if (player == null) {
            onSoundComplete()
            return
        }
        try {
            if (player.isPlaying) {
                player.pause()
            }
            player.setOnCompletionListener {
                onSoundComplete()
            }
            player.seekTo(0)
            player.start()
        } catch (_: Exception) {
            onSoundComplete()
        }
    }

    private fun processCommand(text: String) {
        val response = brain.process(text)

        when {
            response.startsWith("ROOT:") -> {
                val rootCommand = response.removePrefix("ROOT:")
                executeRootAction(rootCommand)
            }
            response.startsWith("DYN_APP:") -> {
                val app = response.removePrefix("DYN_APP:")
                overlayView?.updateText("$app açılıyor")
                voiceAssistant?.speak("$app açılıyor.")
                closeOverlay()
            }
            response.startsWith("DYN_SETTING:") -> {
                val setting = response.removePrefix("DYN_SETTING:")
                overlayView?.updateText("$setting ayarları")
                voiceAssistant?.speak("$setting ayarları açılıyor.")
                closeOverlay()
            }
            else -> {
                val reply = response.removePrefix("CHAT:")
                overlayView?.updateText(reply)
                voiceAssistant?.speak(reply)
                closeOverlay()
            }
        }
    }

    private fun executeRootAction(action: String) {
        serviceScope.launch {
            when (action) {
                "WIFI_OFF" -> RootUtils.disableWifi()
                "WIFI_ON" -> RootUtils.enableWifi()
                "DATA_ON" -> RootUtils.enableMobileData()
                "DATA_OFF" -> RootUtils.disableMobileData()
                "BLUETOOTH_ON" -> RootUtils.enableBluetooth()
                "BLUETOOTH_OFF" -> RootUtils.disableBluetooth()
                "REBOOT" -> RootUtils.rebootDevice()
                "LOCK" -> RootUtils.lockScreen()
                "USB_DEBUG_ON" -> RootUtils.enableUsbDebugging()
                "USB_DEBUG_OFF" -> RootUtils.disableUsbDebugging()
                "BATTERY_SAVER_ON" -> RootUtils.enableBatterySaver()
                "BATTERY_SAVER_OFF" -> RootUtils.disableBatterySaver()
                "AIRPLANE_ON" -> RootUtils.enableAirplane()
                "AIRPLANE_OFF" -> RootUtils.disableAirplane()
                "LOCATION_ON" -> RootUtils.enableLocation()
                "LOCATION_OFF" -> RootUtils.disableLocation()
                "NFC_ON" -> RootUtils.enableNfc()
                "NFC_OFF" -> RootUtils.disableNfc()
                "DND_ON" -> RootUtils.enableDnd()
                "DND_OFF" -> RootUtils.disableDnd()
                "BRIGHTNESS_MAX" -> RootUtils.setBrightnessMax()
                "BRIGHTNESS_MIN" -> RootUtils.setBrightnessMin()
                "BRIGHTNESS_AUTO" -> RootUtils.setAutoBrightness()
                "NAV_HOME" -> RootUtils.pressHome()
                "NAV_BACK" -> RootUtils.pressBack()
                "NAV_RECENTS" -> RootUtils.pressRecents()
                "MEDIA_PLAY_PAUSE" -> RootUtils.mediaPlayPause()
                "MEDIA_NEXT" -> RootUtils.mediaNext()
                "MEDIA_PREV" -> RootUtils.mediaPrev()
                "VOLUME_MAX" -> RootUtils.volumeMax()
                "VOLUME_MUTE" -> RootUtils.volumeMute()
                "SCREENSHOT" -> RootUtils.takeScreenshot()
            }
            withContext(Dispatchers.Main) {
                overlayView?.updateText("İşlem Tamamlandı")
                closeOverlay()
            }
        }
    }

    private fun closeOverlay() {
        serviceScope.launch {
            delay(1500.milliseconds)
            RootUtils.unmuteSystemBip()
            withContext(Dispatchers.Main) {
                try {
                    overlayView?.let { windowManager.removeView(it) }
                } catch (_: Exception) {
                } finally {
                    overlayView = null
                    destroyVoiceAssistant()
                }
            }
        }
    }

    private fun startForegroundNotification() {
        val channel = NotificationChannel("daemon_core", "Asistan", NotificationManager.IMPORTANCE_MIN)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, "daemon_core")
            .setContentTitle("Daemon")
            .setContentText("Aktif")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        destroyVoiceAssistant()
        RootUtils.closeShell()
        triggerSoundPlayer?.release()
        triggerSoundPlayer = null
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (_: IllegalArgumentException) {
        }
        isRunning = false
    }
}