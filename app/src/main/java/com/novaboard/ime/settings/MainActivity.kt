package com.novaboard.ime.settings

import android.content.ActivityNotFoundException
import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
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
import com.novaboard.ime.clipboard.ClipboardHistoryManager
import com.novaboard.ime.emoji.EmojiPanel
import com.novaboard.ime.model.Key
import com.novaboard.ime.theme.ThemeManager
import com.novaboard.ime.theme.ThemeMode
import com.novaboard.ime.util.AppLog
import com.novaboard.ime.view.KeyboardView

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
        findViewById<ImageButton>(R.id.btnLayoutPreview).setOnClickListener {
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
                    showText = false
                    minimumWidth = dp(52)
                    minimumHeight = dp(44)
                    setPadding(0, 0, 0, 0)
                    thumbTintList = ColorStateList.valueOf(getColor(R.color.kb_key_bg))
                    trackTintList =
                        ColorStateList.valueOf(getColor(R.color.kb_key_pressed))
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
            val heightLabel =
                TextView(this).apply {
                    text =
                        getString(
                            R.string.keyboard_height_value,
                            KeyboardPreferences.getKeyboardHeightScale(this@MainActivity),
                        )
                    setPadding(0, 18, 0, 4)
                }
            content.addView(heightLabel)
            content.addView(
                SeekBar(this).apply {
                    max = 40
                    progress =
                        KeyboardPreferences.getKeyboardHeightScale(this@MainActivity) - 80
                    contentDescription = getString(R.string.keyboard_height)
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(
                                seekBar: SeekBar?,
                                progress: Int,
                                fromUser: Boolean,
                            ) {
                                KeyboardPreferences.setKeyboardHeightScale(
                                    this@MainActivity,
                                    progress + 80,
                                )
                                heightLabel.text =
                                    getString(R.string.keyboard_height_value, progress + 80)
                            }

                            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                        },
                    )
                },
            )
            content.addView(
                Button(this).apply {
                    text = getString(R.string.delete_saved_image_clips)
                    contentDescription = getString(R.string.delete_saved_image_clips_summary)
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(R.string.delete_saved_image_clips)
                            .setMessage(R.string.delete_saved_image_clips_confirmation)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.delete) { _, _ ->
                                val removed =
                                    ClipboardHistoryManager.clearStoredImageHistory(
                                        this@MainActivity,
                                    )
                                Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.saved_image_clips_deleted, removed),
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                            .show()
                    }
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
        builder.show()
    }

    private fun showLayoutPreview() {
        val preview = LayoutInflater.from(this).inflate(R.layout.keyboard_container, null)
        val keyboard = preview.findViewById<KeyboardView>(R.id.keyboardView)
        keyboard.applyPreferences()
        keyboard.setPage(
            if (KeyboardPreferences.getBoolean(this, KeyboardPreferences.SHOW_NUMBER_ROW)) {
                com.novaboard.ime.model.KeyboardLayouts.letters
            } else {
                com.novaboard.ime.model.KeyboardLayouts.lettersWithoutNumberRow
            }
        )
        preview.findViewById<View>(R.id.cursorRow).visibility =
            if (KeyboardPreferences.getBoolean(this, KeyboardPreferences.SHOW_ARROW_KEYS)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        wirePreview(preview, keyboard)

        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(getColor(R.color.kb_background))
            }
        val resizeLabel =
            TextView(this).apply {
                text =
                    getString(
                        R.string.keyboard_height_value,
                        KeyboardPreferences.getKeyboardHeightScale(this@MainActivity),
                    )
                textSize = 13f
                setTextColor(getColor(R.color.kb_toolbar_icon))
                setPadding(dp(16), dp(12), dp(16), 0)
            }
        val resizeSeekBar =
            SeekBar(this).apply {
                max = 40
                progress = KeyboardPreferences.getKeyboardHeightScale(this@MainActivity) - 80
                contentDescription = getString(R.string.keyboard_height)
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean,
                        ) {
                            val scale = progress + 80
                            KeyboardPreferences.setKeyboardHeightScale(this@MainActivity, scale)
                            resizeLabel.text =
                                getString(R.string.keyboard_height_value, scale)
                            keyboard.applyPreferences()
                            keyboard.requestLayout()
                            preview.requestLayout()
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                    },
                )
            }
        content.addView(
            preview,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
            ).apply { weight = 1f },
        )
        content.addView(resizeLabel)
        content.addView(
            resizeSeekBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48),
            ).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
            },
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.layout_preview)
            .setView(content)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun wirePreview(root: View, keyboard: KeyboardView) {
        val toolsBar = root.findViewById<View>(R.id.toolsBar)
        val toggle = root.findViewById<ImageButton>(R.id.btnToggleStripFromSuggestions)
        toggle.setOnClickListener {
            val expanded = toolsBar.visibility != View.VISIBLE
            toolsBar.visibility = if (expanded) View.VISIBLE else View.GONE
            toggle.setImageResource(
                if (expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up,
            )
            toggle.contentDescription =
                getString(if (expanded) R.string.cd_collapse_tools else R.string.cd_expand_tools)
        }
        root.findViewById<ImageButton>(R.id.btnMore).setOnClickListener {
            showPreviewTools(root)
        }
        root.findViewById<ImageButton>(R.id.btnClipboard).setOnClickListener {
            showPreviewPanel(root, R.string.preview_clipboard, "No clipboard items yet")
        }
        root.findViewById<ImageButton>(R.id.btnTranslate).setOnClickListener {
            showPreviewPanel(root, R.string.preview_translation, "Select text to translate")
        }
        root.findViewById<ImageButton>(R.id.btnVoice).setOnClickListener {
            Toast.makeText(this, R.string.preview_voice_ready, Toast.LENGTH_SHORT).show()
        }
        root.findViewById<ImageButton>(R.id.btnHotkeys).setOnClickListener {
            root.findViewById<View>(R.id.hotkeysScroller).visibility = View.VISIBLE
            root.findViewById<View>(R.id.cursorRow).visibility = View.GONE
        }
        keyboard.listener =
            object : KeyboardView.OnKeyListener {
                override fun onKey(key: Key, outputChar: String) = Unit
                override fun onBackspace() = Unit
                override fun onEnter() = Unit
                override fun onShiftToggled(shiftOn: Boolean, capsLock: Boolean) = Unit
                override fun onSwitchToSymbols() = Unit
                override fun onSwitchToLetters() = Unit
                override fun onCursorMove(direction: Int) = Unit
                override fun onQuickDelete() = Unit
                override fun onGestureWord(path: String) = Unit

                override fun onEmoji() {
                    val container = root.findViewById<FrameLayout>(R.id.emojiPanelContainer)
                    EmojiPanel(this@MainActivity) {}.show(container)
                }
            }
    }

    private fun showPreviewTools(root: View) {
        val overlay = root.findViewById<FrameLayout>(R.id.overlayPanelContainer)
        overlay.removeAllViews()
        val panel =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(getColor(R.color.kb_background))
                setPadding(dp(12), dp(8), dp(12), dp(12))
            }
        val header =
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        header.addView(
            TextView(this).apply {
                text = getString(R.string.preview_tools)
                textSize = 18f
                setTextColor(getColor(R.color.kb_key_text))
                layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
            },
        )
        header.addView(
            ImageButton(this).apply {
                setImageResource(R.drawable.ic_arrow_back)
                contentDescription = getString(R.string.preview_close_panel)
                setBackgroundColor(Color.TRANSPARENT)
                setColorFilter(getColor(R.color.kb_toolbar_icon))
                minimumWidth = dp(48)
                minimumHeight = dp(48)
                setOnClickListener { overlay.visibility = View.GONE }
            },
            LinearLayout.LayoutParams(dp(48), dp(56)),
        )
        panel.addView(header)
        val grid = GridLayout(this).apply { columnCount = 3 }
        listOf(
            R.string.cd_clipboard to R.drawable.ic_clipboard,
            R.string.cd_hotkeys to R.drawable.ic_hotkeys,
            R.string.cd_translate to R.drawable.ic_translate,
            R.string.cd_voice to R.drawable.ic_mic,
            R.string.cd_emoji to R.drawable.ic_keyboard,
            R.string.tool_resize to R.drawable.ic_more,
        ).forEach { (labelRes, iconRes) ->
            val cell =
                ImageButton(this).apply {
                    setImageResource(iconRes)
                    contentDescription = getString(labelRes)
                    setBackgroundColor(Color.TRANSPARENT)
                    setColorFilter(getColor(R.color.kb_toolbar_icon))
                    setOnClickListener {
                        overlay.visibility = View.GONE
                        when (labelRes) {
                            R.string.cd_emoji ->
                                root.findViewById<KeyboardView>(R.id.keyboardView).listener
                                    ?.onEmoji()
                            R.string.cd_clipboard ->
                                showPreviewPanel(
                                    root,
                                    R.string.preview_clipboard,
                                    "No clipboard items yet",
                                )
                            R.string.cd_translate ->
                                showPreviewPanel(
                                    root,
                                    R.string.preview_translation,
                                    "Select text to translate",
                                )
                        }
                    }
                }
            grid.addView(
                cell,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(64)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                },
            )
        }
        panel.addView(grid)
        overlay.addView(panel)
        overlay.visibility = View.VISIBLE
    }

    private fun showPreviewPanel(root: View, titleRes: Int, message: String) {
        val overlay = root.findViewById<FrameLayout>(R.id.overlayPanelContainer)
        overlay.removeAllViews()
        val panel =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(getColor(R.color.kb_background))
                setPadding(dp(16), dp(8), dp(16), dp(16))
            }
        panel.addView(
            TextView(this).apply {
                text = getString(titleRes)
                textSize = 18f
                setTextColor(getColor(R.color.kb_key_text))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(12))
            },
        )
        panel.addView(
            TextView(this).apply {
                text = message
                textSize = 16f
                setTextColor(getColor(R.color.kb_toolbar_icon))
                setPadding(0, dp(16), 0, dp(16))
            },
        )
        panel.addView(
            Button(this).apply {
                text = getString(R.string.preview_close_panel)
                setOnClickListener { overlay.visibility = View.GONE }
            },
        )
        overlay.addView(panel)
        overlay.visibility = View.VISIBLE
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

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
