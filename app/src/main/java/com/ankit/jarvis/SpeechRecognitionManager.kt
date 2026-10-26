/*
 * Copyright ©️ 2026 Ankit Gothwal.
 * All rights reserved, subject to applicable third-party license terms.
 *
 * Project: JARVIS AI
 * Developer: Ankit Gothwal
 * Application ID: com.ankit.jarvis
 */
package com.ankit.jarvis

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener as VoskRecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/**
 * Manages offline-first voice input for JARVIS AI.
 *
 * Supports two on-device recognition pathways:
 * 1. Primary (100% Air-Gapped Offline): Vosk Speech Recognition (`org.vosk`).
 *    Reads pre-downloaded Vosk acoustic models for English (`vosk-model-small-en-in-0.4`) or
 *    Hindi (`vosk-model-small-hi-0.22`) from `/sdcard/Android/data/com.ankit.jarvis/files/vosk-models/`.
 * 2. Fallback: Android System `SpeechRecognizer` configured with `EXTRA_PREFER_OFFLINE = true`.
 *
 * Includes strict error handling for silence, missing models, unsupported languages, and permissions.
 * Never performs hidden recording or unauthorized audio transmission.
 */
class SpeechRecognitionManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val callback: Listener
) {

    interface Listener {
        fun onListeningStateChanged(isListening: Boolean, engineLabel: String)
        fun onPartialTranscript(partialText: String)
        fun onFinalTranscript(finalText: String)
        fun onSpeechError(userFriendlyError: String)
        fun onAudioLevelUpdate(normalizedLevel: Float)
    }

    private var voskModel: Model? = null
    private var voskSpeechService: SpeechService? = null
    private var androidSpeechRecognizer: SpeechRecognizer? = null
    private var isCurrentlyListening = false
    private var loadedLanguageCode: String = ""

    companion object {
        const val FOLDER_EN_IN = "vosk-model-small-en-in"
        const val FOLDER_HI = "vosk-model-small-hi"
    }

    /**
     * Returns the local directory where Vosk offline speech models should be extracted.
     */
    fun getVoskModelsRootDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "vosk-models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Checks whether a local Vosk model folder exists for the given language ("en-IN" or "hi-IN").
     */
    fun getLocalVoskModelDir(languageCode: String): File? {
        val root = getVoskModelsRootDir()
        val folderName = if (languageCode.startsWith("hi", ignoreCase = true)) FOLDER_HI else FOLDER_EN_IN
        val target = File(root, folderName)
        // A valid Vosk model directory contains conf/ and am/ or final.mdl
        if (target.exists() && target.isDirectory && (File(target, "conf").exists() || File(target, "am").exists() || File(target, "final.mdl").exists())) {
            return target
        }
        return null
    }

    /**
     * Starts listening for a single voice command using Vosk (if local model is installed)
     * or Android's on-device SpeechRecognizer with EXTRA_PREFER_OFFLINE.
     */
    fun startListening(languageCode: String) {
        if (!PermissionManager.hasAudioPermission(context)) {
            callback.onSpeechError("Microphone permission is not granted. Please grant permission or use the text box below.")
            return
        }

        if (isCurrentlyListening) {
            stopListening()
            return
        }

        val localVoskDir = getLocalVoskModelDir(languageCode)
        if (localVoskDir != null) {
            startVoskListening(localVoskDir, languageCode)
        } else {
            startAndroidOfflineRecognizer(languageCode)
        }
    }

    private fun startVoskListening(modelDir: File, languageCode: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (voskModel == null || loadedLanguageCode != languageCode) {
                    voskModel?.close()
                    voskModel = Model(modelDir.absolutePath)
                    loadedLanguageCode = languageCode
                }

                val recognizer = Recognizer(voskModel, 16000.0f)
                val service = SpeechService(recognizer, 16000.0f)
                voskSpeechService = service

                withContext(Dispatchers.Main) {
                    isCurrentlyListening = true
                    callback.onListeningStateChanged(true, "VOSK OFFLINE ASR ($languageCode)")
                    service.startListening(object : VoskRecognitionListener {
                        override fun onPartialResult(hypothesis: String?) {
                            val partial = parseVoskJson(hypothesis, "partial")
                            if (partial.isNotEmpty()) {
                                callback.onPartialTranscript(partial)
                                callback.onAudioLevelUpdate(0.65f)
                            }
                        }

                        override fun onResult(hypothesis: String?) {
                            val text = parseVoskJson(hypothesis, "text")
                            if (text.isNotEmpty()) {
                                stopListening()
                                callback.onFinalTranscript(text)
                            }
                        }

                        override fun onFinalResult(hypothesis: String?) {
                            val text = parseVoskJson(hypothesis, "text")
                            stopListening()
                            if (text.isNotEmpty()) {
                                callback.onFinalTranscript(text)
                            } else {
                                callback.onSpeechError("No speech detected. Tap the microphone to try again or type your command.")
                            }
                        }

                        override fun onError(exception: Exception?) {
                            stopListening()
                            callback.onSpeechError("Offline Vosk speech error: ${exception?.localizedMessage ?: "Unknown error"}")
                        }

                        override fun onTimeout() {
                            stopListening()
                            callback.onSpeechError("Listening timed out due to silence.")
                        }
                    })
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isCurrentlyListening = false
                    callback.onListeningStateChanged(false, "ASR ERROR")
                    callback.onSpeechError(
                        "Failed to load Vosk model from ${modelDir.name}: ${e.localizedMessage}. Falling back to Android speech recognizer."
                    )
                    startAndroidOfflineRecognizer(languageCode)
                }
            }
        }
    }

    private fun startAndroidOfflineRecognizer(languageCode: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            val voskPath = getVoskModelsRootDir().absolutePath
            callback.onSpeechError(
                "No system speech engine found and no local Vosk model installed yet. Extract '$FOLDER_EN_IN' or '$FOLDER_HI' into $voskPath, or use the text input bar."
            )
            return
        }

        try {
            androidSpeechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            androidSpeechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isCurrentlyListening = true
                    callback.onListeningStateChanged(true, "ANDROID OFFLINE ASR ($languageCode)")
                }

                override fun onBeginningOfSpeech() {
                    callback.onAudioLevelUpdate(0.5f)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                    callback.onAudioLevelUpdate(normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isCurrentlyListening = false
                    callback.onListeningStateChanged(false, "PROCESSING SPEECH...")
                }

                override fun onError(error: Int) {
                    isCurrentlyListening = false
                    callback.onListeningStateChanged(false, "READY")
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "No speech detected. Please speak clearly into the microphone or type your command below."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Offline voice pack for '$languageCode' is missing in Android Settings, and no local Vosk model was found in ${getVoskModelsRootDir().name}."
                        SpeechRecognizer.ERROR_AUDIO ->
                            "Microphone audio capture error. Check if another app is using the microphone."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone permission is required for voice commands."
                        else ->
                            "Speech recognition could not complete (code $error). Place a Vosk offline model in ${getVoskModelsRootDir().absolutePath} for 100% offline speech."
                    }
                    callback.onSpeechError(message)
                }

                override fun onResults(results: Bundle?) {
                    isCurrentlyListening = false
                    callback.onListeningStateChanged(false, "READY")
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty()
                    if (best.isNotEmpty()) {
                        callback.onFinalTranscript(best)
                    } else {
                        callback.onSpeechError("Could not recognize speech. Please try again.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim().orEmpty()
                    if (partial.isNotEmpty()) {
                        callback.onPartialTranscript(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
        } catch (e: Exception) {
            isCurrentlyListening = false
            callback.onListeningStateChanged(false, "READY")
            callback.onSpeechError("Unable to start speech recognition: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        isCurrentlyListening = false
        try {
            voskSpeechService?.stop()
            voskSpeechService?.shutdown()
            voskSpeechService = null
        } catch (_: Exception) {
        }
        try {
            androidSpeechRecognizer?.stopListening()
            androidSpeechRecognizer?.cancel()
        } catch (_: Exception) {
        }
        callback.onAudioLevelUpdate(0f)
        callback.onListeningStateChanged(false, "READY")
    }

    fun destroy() {
        stopListening()
        try {
            voskModel?.close()
            voskModel = null
        } catch (_: Exception) {
        }
        try {
            androidSpeechRecognizer?.destroy()
            androidSpeechRecognizer = null
        } catch (_: Exception) {
        }
    }

    private fun parseVoskJson(jsonStr: String?, key: String): String {
        if (jsonStr.isNullOrBlank()) return ""
        return try {
            val obj = JSONObject(jsonStr)
            obj.optString(key, "").trim()
        } catch (_: Exception) {
            ""
        }
    }
}
