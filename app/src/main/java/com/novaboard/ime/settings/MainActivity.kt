package com.novaboard.ime.settings

import android.content.ActivityNotFoundException
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.novaboard.ime.R
import com.novaboard.ime.theme.ThemeManager
import com.novaboard.ime.theme.ThemeMode
import com.novaboard.ime.util.AppLog

class MainActivity : AppCompatActivity() {
    private val requestMicrophone =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { granted ->
            val message =
                if (granted) R.string.voice_permission_granted
                else R.string.voice_permission_denied
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    private val createReport =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
        ) { destination ->
            if (destination == null) return@registerForActivityResult
            runCatching {
                    AppLog.exportReport(this, destination)
                }
                .onSuccess {
                    Toast.makeText(this, R.string.app_log_exported, Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    AppLog.e("MainActivity", "Failed to export diagnostic report", it)
                    Toast.makeText(this, R.string.app_log_export_failed, Toast.LENGTH_LONG).show()
                }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLog.init(this)
        AppLog.i("MainActivity", "Settings screen opened")
        ThemeManager.applyStored(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (intent.getBooleanExtra(EXTRA_REQUEST_MICROPHONE, false) &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        }

        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btnSwitch).setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        findViewById<Button>(R.id.btnExportLog).setOnClickListener {
            try {
                createReport.launch("NovaBoard-diagnostic-report.txt")
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.app_log_export_failed, Toast.LENGTH_LONG).show()
            }
        }
        findViewById<Button>(R.id.btnLayoutSettings).setOnClickListener {
            showPreferenceDialog(
                R.string.layout_keys,
                listOf(
                    PreferenceSpec(
                        R.string.number_row,
                        R.string.number_row_summary,
                        KeyboardPreferences.SHOW_NUMBER_ROW,
                        true,
                    ),
                    PreferenceSpec(
                        R.string.arrow_keys,
                        R.string.arrow_keys_summary,
                        KeyboardPreferences.SHOW_ARROW_KEYS,
                        true,
                    ),
                    PreferenceSpec(
                        R.string.long_press_symbols,
                        R.string.long_press_symbols_summary,
                        KeyboardPreferences.LONG_PRESS_SYMBOLS,
                        true,
                    ),
                    PreferenceSpec(
                        R.string.accented_characters,
                        R.string.accented_characters_summary,
                        KeyboardPreferences.ACCENTED_CHARACTERS,
                        true,
                    ),
                    PreferenceSpec(
                        R.string.key_popups,
                        R.string.key_popups_summary,
                        KeyboardPreferences.KEY_POPUPS,
                        true,
                    ),
                    PreferenceSpec(
                        R.string.large_key_text,
                        R.string.large_key_text_summary,
                        KeyboardPreferences.LARGE_KEY_TEXT,
                        false,
                    ),
                    PreferenceSpec(
                        R.string.image_clipboard_history,
                        R.string.image_clipboard_history_summary,
                        KeyboardPreferences.IMAGE_CLIPBOARD_HISTORY,
                        false,
                    ),
                ),
            )
        }
        findViewById<Button>(R.id.btnLayoutPreview).setOnClickListener {
            showLayoutPreview()
        }
        findViewById<Button>(R.id.btnTypingSettings).setOnClickListener {
            showPreferenceDialog(
                R.string.typing,
                listOf(
                    PreferenceSpec(
                        R.string.autocorrect,
                        R.string.autocorrect_summary,
                        KeyboardPreferences.AUTOCORRECT,
                        false,
                    ),
                    PreferenceSpec(
                        R.string.undo_autocorrect,
                        R.string.undo_autocorrect_summary,
                        KeyboardPreferences.UNDO_AUTOCORRECT,
                        false,
                    ),
                    PreferenceSpec(
                        R.string.quick_prediction_insert,
                        R.string.quick_prediction_insert_summary,
                        KeyboardPreferences.QUICK_PREDICTION_INSERT,
                        false,
                    ),
                    PreferenceSpec(
                        R.string.quick_period,
                        R.string.quick_period_summary,
                        KeyboardPreferences.QUICK_PERIOD,
                        true,
                    ),
                    PreferenceSpec(
                        R.string.auto_capitalize,
                        R.string.auto_capitalize_summary,
                        KeyboardPreferences.AUTO_CAPITALIZE,
                        true,
                    ),
                    PreferenceSpec(
                        R.string.auto_space,
                        R.string.auto_space_summary,
                        KeyboardPreferences.AUTO_SPACE,
                        false,
                    ),
                    PreferenceSpec(
                        R.string.cursor_control,
                        R.string.cursor_control_summary,
                        KeyboardPreferences.CURSOR_CONTROL,
                        true,
                    ),
                    PreferenceSpec(
                        R.string.quick_delete,
                        R.string.quick_delete_summary,
                        KeyboardPreferences.QUICK_DELETE,
                        true,
                    ),
                ),
            )
        }
        findViewById<Button>(R.id.btnEmojiSettings).setOnClickListener {
            showPreferenceDialog(
                R.string.emoji,
                listOf(
                    PreferenceSpec(
                        R.string.emoji_predictions,
                        R.string.emoji_predictions_summary,
                        KeyboardPreferences.EMOJI_PREDICTIONS,
                        true,
                    ),
                    PreferenceSpec(
                        R.string.dedicated_emoji_key,
                        R.string.dedicated_emoji_key_summary,
                        KeyboardPreferences.DEDICATED_EMOJI_KEY,
                        true,
                    ),
                    PreferenceSpec(
                        R.string.emoji_on_enter,
                        R.string.emoji_on_enter_summary,
                        KeyboardPreferences.EMOJI_ON_ENTER,
                        false,
                    ),
                ),
            )
        }
        findViewById<Button>(R.id.btnSoundSettings).setOnClickListener {
            showPreferenceDialog(
                R.string.sound_vibration,
                listOf(
                    PreferenceSpec(
                        R.string.keypress_sound,
                        R.string.keypress_sound_summary,
                        KeyboardPreferences.SOUND_ON_KEYPRESS,
                        false,
                    ),
                    PreferenceSpec(
                        R.string.keypress_vibration,
                        R.string.keypress_vibration_summary,
                        KeyboardPreferences.VIBRATION_ON_KEYPRESS,
                        false,
                    ),
                ),
            )
        }
        findViewById<Button>(R.id.btnGestureSettings).setOnClickListener {
            showGestureModeDialog()
        }
        findViewById<Button>(R.id.btnIncognito).setOnClickListener {
            val enabled = !KeyboardPreferences.isIncognitoMode(this)
            KeyboardPreferences.setIncognitoMode(this, enabled)
            updateIncognitoButton()
            Toast.makeText(
                    this,
                    if (enabled) R.string.incognito_on else R.string.incognito_off,
                    Toast.LENGTH_SHORT,
                )
                .show()
        }
        findViewById<Button>(R.id.btnResetSettings).setOnClickListener {
            KeyboardPreferences.reset(this)
            updateIncognitoButton()
            Toast.makeText(this, R.string.settings_reset, Toast.LENGTH_SHORT).show()
        }
        updateIncognitoButton()

        val group = findViewById<RadioGroup>(R.id.themeGroup)
        when (ThemeManager.get(this)) {
            ThemeMode.SYSTEM -> findViewById<RadioButton>(R.id.radioSystem).isChecked = true
            ThemeMode.LIGHT -> findViewById<RadioButton>(R.id.radioLight).isChecked = true
            ThemeMode.DARK -> findViewById<RadioButton>(R.id.radioDark).isChecked = true
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val mode =
                when (checkedId) {
                    R.id.radioLight -> ThemeMode.LIGHT
                    R.id.radioDark -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                }
            ThemeManager.set(this, mode)
            recreate()
        }
    }

    private fun updateIncognitoButton() {
        val button = findViewById<Button>(R.id.btnIncognito)
        button.text =
            getString(
                if (KeyboardPreferences.isIncognitoMode(this)) {
                    R.string.incognito_on
                } else {
                    R.string.incognito_mode
                },
            )
        button.contentDescription = getString(R.string.incognito_summary)
    }

    private fun showGestureModeDialog() {
        val modes = GestureMode.entries.toTypedArray()
        val labels =
            arrayOf(
                getString(R.string.gesture_input_flow),
                getString(R.string.gesture_input_gestures),
            )
        val selected = modes.indexOf(KeyboardPreferences.getGestureMode(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.gesture_input)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                KeyboardPreferences.setGestureMode(this, modes[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPreferenceDialog(titleRes: Int, specs: List<PreferenceSpec>) {
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 8, 24, 8)
            }
        specs.forEach { spec ->
            val row =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 10, 0, 10)
                }
            val labels =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams =
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            labels.addView(
                TextView(this).apply {
                    text = getString(spec.titleRes)
                    textSize = 16f
                }
            )
            labels.addView(
                TextView(this).apply {
                    text = getString(spec.summaryRes)
                    textSize = 12f
                    alpha = 0.7f
                }
            )
            row.addView(labels)
            row.addView(
                SwitchCompat(this).apply {
                    isChecked = KeyboardPreferences.getBoolean(this@MainActivity, spec.key)
                    setOnCheckedChangeListener { _, checked ->
                        KeyboardPreferences.setBoolean(this@MainActivity, spec.key, checked)
                    }
                }
            )
            content.addView(row)
        }
        if (titleRes == R.string.layout_keys) {
            val durationLabel =
                TextView(this).apply {
                    text =
                        getString(
                            R.string.long_press_duration_value,
                            KeyboardPreferences.getLongPressDuration(this@MainActivity),
                        )
                    setPadding(0, 18, 0, 4)
                }
            content.addView(durationLabel)
            content.addView(
                SeekBar(this).apply {
                    max = 600
                    progress = KeyboardPreferences.getLongPressDuration(this@MainActivity) - 200
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(
                                seekBar: SeekBar?,
                                progress: Int,
                                fromUser: Boolean,
                            ) {
                                KeyboardPreferences.setLongPressDuration(
                                    this@MainActivity,
                                    progress + 200,
                                )
                                durationLabel.text =
                                    getString(
                                        R.string.long_press_duration_value,
                                        progress + 200,
                                    )
                            }

                            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                        }
                    )
                }
            )
        }
        if (titleRes == R.string.typing) {
            content.addView(
                Button(this).apply {
                    text = getString(R.string.clear_typing_data)
                    setOnClickListener {
                        getSharedPreferences("novaboard_prefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean(KeyboardPreferences.CLEAR_TYPING_DATA, true)
                            .apply()
                        Toast.makeText(
                                this@MainActivity,
                                R.string.typing_data_cleared,
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                }
            )
        }
        val builder =
            AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
        if (titleRes == R.string.emoji) {
            builder.setNeutralButton(R.string.emoji_font) { _, _ -> showEmojiFontDialog() }
        }
        builder.show()
    }

    private fun showEmojiFontDialog() {
        val values = arrayOf("system", "google")
        val labels =
            arrayOf(
                getString(R.string.system_emoji),
                getString(R.string.sans_serif_emoji),
            )
        val selected = values.indexOf(KeyboardPreferences.getEmojiFont(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.emoji_font)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                KeyboardPreferences.setEmojiFont(this, values[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLayoutPreview() {
        val preview = LayoutInflater.from(this).inflate(R.layout.keyboard_container, null)
        val keyboard = preview.findViewById<com.novaboard.ime.view.KeyboardView>(R.id.keyboardView)
        keyboard.applyPreferences()
        keyboard.setPage(
            if (KeyboardPreferences.getBoolean(this, KeyboardPreferences.SHOW_NUMBER_ROW)) {
                com.novaboard.ime.model.KeyboardLayouts.letters
            } else {
                com.novaboard.ime.model.KeyboardLayouts.lettersWithoutNumberRow
            }
        )
        preview.findViewById<View>(R.id.toolsBar).visibility = View.GONE
        preview.findViewById<View>(R.id.suggestionBar).visibility = View.VISIBLE
        preview.findViewById<View>(R.id.cursorRow).visibility =
            if (KeyboardPreferences.getBoolean(this, KeyboardPreferences.SHOW_ARROW_KEYS)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        AlertDialog.Builder(this)
            .setTitle(R.string.layout_preview)
            .setView(preview)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private data class PreferenceSpec(
        val titleRes: Int,
        val summaryRes: Int,
        val key: String,
        val default: Boolean,
    )

    companion object {
        const val EXTRA_REQUEST_MICROPHONE = "request_microphone"
    }
}
