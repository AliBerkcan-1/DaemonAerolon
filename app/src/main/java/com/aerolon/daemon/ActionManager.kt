package com.aerolon.daemon

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.net.toUri
import java.util.Locale
import kotlin.math.abs

class ActionManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val spaceRegex = "\\s".toRegex()
    private val cleanRegex = "[^a-z0-9]".toRegex()

    @Volatile
    private var appCache: Map<String, String>? = null

    private val settingsMap = mapOf(
        "geliştirici" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        "erişilebilirlik" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "ekran" to Settings.ACTION_DISPLAY_SETTINGS,
        "pil" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "konum" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "nfc" to Settings.ACTION_NFC_SETTINGS,
        "uçak" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
        "ses" to Settings.ACTION_SOUND_SETTINGS,
        "uygulama" to Settings.ACTION_APPLICATION_SETTINGS,
        "tarih" to Settings.ACTION_DATE_SETTINGS,
        "saat" to Settings.ACTION_DATE_SETTINGS,
        "depolama" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "wi-fi" to Settings.ACTION_WIFI_SETTINGS,
        "ağ" to Settings.ACTION_WIRELESS_SETTINGS,
        "dil" to Settings.ACTION_LOCALE_SETTINGS,
        "klavye" to Settings.ACTION_INPUT_METHOD_SETTINGS,
        "güvenlik" to Settings.ACTION_SECURITY_SETTINGS,
        "gizlilik" to Settings.ACTION_PRIVACY_SETTINGS,
        "şifre" to Settings.ACTION_SECURITY_SETTINGS,
        "karanlık" to Settings.ACTION_DISPLAY_SETTINGS,
        "hesaplar" to Settings.ACTION_SYNC_SETTINGS,
        "hakkında" to Settings.ACTION_DEVICE_INFO_SETTINGS,
        "bildirim" to Settings.ACTION_APP_NOTIFICATION_SETTINGS
    )

    private fun buildAppCache(): Map<String, String> {
        val tempCache = mutableMapOf<String, String>()
        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (appInfo in packages) {
            val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (launchIntent != null) {
                val appName = packageManager.getApplicationLabel(appInfo).toString().lowercase(Locale.forLanguageTag("tr-TR"))
                tempCache[appName] = appInfo.packageName
            }
        }
        return tempCache
    }

    fun warmUpCache() {
        if (appCache == null) {
            appCache = buildAppCache()
        }
    }

    fun invalidateCache() {
        appCache = null
    }

    private fun getAppCache(): Map<String, String> {
        return appCache ?: emptyMap()
    }

    fun tryOpenAppDynamic(spokenText: String): String? {
        val cache = getAppCache()
        var bestMatch: String? = null
        var bestMatchLength = -1
        var bestPackage: String? = null

        for ((appName, packageName) in cache) {
            if (spokenText.contains(appName)) {
                if (appName.length > bestMatchLength) {
                    bestMatch = appName
                    bestMatchLength = appName.length
                    bestPackage = packageName
                }
            }
        }

        if (bestMatch == null) {
            val cleanSpoken = spokenText.replace(spaceRegex, "")
            for ((appName, packageName) in cache) {
                val cleanAppName = appName.replace(spaceRegex, "")
                if (cleanSpoken.contains(cleanAppName)) {
                    if (cleanAppName.length > bestMatchLength) {
                        bestMatch = appName
                        bestMatchLength = cleanAppName.length
                        bestPackage = packageName
                    }
                }
            }
        }

        if (bestPackage != null) {
            val intent = packageManager.getLaunchIntentForPackage(bestPackage)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return bestMatch
        }
        return null
    }

    fun tryOpenSettingDynamic(spokenText: String): String? {
        for ((key, action) in settingsMap) {
            if (spokenText.contains(key)) {
                try {
                    val intent = Intent(action).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return key
                } catch (_: Exception) {
                    return null
                }
            }
        }
        return null
    }

    fun searchOnWeb(query: String) {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun setTimer(minutes: Int) {
        try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun setAlarm(hour: Int, minute: Int): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun normalizeForSearch(text: String): String {
        return text.lowercase(Locale.forLanguageTag("tr-TR"))
            .replace("ç", "c").replace("ğ", "g")
            .replace("ı", "i").replace("i̇", "i")
            .replace("ö", "o").replace("ş", "s")
            .replace("ü", "u")
            .replace(cleanRegex, "")
    }

    private fun findContactNumber(spokenName: String): String? {
        val cleanSpoken = normalizeForSearch(spokenName)
        if (cleanSpoken.isEmpty()) return null

        var bestMatchNumber: String? = null
        var bestScore = Int.MAX_VALUE

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val contactName = it.getString(nameIndex) ?: continue
                    val contactNumber = it.getString(numberIndex) ?: continue

                    val cleanContact = normalizeForSearch(contactName)
                    if (cleanContact.isEmpty()) continue

                    if (cleanContact == cleanSpoken) {
                        return contactNumber
                    }

                    if (cleanContact.startsWith(cleanSpoken) || cleanSpoken.startsWith(cleanContact)) {
                        val diff = abs(cleanContact.length - cleanSpoken.length)
                        if (diff < bestScore) {
                            bestScore = diff
                            bestMatchNumber = contactNumber
                        }
                    } else if (cleanContact.contains(cleanSpoken)) {
                        val diff = abs(cleanContact.length - cleanSpoken.length) + 10
                        if (diff < bestScore) {
                            bestScore = diff
                            bestMatchNumber = contactNumber
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return bestMatchNumber
    }

    fun callContact(name: String): Boolean {
        val number = findContactNumber(name)
        if (number != null) {
            try {
                val intent = Intent(Intent.ACTION_CALL, "tel:$number".toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun messageContact(name: String): Boolean {
        val number = findContactNumber(name)
        if (number != null) {
            try {
                val intent = Intent(Intent.ACTION_SENDTO, "smsto:$number".toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }
}