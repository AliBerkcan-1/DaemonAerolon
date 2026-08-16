package com.aerolon.daemon

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream

object RootUtils {
    private var originalSoundEffects = 1
    private var originalSystemVol = 7
    private var originalMusicVol = 7
    private var originalNotifVol = 7

    private var rootProcess: Process? = null
    private var rootOutputStream: DataOutputStream? = null

    fun initShell() {
        if (rootProcess == null) {
            try {
                rootProcess = Runtime.getRuntime().exec("su")
                rootOutputStream = DataOutputStream(rootProcess!!.outputStream)
            } catch (e: Exception) {
            }
        }
    }

    fun closeShell() {
        try {
            rootOutputStream?.writeBytes("exit\n")
            rootOutputStream?.flush()
            rootProcess?.waitFor()
        } catch (e: Exception) {
        } finally {
            rootOutputStream?.close()
            rootOutputStream = null
            rootProcess = null
        }
    }

    suspend fun executeCommand(command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (rootOutputStream == null) {
                initShell()
            }
            rootOutputStream?.writeBytes("$command\n")
            rootOutputStream?.flush()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun hideNotification() = executeCommand("appops set com.aerolon.daemon POST_NOTIFICATION ignore")
    suspend fun disableWifi() = executeCommand("svc wifi disable")
    suspend fun enableWifi() = executeCommand("svc wifi enable")
    suspend fun enableMobileData() = executeCommand("svc data enable")
    suspend fun disableMobileData() = executeCommand("svc data disable")
    suspend fun enableBluetooth() = executeCommand("svc bluetooth enable")
    suspend fun disableBluetooth() = executeCommand("svc bluetooth disable")
    suspend fun rebootDevice() = executeCommand("reboot")
    suspend fun lockScreen() = executeCommand("input keyevent 26")

    suspend fun enableUsbDebugging() = executeCommand("settings put global adb_enabled 1")
    suspend fun disableUsbDebugging() = executeCommand("settings put global adb_enabled 0")
    suspend fun enableBatterySaver() = executeCommand("settings put global low_power 1")
    suspend fun disableBatterySaver() = executeCommand("settings put global low_power 0")
    suspend fun enableAirplane() {
        executeCommand("settings put global airplane_mode_on 1")
        executeCommand("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true")
    }
    suspend fun disableAirplane() {
        executeCommand("settings put global airplane_mode_on 0")
        executeCommand("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false")
    }
    suspend fun enableLocation() = executeCommand("settings put secure location_mode 3")
    suspend fun disableLocation() = executeCommand("settings put secure location_mode 0")
    suspend fun enableNfc() = executeCommand("svc nfc enable")
    suspend fun disableNfc() = executeCommand("svc nfc disable")
    suspend fun enableDnd() = executeCommand("settings put global zen_mode 1")
    suspend fun disableDnd() = executeCommand("settings put global zen_mode 0")

    suspend fun setBrightnessMax() {
        executeCommand("settings put system screen_brightness_mode 0")
        executeCommand("settings put system screen_brightness 255")
    }
    suspend fun setBrightnessMin() {
        executeCommand("settings put system screen_brightness_mode 0")
        executeCommand("settings put system screen_brightness 0")
    }
    suspend fun setAutoBrightness() = executeCommand("settings put system screen_brightness_mode 1")

    suspend fun pressHome() = executeCommand("input keyevent 3")
    suspend fun pressBack() = executeCommand("input keyevent 4")
    suspend fun pressRecents() = executeCommand("input keyevent 187")
    suspend fun takeScreenshot() = executeCommand("input keyevent 120")

    suspend fun mediaPlayPause() = executeCommand("input keyevent 85")
    suspend fun mediaNext() = executeCommand("input keyevent 87")
    suspend fun mediaPrev() = executeCommand("input keyevent 88")

    suspend fun volumeMax() = executeCommand("media volume --show --stream 3 --set 15")
    suspend fun volumeMute() = executeCommand("media volume --show --stream 3 --set 0")

    suspend fun muteSystemBip(context: Context) = withContext(Dispatchers.IO) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        originalSoundEffects = Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1)
        originalSystemVol = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
        originalMusicVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        originalNotifVol = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)

        executeCommand("settings put system sound_effects_enabled 0")
        executeCommand("media volume --stream 1 --set 0")
        executeCommand("media volume --stream 5 --set 0")
    }

    suspend fun unmuteSystemBip() = withContext(Dispatchers.IO) {
        executeCommand("settings put system sound_effects_enabled $originalSoundEffects")
        executeCommand("media volume --stream 1 --set $originalSystemVol")
        executeCommand("media volume --stream 5 --set $originalNotifVol")
    }
}