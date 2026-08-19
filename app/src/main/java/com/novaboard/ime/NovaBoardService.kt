package com.novaboard.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.novaboard.ime.clipboard.ClipboardHistoryManager
import com.novaboard.ime.clipboard.ClipboardItem
import com.novaboard.ime.clipboard.ClipboardPanel
import com.novaboard.ime.clipboard.ClipType
import com.novaboard.ime.emoji.EmojiPanel
import com.novaboard.ime.hotkeys.HotkeyController
import com.novaboard.ime.model.Key
import com.novaboard.ime.model.KeyType
import com.novaboard.ime.model.KeyboardLayouts
import com.novaboard.ime.settings.MainActivity
import com.novaboard.ime.suggestion.SuggestionEngine
import com.novaboard.ime.theme.ThemeManager
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

    private lateinit var clipboardHistory: ClipboardHistoryManager
    private val suggestionEngine = SuggestionEngine()
    private val hotkeyController = HotkeyController { currentInputConnection }
    private var clipboardPanel: ClipboardPanel? = null
    private var emojiPanel: EmojiPanel? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false

    private val currentWord = StringBuilder()
    private var previousWord: String? = null

    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyStored(this)
        clipboardHistory = ClipboardHistoryManager(this)
        clipboardHistory.start()
    }

    override fun onDestroy() {
        clipboardHistory.stop()
        speechRecognizer?.destroy()
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

        keyboardView.listener = this
        keyboardView.setPage(KeyboardLayouts.letters)

        hotkeyController.build(this, hotkeysRow) { /* modifier armed/disarmed, nothing extra to redraw */ }

        wireToggleChevrons(root)
        wireToolsBar(root)
        wireCursorRow(root)
        wireSuggestions()

        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentWord.clear()
        previousWord = null
        refreshSuggestions()
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
            Toast.makeText(this, "Language switching: hook up additional locales here", Toast.LENGTH_SHORT).show()
        }
        root.findViewById<ImageButton>(R.id.btnVoice).setOnClickListener { toggleVoiceInput() }
        root.findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
        }
        root.findViewById<ImageButton>(R.id.btnMore).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun openClipboard(root: View) {
        clipboardPanel = ClipboardPanel(this, clipboardHistory) { item -> pasteClipboardItem(item) }
        clipboardPanel?.show(root)
    }

    private fun pasteClipboardItem(item: ClipboardItem) {
        val ic = currentInputConnection ?: return
        when (item.type) {
            ClipType.TEXT -> ic.commitText(item.text ?: "", 1)
            ClipType.IMAGE -> Toast.makeText(this, "Image paste requires app support for InputContentInfo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleHotkeys() {
        hotkeysScroller.visibility = if (hotkeysScroller.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    // ---- cursor row ----

    private fun wireCursorRow(root: View) {
        root.findViewById<ImageButton>(R.id.btnCursorLeft).setOnClickListener { sendDpad(KeyEvent.KEYCODE_DPAD_LEFT) }
        root.findViewById<ImageButton>(R.id.btnCursorRight).setOnClickListener { sendDpad(KeyEvent.KEYCODE_DPAD_RIGHT) }
        root.findViewById<ImageButton>(R.id.btnCursorUp).setOnClickListener { sendDpad(KeyEvent.KEYCODE_DPAD_UP) }
        root.findViewById<ImageButton>(R.id.btnCursorDown).setOnClickListener { sendDpad(KeyEvent.KEYCODE_DPAD_DOWN) }
    }

    private fun sendDpad(code: Int) {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    // ---- suggestions ----

    private fun wireSuggestions() {
        val views = listOf(suggestion1, suggestion2, suggestion3)
        views.forEach { tv ->
            tv.setOnClickListener { pickSuggestion(tv.text.toString()) }
        }
    }

    private fun refreshSuggestions() {
        val list = suggestionEngine.suggest(currentWord.toString(), previousWord)
        val views = listOf(suggestion1, suggestion2, suggestion3)
        views.forEachIndexed { i, tv -> tv.text = list.getOrNull(i) ?: "" }
    }

    private fun pickSuggestion(word: String) {
        if (word.isEmpty()) return
        val ic = currentInputConnection ?: return
        if (currentWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
        }
        ic.commitText("$word ", 1)
        suggestionEngine.learn(word)
        previousWord = word
        currentWord.clear()
        refreshSuggestions()
    }

    // ---- KeyboardView.OnKeyListener ----

    override fun onKey(key: Key, outputChar: String) {
        val ic = currentInputConnection ?: return

        if ((hotkeyController.isCtrlArmed() || hotkeyController.isAltArmed()) && outputChar.length == 1 && outputChar[0].isLetter()) {
            hotkeyController.sendCharWithModifiers(outputChar[0])
            return
        }

        ic.commitText(outputChar, 1)

        when (key.type) {
            KeyType.CHAR -> {
                currentWord.append(outputChar)
                maybeAutocorrectLastChar(ic)
            }
            KeyType.SPACE, KeyType.COMMA, KeyType.PERIOD -> {
                if (currentWord.isNotEmpty()) {
                    suggestionEngine.learn(currentWord.toString())
                    previousWord = currentWord.toString()
                    currentWord.clear()
                }
                if (key.type == KeyType.PERIOD) {
                    // simple auto-cap after sentence end
                    keyboardView.setShiftState(true)
                }
            }
            else -> Unit
        }
        refreshSuggestions()
    }

    /** Word-level autocorrect fires once a boundary is typed elsewhere; here we just keep it simple
     *  and rely on the suggestion strip + explicit tap-to-accept, which is the safer default for
     *  a first pass (silently rewriting characters as you type is jarring without a real model). */
    private fun maybeAutocorrectLastChar(ic: InputConnection) = Unit

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(1, 0)
        if (currentWord.isNotEmpty()) currentWord.deleteCharAt(currentWord.length - 1)
        refreshSuggestions()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (action != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
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
        emojiPanel = EmojiPanel(this) { emoji ->
            currentInputConnection?.commitText(emoji, 1)
        }
        emojiPanel?.show(keyboardView)
    }

    // ---- voice typing ----

    private fun toggleVoiceInput() {
        if (listening) stopVoiceInput() else startVoiceInput()
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition isn't available on this device", Toast.LENGTH_SHORT).show()
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                listening = false
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) currentInputConnection?.commitText("$text ", 1)
                listening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
        listening = true
    }

    private fun stopVoiceInput() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        listening = false
    }
}
