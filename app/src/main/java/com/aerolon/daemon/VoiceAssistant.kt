package com.aerolon.daemon

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceAssistant(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onErrorCallback: (String) -> Unit,
    private val onVolumeChanged: (Float) -> Unit
) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private var tts: TextToSpeech = TextToSpeech(context, this)

    init {
        speechRecognizer.setRecognitionListener(this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("tr", "TR")
        }
    }

    fun startListening() {
        speechRecognizer.cancel()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
        }
        speechRecognizer.startListening(intent)
    }

    fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            onResult(matches[0])
        } else {
            onErrorCallback("Seni tam anlayamadım.")
        }
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Ses kaydedilemedi."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni gerekli."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Ağ hatası oluştu."
            SpeechRecognizer.ERROR_NO_MATCH -> "Anlaşılmadı."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ses algılanmadı."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Mikrofon şu an meşgul."
            SpeechRecognizer.ERROR_CLIENT -> "İstemci tarafında bir sorun oluştu."
            SpeechRecognizer.ERROR_SERVER -> "Sunucu bağlantısı kurulamadı."
            else -> "Bir hata oluştu."
        }
        onErrorCallback(message)
    }

    override fun onRmsChanged(rmsdB: Float) {
        onVolumeChanged(rmsdB)
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        speechRecognizer.destroy()
        tts.stop()
        tts.shutdown()
    }
}