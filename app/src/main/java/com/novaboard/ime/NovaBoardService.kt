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
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.novaboard.ime.clipboard.ClipType
import com.novaboard.ime.clipboard.ClipboardHistoryManager
import com.novaboard.ime.clipboard.ClipboardItem
import com.novaboard.ime.clipboard.ClipboardPanel
import com.novaboard.ime.editing.canUndoAutocorrect
import com.novaboard.ime.editing.previousWordDeletionCount
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
import com.novaboard.ime.translation.TranslationRequest
import com.novaboard.ime.translation.TranslationResultActivity
import com.novaboard.ime.translation.shouldAcceptTranslationResult
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

    private lateinit var clipboardHistory: ClipboardHistoryManager
    private val suggestionEngine = SuggestionEngine()
    private val hotkeyController = HotkeyController { currentInputConnection }
    private var clipboardPanel: ClipboardPanel? = null
    private var emojiPanel: EmojiPanel? = null
    private var lastSpaceTime = 0L
    private var lastAutocorrectOriginal: String? = null
    private var lastAutocorrectReplacement: String? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false
    private var inputSession = 0L
    private var translationRequestId = 0L
    private var pendingTranslation: TranslationRequest? = null
    private var selectionStart = -1
    private var selectionEnd = -1
    private val cursorRepeatHandler = Handler(Looper.getMainLooper())
    private var cursorRepeatRunnable: Runnable? = null

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
        emojiPanelContainer = root.findViewById(R.id.emojiPanelContainer)

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
        if (intent?.action == TranslationResultActivity.ACTION_TRANSLATION_RESULT) {
            handleTranslationResult(intent)
        }
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
            pendingTranslation = null
        }
        selectionStart = newSelStart
        selectionEnd = newSelEnd
        val textBeforeCursor = currentInputConnection?.getTextBeforeCursor(128, 0)?.toString().orEmpty()
        val ownWordStillAtCursor =
            currentWord.isNotEmpty() && textBeforeCursor.endsWith(currentWord.toString())
        if ((oldSelStart != newSelStart || oldSelEnd != newSelEnd) && !ownWordStillAtCursor) {
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
        pendingTranslation = null
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
            val intent =
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
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
        if (text.isNullOrBlank() || start < 0 || end <= start) {
            Toast.makeText(this, "Select text first", Toast.LENGTH_SHORT).show()
            return
        }
        val request =
            TranslationRequest(
                session = inputSession,
                requestId = ++translationRequestId,
                selectionStart = start,
                selectionEnd = end,
            )
        pendingTranslation = request
        startActivity(
            Intent(this, TranslationResultActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(TranslationResultActivity.EXTRA_SESSION, request.session)
                putExtra(TranslationResultActivity.EXTRA_REQUEST_ID, request.requestId)
                putExtra(TranslationResultActivity.EXTRA_SELECTION_START, start)
                putExtra(TranslationResultActivity.EXTRA_SELECTION_END, end)
                putExtra(TranslationResultActivity.EXTRA_TEXT, text)
            },
        )
    }

    private fun handleTranslationResult(intent: Intent) {
        val request =
            TranslationRequest(
                session = intent.getLongExtra(TranslationResultActivity.EXTRA_SESSION, -1L),
                requestId = intent.getLongExtra(TranslationResultActivity.EXTRA_REQUEST_ID, -1L),
                selectionStart =
                    intent.getIntExtra(TranslationResultActivity.EXTRA_SELECTION_START, -1),
                selectionEnd =
                    intent.getIntExtra(TranslationResultActivity.EXTRA_SELECTION_END, -1),
            )
        val result = intent.getStringExtra(TranslationResultActivity.EXTRA_RESULT)
        val accepted =
            shouldAcceptTranslationResult(
                request,
                inputSession,
                pendingTranslation?.requestId ?: -1L,
                selectionStart,
                selectionEnd,
            )
        pendingTranslation = null
        if (!accepted || result.isNullOrBlank()) {
            Toast.makeText(this, "Translation is unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        val ic = currentInputConnection
        if (ic == null) {
            Toast.makeText(this, "Translation is unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        ic.setSelection(request.selectionStart, request.selectionEnd)
        ic.commitText(result, 1)
        selectionStart = request.selectionStart + result.length
        selectionEnd = selectionStart
        resetTypingState()
        refreshSuggestionsIfReady()
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
        sendDpad(code)
        val runnable =
            object : Runnable {
                override fun run() {
                    if (session != inputSession || currentInputConnection == null) {
                        cursorRepeatRunnable = null
                        return
                    }
                    sendDpad(code)
                    cursorRepeatHandler.postDelayed(this, CURSOR_REPEAT_INTERVAL_MS)
                }
            }
        cursorRepeatRunnable = runnable
        cursorRepeatHandler.postDelayed(runnable, CURSOR_REPEAT_INITIAL_DELAY_MS)
    }

    private fun stopCursorRepeat() {
        cursorRepeatRunnable?.let(cursorRepeatHandler::removeCallbacks)
        cursorRepeatRunnable = null
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
                    if (session != inputSession || recognizer !== speechRecognizer) {
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
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        listening = false
    }

    private fun isConversationEditor(info: EditorInfo?): Boolean {
        return info?.inputType?.let(::isConversationEditorInputType) == true
    }
}
