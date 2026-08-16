package com.aerolon.daemon

import java.util.Locale

class Brain(private val actionManager: ActionManager) {

    private val phoneticMap = mapOf(
        "ceminay" to "gemini",
        "gugıl" to "google",
        "piley" to "play",
        "vatzap" to "whatsapp",
        "vatap" to "whatsapp",
        "yutub" to "youtube",
        "inste" to "instagram",
        "ins" to "instagram",
        "twit" to "twitter",
        "tivit" to "twitter",
        "tivitır" to "twitter"
    )

    private val numberRegex = "\\d+".toRegex()

    private fun normalizeText(text: String): String {
        var normalized = text
        for ((wrong, correct) in phoneticMap) {
            normalized = normalized.replace(wrong, correct)
        }
        return normalized
    }

    private fun cleanContactPrompt(prompt: String): String {
        var clean = prompt
        val junkWords = listOf(
            "sms ekranını aç", "sms ekranı aç", "mesaj ekranını aç", "mesaj ekranı aç",
            "ekranını aç", "ekranı aç", "mesaj at", "mesaj gönder", "sms at", "sms gönder",
            "yaz", "ara", "telefon et", "çevir", "sms", "mesaj"
        )
        for (word in junkWords) {
            clean = clean.replace(word, "")
        }
        clean = clean.trim()
        if (clean.contains("'")) {
            clean = clean.substringBefore("'").trim()
        }
        return clean
    }

    fun process(prompt: String): String {
        val safePrompt = normalizeText(prompt.lowercase(Locale.forLanguageTag("tr-TR")))

        val isTurnOn = safePrompt.contains("aç") || safePrompt.contains("aktif") || safePrompt.contains("başlat") || safePrompt.contains("fulle") || safePrompt.contains("yap")
        val isTurnOff = safePrompt.contains("kapat") || safePrompt.contains("devre dışı") || safePrompt.contains("durdur") || safePrompt.contains("kıs") || safePrompt.contains("sıfırla")

        if (safePrompt.contains("google'da ara") || safePrompt.contains("internette ara")) {
            val query = safePrompt.replace("google'da ara", "").replace("internette ara", "").trim()
            actionManager.searchOnWeb(query)
            return "CHAT:İnternette aranıyor."
        }

        if (safePrompt.contains("zamanlayıcı") || (safePrompt.contains("alarm") && safePrompt.contains("dakika"))) {
            val match = numberRegex.find(safePrompt)
            if (match != null) {
                val minutes = match.value.toInt()
                actionManager.setTimer(minutes)
                return "CHAT:$minutes dakikalık zamanlayıcı kuruldu."
            }
        }

        if (safePrompt.contains("alarm")) {
            val matches = numberRegex.findAll(safePrompt).map { it.value.toInt() }.toList()
            if (matches.isNotEmpty()) {
                val hour = matches[0]
                val minute = if (matches.size > 1) matches[1] else 0
                if (hour in 0..23 && minute in 0..59) {
                    val formattedTime = String.format(Locale.forLanguageTag("tr-TR"), "%02d:%02d", hour, minute)
                    actionManager.setAlarm(hour, minute)
                    return "CHAT:Saat $formattedTime için alarm kuruldu."
                }
            }
        }

        if (safePrompt.contains("mesaj") || safePrompt.contains("sms") || safePrompt.endsWith(" yaz")) {
            val name = cleanContactPrompt(safePrompt)
            if (name.isNotEmpty()) {
                val success = actionManager.messageContact(name)
                return if (success) {
                    "CHAT:$name için mesaj ekranı açılıyor."
                } else {
                    "CHAT:Rehberde $name bulunamadı."
                }
            }
        }

        if ((safePrompt.contains("ara") || safePrompt.contains("telefon et")) && !safePrompt.contains("ekran") && !safePrompt.contains("internette") && !safePrompt.contains("google")) {
            val name = cleanContactPrompt(safePrompt)
            if (name.isNotEmpty()) {
                val success = actionManager.callContact(name)
                return if (success) {
                    "CHAT:$name aranıyor."
                } else {
                    "CHAT:Rehberde $name bulunamadı."
                }
            }
        }

        if (safePrompt.contains("usb") || safePrompt.contains("hata ayıklama")) {
            if (isTurnOn) return "ROOT:USB_DEBUG_ON"
            if (isTurnOff) return "ROOT:USB_DEBUG_OFF"
        }
        if (safePrompt.contains("pil tasarrufu") || safePrompt.contains("güç tasarrufu")) {
            if (isTurnOn) return "ROOT:BATTERY_SAVER_ON"
            if (isTurnOff) return "ROOT:BATTERY_SAVER_OFF"
        }
        if (safePrompt.contains("uçak modu")) {
            if (isTurnOn) return "ROOT:AIRPLANE_ON"
            if (isTurnOff) return "ROOT:AIRPLANE_OFF"
        }
        if (safePrompt.contains("konum") || safePrompt.contains("gps")) {
            if (isTurnOn) return "ROOT:LOCATION_ON"
            if (isTurnOff) return "ROOT:LOCATION_OFF"
        }
        if (safePrompt.contains("nfc")) {
            if (isTurnOn) return "ROOT:NFC_ON"
            if (isTurnOff) return "ROOT:NFC_OFF"
        }
        if (safePrompt.contains("rahatsız etme") || safePrompt.contains("sessiz mod")) {
            if (isTurnOn) return "ROOT:DND_ON"
            if (isTurnOff) return "ROOT:DND_OFF"
        }
        if (safePrompt.contains("parlaklık") || safePrompt.contains("ekran ışığı")) {
            if (safePrompt.contains("otomatik")) return "ROOT:BRIGHTNESS_AUTO"
            if (isTurnOn) return "ROOT:BRIGHTNESS_MAX"
            if (isTurnOff) return "ROOT:BRIGHTNESS_MIN"
        }
        if (safePrompt.contains("sesi")) {
            if (isTurnOn) return "ROOT:VOLUME_MAX"
            if (isTurnOff) return "ROOT:VOLUME_MUTE"
        }
        if (safePrompt.contains("müzik") || safePrompt.contains("şarkı")) {
            if (safePrompt.contains("sonraki") || safePrompt.contains("geç")) return "ROOT:MEDIA_NEXT"
            if (safePrompt.contains("önceki") || safePrompt.contains("geri")) return "ROOT:MEDIA_PREV"
            if (isTurnOn || isTurnOff) return "ROOT:MEDIA_PLAY_PAUSE"
        }

        if (safePrompt.contains("ana ekran") || safePrompt.contains("ana ekrana dön")) return "ROOT:NAV_HOME"
        if (safePrompt.contains("geri git") || safePrompt.contains("geri gel")) return "ROOT:NAV_BACK"
        if (safePrompt.contains("son uygulamalar") || safePrompt.contains("arka plan")) return "ROOT:NAV_RECENTS"
        if (safePrompt.contains("ekran görüntüsü") || safePrompt.contains("ss al")) return "ROOT:SCREENSHOT"

        if (safePrompt.contains("mobil veri") || safePrompt.contains("internet")) {
            if (isTurnOn) return "ROOT:DATA_ON"
            if (isTurnOff) return "ROOT:DATA_OFF"
        }
        if (safePrompt.contains("wifi") || safePrompt.contains("wi-fi")) {
            if (isTurnOn) return "ROOT:WIFI_ON"
            if (isTurnOff) return "ROOT:WIFI_OFF"
        }
        if (safePrompt.contains("bluetooth")) {
            if (isTurnOn) return "ROOT:BLUETOOTH_ON"
            if (isTurnOff) return "ROOT:BLUETOOTH_OFF"
        }
        if (safePrompt.contains("yeniden başlat")) {
            return "ROOT:REBOOT"
        }
        if (safePrompt.contains("ekranı kilitle") || safePrompt.contains("ekranı kapat")) {
            return "ROOT:LOCK"
        }

        val isSettingsRequest = safePrompt.contains("ayarı") || safePrompt.contains("ayarlar") || safePrompt.contains("menü")

        if (isSettingsRequest || safePrompt.contains("seçenekleri") || safePrompt.contains("modu")) {
            val settingName = actionManager.tryOpenSettingDynamic(safePrompt)
            if (settingName != null) {
                return "DYN_SETTING:$settingName"
            }
        }

        if (safePrompt.contains("aç") || safePrompt.contains("başlat") || safePrompt.contains("gir")) {
            val appName = actionManager.tryOpenAppDynamic(safePrompt)
            if (appName != null) {
                return "DYN_APP:$appName"
            }
        }

        return "CHAT:Bu komutu henüz nasıl yapacağımı bilmiyorum."
    }
}