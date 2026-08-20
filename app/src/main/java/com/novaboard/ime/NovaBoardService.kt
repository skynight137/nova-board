package com.novaboard.ime

import android.content.ClipDescription
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.novaboard.ime.clipboard.ClipType
import com.novaboard.ime.clipboard.ClipboardHistoryManager
import com.novaboard.ime.clipboard.ClipboardItem
import com.novaboard.ime.clipboard.ClipboardPanel
import com.novaboard.ime.editing.RepeatController
import com.novaboard.ime.editing.RepeatToken
import com.novaboard.ime.editing.acceptsInputSessionResult
import com.novaboard.ime.editing.canUndoAutocorrect
import com.novaboard.ime.editing.previousWordDeletionCount
import com.novaboard.ime.editing.shouldResetTrackedTyping
import com.novaboard.ime.editor.isConversationEditorInputType
import com.novaboard.ime.emoji.EmojiData
import com.novaboard.ime.emoji.EmojiPanel
import com.novaboard.ime.hotkeys.HotkeyController
import com.novaboard.ime.model.Key
import com.novaboard.ime.model.KeyType
import com.novaboard.ime.model.KeyboardLayouts
import com.novaboard.ime.settings.KeyboardPreferences
import com.novaboard.ime.settings.MainActivity
import com.novaboard.ime.suggestion.SuggestionEngine
import com.novaboard.ime.suggestion.shouldLearnWord
import com.novaboard.ime.theme.ThemeManager
import com.novaboard.ime.translation.TranslationComposerState
import com.novaboard.ime.translation.TranslationCommit
import com.novaboard.ime.translation.TranslationPanel
import com.novaboard.ime.translation.UnavailableTranslationProvider
import com.novaboard.ime.tools.ToolMenuItem
import com.novaboard.ime.tools.visibleToolMenuItems
import com.novaboard.ime.util.AppLog
import com.novaboard.ime.view.KeyboardView

class NovaBoardService : InputMethodService(), KeyboardView.OnKeyListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var toolsBar: LinearLayout
    private lateinit var suggestionBar: LinearLayout
    private lateinit var suggestion1: TextView
    private lateinit var suggestion2: TextView
    private lateinit var suggestion3: TextView
    private lateinit var hotkeysScroller: HorizontalScrollView
    private lateinit var hotkeysRow: LinearLayout
    private lateinit var cursorRow: LinearLayout
    private lateinit var emojiPanelContainer: android.widget.FrameLayout
    private lateinit var translationPanelContainer: android.widget.FrameLayout
    private lateinit var incognitoBanner: TextView

    private lateinit var clipboardHistory: ClipboardHistoryManager
    private val suggestionEngine = SuggestionEngine()
    private val hotkeyController = HotkeyController { currentInputConnection }
    private var clipboardPanel: ClipboardPanel? = null
    private var emojiPanel: EmojiPanel? = null
    private var toolsMenuPopup: PopupWindow? = null
    private var lastSpaceTime = 0L
    private var lastAutocorrectOriginal: String? = null
    private var lastAutocorrectReplacement: String? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false
    private var inputSession = 0L
    private var voiceRecognizerGeneration = 0L
    private var translationPanel: TranslationPanel? = null
    private val translationProvider = UnavailableTranslationProvider()
    private var selectionStart = -1
    private var selectionEnd = -1
    private val cursorRepeatHandler = Handler(Looper.getMainLooper())
    private var cursorRepeatRunnable: Runnable? = null
    private val cursorRepeatController =
        RepeatController(
            initialDelayMs = CURSOR_REPEAT_INITIAL_DELAY_MS,
            intervalMs = CURSOR_REPEAT_INTERVAL_MS,
        )

    private val currentWord = StringBuilder()
    private var previousWord: String? = null
    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == KeyboardPreferences.CLEAR_TYPING_DATA) {
                suggestionEngine.clearLearnedData()
                preferences.edit().remove(KeyboardPreferences.CLEAR_TYPING_DATA).apply()
                currentWord.clear()
                previousWord = null
                refreshSuggestionsIfReady()
                return@OnSharedPreferenceChangeListener
            }
            if (::keyboardView.isInitialized) {
                keyboardView.post { applyKeyboardPreferences() }
            }
        }

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.i("NovaBoardService", "Input method service created")
        ThemeManager.applyStored(this)
        clipboardHistory = ClipboardHistoryManager(this)
        clipboardHistory.start()
        val preferences = getSharedPreferences("novaboard_prefs", MODE_PRIVATE)
        if (preferences.getBoolean(KeyboardPreferences.CLEAR_TYPING_DATA, false)) {
            suggestionEngine.clearLearnedData()
            preferences.edit().remove(KeyboardPreferences.CLEAR_TYPING_DATA).apply()
        }
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onDestroy() {
        resetInputSession()
        emojiPanel?.dismiss()
        clipboardHistory.stop()
        speechRecognizer?.destroy()
        getSharedPreferences("novaboard_prefs", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(preferenceListener)
        AppLog.i("NovaBoardService", "Input method service destroyed")
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val root = LayoutInflater.from(this).inflate(R.layout.keyboard_container, null)

        keyboardView = root.findViewById(R.id.keyboardView)
        toolsBar = root.findViewById(R.id.toolsBar)
        suggestionBar = root.findViewById(R.id.suggestionBar)
        suggestion1 = root.findViewById(R.id.suggestion1)
        suggestion2 = root.findViewById(R.id.suggestion2)
        suggestion3 = root.findViewById(R.id.suggestion3)
        hotkeysScroller = root.findViewById(R.id.hotkeysScroller)
        hotkeysRow = root.findViewById(R.id.hotkeysRow)
        cursorRow = root.findViewById(R.id.cursorRow)
        translationPanelContainer = root.findViewById(R.id.translationPanelContainer)
        emojiPanelContainer = root.findViewById(R.id.emojiPanelContainer)
        incognitoBanner = root.findViewById(R.id.incognitoBanner)
        incognitoBanner.setOnClickListener {
            KeyboardPreferences.setIncognitoMode(this, false)
            updateIncognitoBanner()
        }

        keyboardView.listener = this
        applyKeyboardPreferences()

        hotkeyController.build(this, hotkeysRow) {
            /* modifier armed/disarmed, nothing extra to redraw */
        }

        wireToggleChevrons(root)
        wireToolsBar(root)
        wireCursorRow(root)
        wireSuggestions()

        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        stopCursorRepeat()
        if (::keyboardView.isInitialized) keyboardView.cancelInteractions()
        inputSession++
        selectionStart = -1
        selectionEnd = -1
        resetTypingState()
        applyKeyboardPreferences()
        keyboardView.setShiftState(false)
        refreshSuggestions()
    }

    override fun onFinishInput() {
        resetInputSession()
        super.onFinishInput()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd,
        )
        if (selectionStart != newSelStart || selectionEnd != newSelEnd) {
            dismissTranslationPanel()
        }
        selectionStart = newSelStart
        selectionEnd = newSelEnd
        val textBeforeCursor = currentInputConnection?.getTextBeforeCursor(128, 0)?.toString().orEmpty()
        if (
            shouldResetTrackedTyping(
                oldSelectionStart = oldSelStart,
                oldSelectionEnd = oldSelEnd,
                newSelectionStart = newSelStart,
                newSelectionEnd = newSelEnd,
                trackedWord = currentWord.toString(),
                textBeforeCursor = textBeforeCursor,
            )
        ) {
            resetTypingState()
            refreshSuggestionsIfReady()
        }
    }

    private fun resetTypingState() {
        currentWord.clear()
        previousWord = null
        lastAutocorrectOriginal = null
        lastAutocorrectReplacement = null
    }

    private fun resetInputSession() {
        stopCursorRepeat()
        inputSession++
        if (::keyboardView.isInitialized) keyboardView.cancelInteractions()
        stopVoiceInput()
        clipboardPanel?.dismiss()
        clipboardPanel = null
        emojiPanel?.dismiss()
        emojiPanel = null
        dismissTranslationPanel()
        toolsMenuPopup?.dismiss()
        toolsMenuPopup = null
        selectionStart = -1
        selectionEnd = -1
        resetTypingState()
        hotkeyController.disarm()
    }

    private fun applyKeyboardPreferences() {
        keyboardView.applyPreferences()
        keyboardView.setPage(
            if (KeyboardPreferences.getBoolean(this, KeyboardPreferences.SHOW_NUMBER_ROW)) {
                KeyboardLayouts.letters
            } else {
                KeyboardLayouts.lettersWithoutNumberRow
            }
        )
        cursorRow.visibility =
            if (KeyboardPreferences.getBoolean(this, KeyboardPreferences.SHOW_ARROW_KEYS)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        updateIncognitoBanner()
    }

    private fun updateIncognitoBanner() {
        if (!::incognitoBanner.isInitialized) return
        val enabled = KeyboardPreferences.isIncognitoMode(this)
        incognitoBanner.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            incognitoBanner.text = getString(R.string.incognito_on)
            incognitoBanner.contentDescription = getString(R.string.incognito_exit)
        }
    }

    // ---- tools/suggestion strip toggle (the chevron in the reference screenshot) ----

    private fun wireToggleChevrons(root: View) {
        val toChevron = root.findViewById<ImageButton>(R.id.btnToggleStrip)
        val fromChevron = root.findViewById<ImageButton>(R.id.btnToggleStripFromSuggestions)
        toChevron.setOnClickListener { showSuggestionStrip() }
        fromChevron.setOnClickListener { showToolsStrip() }
    }

    private fun showSuggestionStrip() {
        toolsBar.visibility = View.GONE
        suggestionBar.visibility = View.VISIBLE
    }

    private fun showToolsStrip() {
        suggestionBar.visibility = View.GONE
        toolsBar.visibility = View.VISIBLE
    }

    // ---- tools row ----

    private fun wireToolsBar(root: View) {
        root.findViewById<ImageButton>(R.id.btnClipboard).setOnClickListener { openClipboard(root) }
        root.findViewById<ImageButton>(R.id.btnHotkeys).setOnClickListener { toggleHotkeys() }
        root.findViewById<ImageButton>(R.id.btnTranslate).setOnClickListener {
            openTranslation()
        }
        root.findViewById<ImageButton>(R.id.btnVoice).setOnClickListener { toggleVoiceInput() }
        root.findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
        }
        root.findViewById<ImageButton>(R.id.btnMore).setOnClickListener {
            showToolsMenu(root)
        }
    }

    private fun showToolsMenu(anchor: View) {
        toolsMenuPopup?.dismiss()
        val menu =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(getColor(R.color.kb_key_bg_special))
                setPadding(8, 6, 8, 8)
            }
        menu.addView(
            TextView(this).apply {
                text = getString(R.string.tool_menu_title)
                textSize = 16f
                setTextColor(getColor(R.color.kb_key_text))
                gravity = Gravity.CENTER
                setPadding(8, 10, 8, 10)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val grid =
            GridLayout(this).apply {
                columnCount = 4
                alignmentMode = GridLayout.ALIGN_BOUNDS
                useDefaultMargins = false
            }
        val items =
            visibleToolMenuItems(
                listOf(
                    ToolMenuItem("clipboard", getString(R.string.cd_clipboard)),
                    ToolMenuItem("hotkeys", getString(R.string.cd_hotkeys)),
                    ToolMenuItem("translate", getString(R.string.tool_translate)),
                    ToolMenuItem("voice", getString(R.string.cd_voice)),
                    ToolMenuItem("search", getString(R.string.tool_search)),
                    ToolMenuItem("emoji", getString(R.string.cd_emoji)),
                    ToolMenuItem("settings", getString(R.string.tool_settings)),
                    ToolMenuItem("languages", getString(R.string.tool_languages)),
                    ToolMenuItem("layouts", getString(R.string.tool_layouts)),
                    ToolMenuItem("themes", getString(R.string.tool_themes)),
                    ToolMenuItem("modes", getString(R.string.tool_modes)),
                    ToolMenuItem("resize", getString(R.string.tool_resize)),
                    ToolMenuItem("incognito", getString(R.string.tool_incognito)),
                    ToolMenuItem("stickers", getString(R.string.tool_stickers)),
                    ToolMenuItem("gif", getString(R.string.tool_gif)),
                    ToolMenuItem("tips", getString(R.string.tool_tips)),
                ),
            )
        items.forEach { item ->
            val cell =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isFocusable = true
                    isClickable = true
                    contentDescription = item.label
                    setPadding(4, 10, 4, 10)
                }
            cell.addView(
                TextView(this).apply {
                    text = toolGlyph(item.id)
                    textSize = 25f
                    setTextColor(getColor(R.color.kb_key_text))
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (36 * resources.displayMetrics.density).toInt(),
                ),
            )
            cell.addView(
                TextView(this).apply {
                    text = item.label
                    textSize = 13f
                    setTextColor(getColor(R.color.kb_key_text))
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            cell.setOnClickListener {
                toolsMenuPopup?.dismiss()
                toolsMenuPopup = null
                when (item.id) {
                    "clipboard" -> openClipboard(anchor)
                    "hotkeys" -> toggleHotkeys()
                    "translate" -> openTranslation()
                    "voice" -> toggleVoiceInput()
                    "search" ->
                        currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
                    "emoji", "stickers", "gif" -> onEmoji()
                    "settings", "languages", "layouts", "themes", "modes", "resize", "tips" ->
                        startActivity(
                            Intent(this@NovaBoardService, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    "incognito" -> {
                        KeyboardPreferences.setIncognitoMode(
                            this,
                            !KeyboardPreferences.isIncognitoMode(this),
                        )
                        updateIncognitoBanner()
                    }
                }
            }
            grid.addView(
                cell,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                },
            )
        }
        menu.addView(
            grid,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        toolsMenuPopup =
            PopupWindow(
                menu,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true,
            ).also { popup ->
                popup.isOutsideTouchable = true
                popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                popup.elevation = 8f
                popup.setOnDismissListener { toolsMenuPopup = null }
                popup.showAtLocation(anchor, Gravity.BOTTOM, 0, anchor.height)
            }
    }

    private fun toolGlyph(id: String): String =
        when (id) {
            "clipboard" -> "▣"
            "hotkeys" -> "⌨"
            "translate" -> "文"
            "voice" -> "♩"
            "search" -> "⌕"
            "emoji", "stickers", "gif" -> "☺"
            "settings" -> "⚙"
            "languages" -> "◎"
            "layouts" -> "▦"
            "themes" -> "◉"
            "modes" -> "◆"
            "resize" -> "↗"
            "incognito" -> "⌒"
            "tips" -> "ⓘ"
            else -> "•"
        }

    private fun openClipboard(root: View) {
        clipboardPanel?.dismiss()
        clipboardPanel = ClipboardPanel(this, clipboardHistory) { item -> pasteClipboardItem(item) }
        clipboardPanel?.show(root)
    }

    private fun pasteClipboardItem(item: ClipboardItem) {
        val ic = currentInputConnection ?: return
        when (item.type) {
            ClipType.TEXT -> {
                ic.commitText(item.text ?: "", 1)
                resetTypingState()
                refreshSuggestionsIfReady()
            }
            ClipType.IMAGE -> pasteClipboardImage(ic, item.imageUri)
        }
    }

    private fun pasteClipboardImage(ic: InputConnection, rawUri: String?) {
        val uri = rawUri?.let(Uri::parse)
        if (uri == null) {
            Toast.makeText(this, "The image clipboard item is unavailable", Toast.LENGTH_SHORT)
                .show()
            return
        }
        val mimeType = contentResolver.getType(uri) ?: "image/*"
        runCatching {
                val contentInfo =
                    InputContentInfo(
                        uri,
                        ClipDescription("NovaBoard image", arrayOf(mimeType)),
                        null,
                    )
                contentInfo.requestPermission()
                check(
                    ic.commitContent(
                        contentInfo,
                        InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                        null,
                    )
                ) {
                    "The current editor rejected image content"
                }
            }
            .onFailure {
                AppLog.w("NovaBoardService", "Image clipboard paste was rejected", it)
                Toast.makeText(this, "This app does not support image paste", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun openTranslation() {
        val ic = currentInputConnection
        val text = ic?.getSelectedText(0)?.toString()?.takeIf { it.isNotBlank() }
        val start = selectionStart
        val end = selectionEnd
        dismissTranslationPanel()
        val state =
            TranslationComposerState(
                session = inputSession,
                selectedStart = start.takeIf { text != null && it >= 0 } ?: -1,
                selectedEnd = end.takeIf { text != null && it > start } ?: -1,
                sourceText = text.orEmpty(),
            )
        translationPanel =
            TranslationPanel(
                this,
                state,
                provider = translationProvider,
                onDismiss = { dismissTranslationPanel() },
                onPaste = { panelState, cursor ->
                    commitTranslation(
                        TranslationCommit.Paste(panelState.translatedText.orEmpty(), cursor),
                    )
                },
                onReply = { panelState, selectedStart, selectedEnd ->
                    commitTranslation(
                        TranslationCommit.Reply(
                            panelState.translatedText.orEmpty(),
                            selectedStart,
                            selectedEnd,
                        ),
                    )
                },
            )
        translationPanelContainer.removeAllViews()
        translationPanelContainer.addView(
            translationPanel!!.view,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        translationPanelContainer.visibility = View.VISIBLE
        toolsBar.visibility = View.GONE
        suggestionBar.visibility = View.GONE
        hotkeysScroller.visibility = View.GONE
    }

    private fun dismissTranslationPanel() {
        if (!::translationPanelContainer.isInitialized) return
        translationPanelContainer.removeAllViews()
        translationPanelContainer.visibility = View.GONE
        translationPanel = null
        if (::toolsBar.isInitialized) showToolsStrip()
    }

    private fun commitTranslation(commit: TranslationCommit) {
        val ic = currentInputConnection ?: return
        when (commit) {
            is TranslationCommit.Paste -> {
                ic.setSelection(commit.cursor, commit.cursor)
                ic.commitText(commit.text, 1)
                selectionStart = commit.cursor + commit.text.length
                selectionEnd = selectionStart
            }
            is TranslationCommit.Reply -> {
                if (
                    commit.selectionStart != selectionStart ||
                    commit.selectionEnd != selectionEnd ||
                    commit.selectionStart < 0 ||
                    commit.selectionEnd <= commit.selectionStart
                ) {
                    Toast.makeText(
                        this,
                        getString(R.string.translation_selection_unavailable),
                        Toast.LENGTH_SHORT,
                    ).show()
                    return
                }
                ic.setSelection(commit.selectionStart, commit.selectionEnd)
                ic.commitText(commit.text, 1)
                selectionStart = commit.selectionStart + commit.text.length
                selectionEnd = selectionStart
            }
        }
        resetTypingState()
        refreshSuggestionsIfReady()
        dismissTranslationPanel()
    }

    private fun toggleHotkeys() {
        hotkeysScroller.visibility =
            if (hotkeysScroller.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    // ---- cursor row ----

    private fun wireCursorRow(root: View) {
        wireCursorButton(root.findViewById(R.id.btnCursorLeft), KeyEvent.KEYCODE_DPAD_LEFT)
        wireCursorButton(root.findViewById(R.id.btnCursorRight), KeyEvent.KEYCODE_DPAD_RIGHT)
        wireCursorButton(root.findViewById(R.id.btnCursorUp), KeyEvent.KEYCODE_DPAD_UP)
        wireCursorButton(root.findViewById(R.id.btnCursorDown), KeyEvent.KEYCODE_DPAD_DOWN)
    }

    private fun wireCursorButton(button: ImageButton, code: Int) {
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startCursorRepeat(code)
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    stopCursorRepeat()
                    true
                }
                else -> true
            }
        }
    }

    private fun startCursorRepeat(code: Int) {
        stopCursorRepeat()
        val session = inputSession
        val token = cursorRepeatController.start(session, code)
        sendDpad(code)
        val runnable =
            object : Runnable {
                private var scheduledToken: RepeatToken = token

                override fun run() {
                    if (currentInputConnection == null) {
                        cursorRepeatController.stop()
                        cursorRepeatRunnable = null
                        return
                    }
                    val next = cursorRepeatController.next(scheduledToken, inputSession)
                    if (next == null) {
                        cursorRepeatRunnable = null
                        return
                    }
                    scheduledToken = next
                    sendDpad(next.action)
                    cursorRepeatHandler.postDelayed(this, next.delayMs)
                }
            }
        cursorRepeatRunnable = runnable
        cursorRepeatHandler.postDelayed(runnable, token.delayMs)
    }

    private fun stopCursorRepeat() {
        cursorRepeatRunnable?.let(cursorRepeatHandler::removeCallbacks)
        cursorRepeatRunnable = null
        cursorRepeatController.stop()
    }

    private fun sendDpad(code: Int) {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private companion object {
        const val CURSOR_REPEAT_INITIAL_DELAY_MS = 350L
        const val CURSOR_REPEAT_INTERVAL_MS = 70L
    }

    // ---- suggestions ----

    private fun wireSuggestions() {
        val views = listOf(suggestion1, suggestion2, suggestion3)
        views.forEach { tv ->
            tv.setOnClickListener { pickSuggestion(tv.text.toString()) }
        }
    }

    private fun refreshSuggestions() {
        val typed = currentWord.toString()
        val list =
            if (
                KeyboardPreferences.getBoolean(this, KeyboardPreferences.EMOJI_PREDICTIONS) &&
                    typed.startsWith(":")
            ) {
                emojiSuggestions(typed.removePrefix(":"))
            } else {
                suggestionEngine.suggest(typed, previousWord)
            }
        val views = listOf(suggestion1, suggestion2, suggestion3)
        views.forEachIndexed { i, tv -> tv.text = list.getOrNull(i) ?: "" }
    }

    private fun emojiSuggestions(query: String): List<String> {
        val matches =
            when {
                "heart" in query -> listOf("❤️", "💖", "💙")
                "fire" in query -> listOf("🔥", "✨", "💯")
                "smile" in query -> listOf("😀", "😄", "😊")
                "thumb" in query -> listOf("👍", "👏", "🙌")
                else -> EmojiData.all
            }
        return matches.take(3)
    }

    private fun pickSuggestion(word: String) {
        if (word.isEmpty()) return
        val ic = currentInputConnection ?: return
        if (currentWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
        }
        ic.commitText("$word ", 1)
        learnWordIfAllowed(word)
        previousWord = word
        currentWord.clear()
        refreshSuggestions()
    }

    // ---- KeyboardView.OnKeyListener ----

    override fun onKey(key: Key, outputChar: String) {
        val ic = currentInputConnection ?: return

        if (
            (hotkeyController.isCtrlArmed() || hotkeyController.isAltArmed()) &&
                outputChar.length == 1 &&
                outputChar[0].isLetter()
        ) {
            hotkeyController.sendCharWithModifiers(outputChar[0])
            return
        }

        when (key.type) {
            KeyType.CHAR -> {
                lastAutocorrectOriginal = null
                lastAutocorrectReplacement = null
                ic.commitText(outputChar, 1)
                currentWord.append(outputChar)
                maybeAutocorrectLastChar(ic)
            }
            KeyType.SPACE -> {
                val now = System.currentTimeMillis()
                if (
                    KeyboardPreferences.getBoolean(this, KeyboardPreferences.QUICK_PERIOD) &&
                        now - lastSpaceTime in 80..500 &&
                        currentWord.isEmpty()
                ) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    keyboardView.setShiftState(true)
                } else if (currentWord.isNotEmpty() && replaceWithPrediction(ic, " ")) {
                    // The replacement already includes the trailing space.
                } else {
                    ic.commitText(" ", 1)
                }
                if (currentWord.isNotEmpty()) {
                    learnWordIfAllowed(currentWord.toString())
                    previousWord = currentWord.toString()
                }
                lastSpaceTime = now
                currentWord.clear()
                refreshSuggestions()
            }
            KeyType.COMMA,
            KeyType.PERIOD -> {
                val suffix =
                    outputChar +
                        if (KeyboardPreferences.getBoolean(this, KeyboardPreferences.AUTO_SPACE))
                            " "
                        else ""
                if (currentWord.isNotEmpty() && replaceWithPrediction(ic, suffix)) {
                    // The replacement already includes punctuation.
                } else {
                    ic.commitText(suffix, 1)
                }
                if (currentWord.isNotEmpty()) {
                    learnWordIfAllowed(currentWord.toString())
                    previousWord = currentWord.toString()
                    currentWord.clear()
                }
                if (key.type == KeyType.PERIOD) {
                    // simple auto-cap after sentence end
                    if (
                        KeyboardPreferences.getBoolean(
                            this,
                            KeyboardPreferences.AUTO_CAPITALIZE,
                        )
                    ) {
                        keyboardView.setShiftState(true)
                    }
                }
            }
            else -> Unit
        }
        refreshSuggestions()
    }

    private fun refreshSuggestionsIfReady() {
        if (::suggestion1.isInitialized) refreshSuggestions()
    }

    private fun replaceWithPrediction(ic: InputConnection, suffix: String): Boolean {
        val autocorrect = KeyboardPreferences.getBoolean(this, KeyboardPreferences.AUTOCORRECT)
        val quickPrediction =
            KeyboardPreferences.getBoolean(this, KeyboardPreferences.QUICK_PREDICTION_INSERT)
        if (!autocorrect && !quickPrediction) {
            return false
        }
        val replacement =
            if (autocorrect) {
                suggestionEngine.autocorrect(currentWord.toString())
            } else {
                suggestionEngine.suggest(currentWord.toString(), previousWord).getOrNull(1)
            } ?: return false
        if (
            replacement.isBlank() || replacement.equals(currentWord.toString(), ignoreCase = true)
        ) {
            return false
        }
        ic.deleteSurroundingText(currentWord.length, 0)
        ic.commitText(replacement + suffix, 1)
        lastAutocorrectOriginal = currentWord.toString()
        lastAutocorrectReplacement = replacement + suffix
        learnWordIfAllowed(replacement)
        return true
    }

    private fun learnWordIfAllowed(word: String) {
        if (shouldLearnWord(KeyboardPreferences.isIncognitoMode(this), word)) {
            suggestionEngine.learn(word)
        }
    }

    /**
     * Retained as a hook for future per-character correction; boundary correction happens below.
     */
    private fun maybeAutocorrectLastChar(ic: InputConnection) = Unit

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        if (
            KeyboardPreferences.getBoolean(this, KeyboardPreferences.UNDO_AUTOCORRECT) &&
                canUndoAutocorrect(
                    lastAutocorrectReplacement,
                    ic.getTextBeforeCursor(128, 0)?.toString().orEmpty(),
                ) &&
                lastAutocorrectOriginal != null &&
                lastAutocorrectReplacement != null
        ) {
            ic.deleteSurroundingText(lastAutocorrectReplacement!!.length, 0)
            ic.commitText(lastAutocorrectOriginal, 1)
            lastAutocorrectOriginal = null
            lastAutocorrectReplacement = null
            refreshSuggestions()
            return
        }
        ic.deleteSurroundingText(1, 0)
        if (currentWord.isNotEmpty()) currentWord.deleteCharAt(currentWord.length - 1)
        refreshSuggestions()
    }

    override fun onEnter() {
        if (KeyboardPreferences.getBoolean(this, KeyboardPreferences.EMOJI_ON_ENTER) &&
            isConversationEditor(currentInputEditorInfo)
        ) {
            onEmoji()
            return
        }
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (
            action != null &&
                action != EditorInfo.IME_ACTION_NONE &&
                action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    override fun onShiftToggled(shiftOn: Boolean, capsLock: Boolean) {
        // KeyboardView already redraws itself; hook left here for future auto-cap integration.
    }

    override fun onSwitchToSymbols() = Unit

    override fun onSwitchToLetters() = Unit

    override fun onEmoji() {
        if (emojiPanelContainer.visibility == View.VISIBLE) {
            emojiPanel?.dismiss()
            return
        }
        emojiPanel =
            EmojiPanel(this) { emoji ->
                currentInputConnection?.commitText(emoji, 1)
                resetTypingState()
                refreshSuggestionsIfReady()
            }
        AppLog.i("NovaBoardService", "Emoji panel opened")
        emojiPanel?.show(emojiPanelContainer)
    }

    override fun onCursorMove(direction: Int) {
        sendDpad(if (direction > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
    }

    override fun onQuickDelete() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(128, 0)?.toString().orEmpty()
        val count = previousWordDeletionCount(before)
        if (count > 0) {
            ic.deleteSurroundingText(count, 0)
            currentWord.clear()
            refreshSuggestions()
        }
    }

    override fun onGestureWord(path: String) {
        val ic = currentInputConnection ?: return
        val word =
            suggestionEngine.suggest(path, previousWord)
                .firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: path
        val suffix =
            if (KeyboardPreferences.getBoolean(this, KeyboardPreferences.AUTO_SPACE)) " " else ""
        ic.commitText(word + suffix, 1)
        learnWordIfAllowed(word)
        previousWord = word
        currentWord.clear()
        refreshSuggestionsIfReady()
    }

    // ---- voice typing ----

    private fun toggleVoiceInput() {
        if (listening) stopVoiceInput() else startVoiceInput()
    }

    private fun startVoiceInput() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Grant microphone access in NovaBoard settings first", Toast.LENGTH_LONG)
                .show()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_REQUEST_MICROPHONE, true),
            )
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(
                    this,
                    "Speech recognition isn't available on this device",
                    Toast.LENGTH_SHORT,
                )
                .show()
            return
        }
        stopVoiceInput()
        val session = inputSession
        val recognizerGeneration = ++voiceRecognizerGeneration
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    listening = false
                }

                override fun onResults(results: Bundle?) {
                    if (
                        !acceptsInputSessionResult(
                            resultSession = session,
                            activeSession = inputSession,
                            resultRecognizer = recognizerGeneration,
                            activeRecognizer = voiceRecognizerGeneration,
                        ) || recognizer !== speechRecognizer
                    ) {
                        recognizer.destroy()
                        return
                    }
                    val text =
                        results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                    if (!text.isNullOrBlank()) currentInputConnection?.commitText("$text ", 1)
                    listening = false
                    recognizer.destroy()
                    speechRecognizer = null
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
        )
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
        runCatching { recognizer.startListening(intent) }
            .onSuccess { listening = true }
            .onFailure {
                AppLog.w("NovaBoardService", "Voice recognition could not start", it)
                stopVoiceInput()
                Toast.makeText(this, "Voice typing could not start", Toast.LENGTH_SHORT).show()
            }
    }

    private fun stopVoiceInput() {
        voiceRecognizerGeneration++
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        listening = false
    }

    private fun isConversationEditor(info: EditorInfo?): Boolean {
        return info?.inputType?.let(::isConversationEditorInputType) == true
    }
}
