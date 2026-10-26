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
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result of attempting to parse and execute a safe device command.
 */
data class CommandResult(
    val handled: Boolean,
    val spokenResponse: String,
    val badge: String = "DEVICE CMD"
)

/**
 * Executes safe, user-authorized Android phone actions using official Android Intents and APIs.
 * Supports English and Hindi/Hinglish voice and text commands.
 * Does NOT use hidden background actions or bypass Android security boundaries.
 */
class CommandExecutor(
    private val context: Context,
    private val memoryManager: LocalMemoryManager
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Evaluates user input for deterministic device control commands before invoking the LLM.
     */
    fun processCommand(rawInput: String): CommandResult {
        val input = rawInput.trim()
        val lower = input.lowercase(Locale.ROOT)

        if (lower.isEmpty()) {
            return CommandResult(false, "")
        }

        // 1. Creator & Ownership Queries
        if (lower.contains("who created you") ||
            lower.contains("who made you") ||
            lower.contains("your creator") ||
            lower.contains("your developer") ||
            lower.contains("tumhe kisne banaya") ||
            lower.contains("aapko kisne banaya") ||
            lower.contains("who owns you")
        ) {
            return CommandResult(
                handled = true,
                spokenResponse = "I am JARVIS, an independent personal AI assistant created and developed by Ankit Gothwal.",
                badge = "IDENTITY"
            )
        }

        // 2. Current Time Queries (English + Hindi/Hinglish)
        if (lower.contains("what time") ||
            lower.contains("current time") ||
            lower.contains("tell me the time") ||
            lower == "time" ||
            lower.contains("kya time") ||
            lower.contains("samay kya") ||
            lower.contains("kitne baje")
        ) {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val timeStr = sdf.format(Date())
            return CommandResult(
                handled = true,
                spokenResponse = "The current time is $timeStr.",
                badge = "SYSTEM CLOCK"
            )
        }

        // 3. Current Date & Day Queries (English + Hindi/Hinglish)
        if (lower.contains("what date") ||
            lower.contains("today's date") ||
            lower.contains("what day is it") ||
            lower == "date" ||
            lower.contains("aaj kya tareekh") ||
            lower.contains("aaj kaun sa din")
        ) {
            val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
            val dateStr = sdf.format(Date())
            return CommandResult(
                handled = true,
                spokenResponse = "Today is $dateStr.",
                badge = "SYSTEM CLOCK"
            )
        }

        // 4. Battery Status Check
        if (lower.contains("battery") || lower.contains("charge level") || lower.contains("battery kitni hai")) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return CommandResult(
                handled = true,
                spokenResponse = "Your device battery is currently at $pct percent.",
                badge = "TELEMETRY"
            )
        }

        // 5. Camera Launch (English + Hinglish)
        if (lower.contains("open camera") ||
            lower.contains("launch camera") ||
            lower.contains("take a photo") ||
            lower.contains("take a picture") ||
            lower.contains("camera kholo") ||
            lower.contains("photo khicho")
        ) {
            return launchCamera()
        }

        // 6. Volume Controls (Increase / Decrease / Mute / Max)
        if (lower.contains("volume up") ||
            lower.contains("increase volume") ||
            lower.contains("raise volume") ||
            lower.contains("louder") ||
            lower.contains("awaz badhao") ||
            lower.contains("volume badhao")
        ) {
            return adjustMediaVolume(AudioManager.ADJUST_RAISE, "Increasing media volume.")
        }

        if (lower.contains("volume down") ||
            lower.contains("decrease volume") ||
            lower.contains("lower volume") ||
            lower.contains("quieter") ||
            lower.contains("awaz kam karo") ||
            lower.contains("volume kam karo")
        ) {
            return adjustMediaVolume(AudioManager.ADJUST_LOWER, "Decreasing media volume.")
        }

        if (lower.contains("mute media") || lower.contains("mute volume") || lower.contains("awaz band karo")) {
            return adjustMediaVolume(AudioManager.ADJUST_MUTE, "Media audio muted.")
        }

        if (lower.contains("unmute media") || lower.contains("unmute volume") || lower.contains("awaz chalu karo")) {
            return adjustMediaVolume(AudioManager.ADJUST_UNMUTE, "Media audio unmuted.")
        }

        // 7. Media Playback Controls (Play / Pause / Next / Previous)
        if (lower == "play music" || lower == "resume music" || lower == "play media" || lower == "gaana chalao") {
            return dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, "Sending media play command.")
        }
        if (lower == "pause music" || lower == "stop music" || lower == "pause media" || lower == "gaana roko") {
            return dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, "Sending media pause command.")
        }
        if (lower.contains("next song") || lower.contains("next track") || lower.contains("agla gaana")) {
            return dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, "Skipping to the next track.")
        }
        if (lower.contains("previous song") || lower.contains("previous track") || lower.contains("pichla gaana")) {
            return dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Returning to the previous track.")
        }

        // 8. Local Notes (Create, Read, Clear)
        val notePrefixes = listOf(
            "create a note ", "take a note ", "save a note ", "add note ",
            "note down ", "remember that ", "note likho "
        )
        for (prefix in notePrefixes) {
            if (lower.startsWith(prefix)) {
                val noteBody = input.substring(prefix.length).trim()
                if (noteBody.isNotEmpty()) {
                    val saved = memoryManager.addNote(noteBody)
                    return CommandResult(
                        handled = true,
                        spokenResponse = "Saved to your local notes: ${saved.content}",
                        badge = "LOCAL NOTES"
                    )
                }
            }
        }

        if (lower.contains("show my notes") ||
            lower.contains("read my notes") ||
            lower.contains("view notes") ||
            lower == "my notes" ||
            lower.contains("mere notes dikhao")
        ) {
            val notes = memoryManager.getAllNotes()
            if (notes.isEmpty()) {
                return CommandResult(
                    handled = true,
                    spokenResponse = "You do not have any saved local notes yet. Say 'Note down' followed by your message to save one.",
                    badge = "LOCAL NOTES"
                )
            }
            val formatted = notes.take(5).mapIndexed { index, note ->
                "${index + 1}. ${note.content} (${note.formattedDate()})"
            }.joinToString("\n")
            return CommandResult(
                handled = true,
                spokenResponse = "Here are your recent local notes:\n$formatted",
                badge = "LOCAL NOTES"
            )
        }

        if (lower.contains("clear my notes") || lower.contains("delete all notes")) {
            memoryManager.clearAllNotes()
            return CommandResult(
                handled = true,
                spokenResponse = "All local notes have been permanently deleted from your device.",
                badge = "LOCAL NOTES"
            )
        }

        // 9. Android System Settings Screens
        if (lower.contains("wifi settings") || lower.contains("wi-fi settings") || lower.contains("open wifi")) {
            return openSystemSettings(Settings.ACTION_WIFI_SETTINGS, "Opening Wi-Fi settings.")
        }
        if (lower.contains("bluetooth settings") || lower.contains("open bluetooth")) {
            return openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Opening Bluetooth settings.")
        }
        if (lower.contains("display settings") || lower.contains("brightness settings")) {
            return openSystemSettings(Settings.ACTION_DISPLAY_SETTINGS, "Opening Display settings.")
        }
        if (lower.contains("sound settings") || lower.contains("audio settings")) {
            return openSystemSettings(Settings.ACTION_SOUND_SETTINGS, "Opening Sound settings.")
        }
        if (lower.contains("open settings") || lower.contains("system settings") || lower.contains("settings kholo")) {
            return openSystemSettings(Settings.ACTION_SETTINGS, "Opening Android system settings.")
        }

        // 10. Explicit Website Launch
        if (lower.startsWith("open website ") || lower.startsWith("go to website ") || lower.startsWith("browse ")) {
            val rawTarget = input.substringAfter(" ").substringAfter("website ").trim()
            return launchWebsite(rawTarget)
        }

        // 11. Set Alarm via System Clock Intent
        if (lower.startsWith("set alarm") || lower.startsWith("open alarm")) {
            return openAlarmClock()
        }

        // 12. Open Installed Applications (English + Hinglish)
        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            val targetApp = input.substringAfter(" ").trim()
            if (targetApp.isNotEmpty()) {
                return launchInstalledAppOrSite(targetApp)
            }
        }
        if (lower.endsWith(" kholo")) {
            val targetApp = input.substringBeforeLast(" kholo").trim()
            if (targetApp.isNotEmpty()) {
                return launchInstalledAppOrSite(targetApp)
            }
        }

        return CommandResult(handled = false, spokenResponse = "")
    }

    private fun launchCamera(): CommandResult {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                val fallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
            CommandResult(true, "Launching camera.", "CAMERA INTENT")
        } catch (e: Exception) {
            CommandResult(true, "Unable to launch the camera on this device: ${e.localizedMessage}", "ERROR")
        }
    }

    private fun adjustMediaVolume(direction: Int, message: String): CommandResult {
        return try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            )
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            CommandResult(true, "$message Current media volume is $current out of $max.", "AUDIO CTRL")
        } catch (e: Exception) {
            CommandResult(true, "Could not adjust media volume: ${e.localizedMessage}", "ERROR")
        }
    }

    private fun dispatchMediaKey(keyCode: Int, message: String): CommandResult {
        return try {
            val eventTime = SystemClock.uptimeMillis()
            val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
            CommandResult(true, message, "MEDIA CTRL")
        } catch (e: Exception) {
            CommandResult(true, "Could not send media playback key: ${e.localizedMessage}", "ERROR")
        }
    }

    private fun openSystemSettings(action: String, message: String): CommandResult {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult(true, message, "SETTINGS INTENT")
        } catch (e: Exception) {
            CommandResult(true, "Unable to open the requested settings screen.", "ERROR")
        }
    }

    private fun openAlarmClock(): CommandResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult(true, "Opening your clock and alarms.", "CLOCK INTENT")
        } catch (e: Exception) {
            CommandResult(true, "Unable to open the alarm clock application.", "ERROR")
        }
    }

    private fun launchWebsite(rawUrl: String): CommandResult {
        val cleaned = rawUrl.trim().lowercase(Locale.ROOT)
        if (cleaned.isEmpty()) {
            return CommandResult(true, "Please specify a valid website address to open.", "BROWSER")
        }
        val fullUrl = if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            cleaned
        } else if (cleaned.contains(".")) {
            "https://$cleaned"
        } else {
            "https://www.$cleaned.com"
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult(true, "Opening $fullUrl in your default browser. Note: loading external websites requires an active internet connection.", "BROWSER")
        } catch (e: Exception) {
            CommandResult(true, "Could not open website $fullUrl.", "ERROR")
        }
    }

    private fun launchInstalledAppOrSite(targetName: String): CommandResult {
        val lowerTarget = targetName.lowercase(Locale.ROOT).trim()

        // Check if the user explicitly named a website domain (e.g., "open github.com")
        if (lowerTarget.contains(".com") || lowerTarget.contains(".org") || lowerTarget.contains(".in")) {
            return launchWebsite(lowerTarget)
        }

        val pm: PackageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolvedList = pm.queryIntentActivities(mainIntent, 0)
        for (info in resolvedList) {
            val label = info.loadLabel(pm).toString().lowercase(Locale.ROOT)
            val pkg = info.activityInfo.packageName.lowercase(Locale.ROOT)
            if (label == lowerTarget || label.contains(lowerTarget) || pkg.contains(lowerTarget)) {
                val launchIntent = pm.getLaunchIntentForPackage(info.activityInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    val displayLabel = info.loadLabel(pm).toString()
                    return CommandResult(true, "Opening $displayLabel.", "APP LAUNCHER")
                }
            }
        }

        return CommandResult(
            handled = true,
            spokenResponse = "I could not find an installed application matching \"$targetName\" on your phone.",
            badge = "APP LAUNCHER"
        )
    }
}
