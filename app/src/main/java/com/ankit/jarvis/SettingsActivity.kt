/*
 * Copyright ©️ 2026 Ankit Gothwal.
 * All rights reserved, subject to applicable third-party license terms.
 *
 * Project: JARVIS AI
 * Developer: Ankit Gothwal
 * Application ID: com.ankit.jarvis
 */
package com.ankit.jarvis

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ankit.jarvis.databinding.ActivitySettingsBinding
import java.util.Locale

/**
 * Preferences & Offline Configuration Activity for JARVIS AI.
 * Created and developed by Ankit Gothwal.
 *
 * Allows configuring:
 * - Voice Recognition & TTS Language (`en-IN` English / `hi-IN` Hindi & Hinglish)
 * - Speech Rate (0.5x to 2.0x)
 * - Installing Offline Android TTS Voice Data
 * - Viewing Local Vosk & GGUF Model Directory Paths
 * - Accessible HUD Theme Accent Selection (Electric Cyan, Cobalt Blue, High Contrast)
 * - Optional Creator Footer Credit toggle
 * - One-Tap Wipe of Local Conversation History & Saved Notes
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var memoryManager: LocalMemoryManager
    private lateinit var localAIEngine: LocalAIEngine
    private lateinit var speechManager: SpeechRecognitionManager
    private lateinit var ttsManager: TextToSpeechManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        memoryManager = LocalMemoryManager(this)
        localAIEngine = LocalAIEngine(this, memoryManager)
        speechManager = SpeechRecognitionManager(this, kotlinx.coroutines.MainScope(), object : SpeechRecognitionManager.Listener {
            override fun onListeningStateChanged(isListening: Boolean, engineLabel: String) {}
            override fun onPartialTranscript(partialText: String) {}
            override fun onFinalTranscript(finalText: String) {}
            override fun onSpeechError(userFriendlyError: String) {}
            override fun onAudioLevelUpdate(normalizedLevel: Float) {}
        })
        ttsManager = TextToSpeechManager(this, memoryManager) { _, _ -> }

        populateCurrentSettings()
        setupListeners()
    }

    private fun populateCurrentSettings() {
        // Language Selection
        when (memoryManager.preferredLanguage) {
            "hi-IN" -> binding.rbLangHindi.isChecked = true
            else -> binding.rbLangEnglish.isChecked = true
        }

        // Speech Rate Slider (50 to 200 representing 0.5x to 2.0x)
        val progress = ((memoryManager.speechRate - 0.5f) * 100f).toInt().coerceIn(0, 150)
        binding.seekSpeechRate.max = 150
        binding.seekSpeechRate.progress = progress
        binding.tvSpeechRateValue.text = String.format(Locale.US, "Speech Rate: %.2fx", memoryManager.speechRate)

        // Theme Accent
        when (memoryManager.themeAccent) {
            "BLUE" -> binding.rbThemeBlue.isChecked = true
            "HIGH_CONTRAST" -> binding.rbThemeHighContrast.isChecked = true
            else -> binding.rbThemeCyan.isChecked = true
        }

        // Footer Credit Switch
        binding.switchFooterCredit.isChecked = memoryManager.showFooterCredit

        // Display Local Model Paths & Free RAM Diagnostics
        val voskDir = speechManager.getVoskModelsRootDir().absolutePath
        val ggufDir = localAIEngine.getModelsDirectory().absolutePath
        val freeRam = localAIEngine.getAvailableRamMb()
        val totalRam = localAIEngine.getTotalRamMb()

        binding.tvModelDirectoriesInfo.text = """
            Device Memory: $freeRam MB Free / $totalRam MB Total RAM
            
            Offline GGUF LLM Folder:
            $ggufDir
            (Place qwen2.5-0.5b-instruct-q4_k_m.gguf here)
            
            Offline Vosk Speech Folder:
            $voskDir
            (Extract vosk-model-small-en-in or vosk-model-small-hi here)
        """.trimIndent()
    }

    private fun setupListeners() {
        binding.btnBackFromSettings.setOnClickListener {
            finish()
        }

        binding.rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val lang = if (checkedId == R.id.rbLangHindi) "hi-IN" else "en-IN"
            memoryManager.preferredLanguage = lang
            val status = ttsManager.applyLanguageAndRate(lang, memoryManager.speechRate)
            Toast.makeText(this, status, Toast.LENGTH_LONG).show()
        }

        binding.seekSpeechRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val rate = 0.5f + (progress / 100f)
                memoryManager.speechRate = rate
                binding.tvSpeechRateValue.text = String.format(Locale.US, "Speech Rate: %.2fx", rate)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                ttsManager.applyLanguageAndRate(memoryManager.preferredLanguage, memoryManager.speechRate)
            }
        })

        binding.btnInstallOfflineTts.setOnClickListener {
            ttsManager.promptInstallVoiceData(this)
        }

        binding.rgThemeAccent.setOnCheckedChangeListener { _, checkedId ->
            val accent = when (checkedId) {
                R.id.rbThemeBlue -> "BLUE"
                R.id.rbThemeHighContrast -> "HIGH_CONTRAST"
                else -> "CYAN"
            }
            memoryManager.themeAccent = accent
        }

        binding.switchFooterCredit.setOnCheckedChangeListener { _, isChecked ->
            memoryManager.showFooterCredit = isChecked
        }

        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_JarvisAI_Dialog)
                .setTitle("Delete All Local History & Notes?")
                .setMessage("This will permanently wipe all saved conversations and local notes from your device's internal SQLite database.")
                .setPositiveButton("Delete Everything") { _, _ ->
                    memoryManager.wipeAllMemory()
                    Toast.makeText(this, "All local conversations and notes deleted.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onDestroy() {
        speechManager.destroy()
        ttsManager.shutdown()
        super.onDestroy()
    }
}
