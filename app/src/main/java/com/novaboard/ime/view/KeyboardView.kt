package com.novaboard.ime.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.novaboard.ime.R
import com.novaboard.ime.gesture.recognizeGestureWord
import com.novaboard.ime.model.Key
import com.novaboard.ime.model.KeyRow
import com.novaboard.ime.model.KeyType
import com.novaboard.ime.model.KeyboardLayouts
import com.novaboard.ime.model.KeyboardPage
import com.novaboard.ime.settings.KeyboardPreferences
import com.novaboard.ime.settings.GestureMode

/**
 * Renders the number row, QWERTY letters and the bottom row (matches sections 3-4 of the requested
 * layout; the tools/suggestion strip and cursor-arrow row live in the surrounding XML container
 * since they're simple/toggleable).
 */
class KeyboardView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface OnKeyListener {
        fun onKey(key: Key, outputChar: String)

        fun onBackspace()

        fun onEnter()

        fun onShiftToggled(shiftOn: Boolean, capsLock: Boolean)

        fun onSwitchToSymbols()

        fun onSwitchToLetters()

        fun onEmoji()

        fun onCursorMove(direction: Int)

        fun onQuickDelete()

        fun onGestureWord(path: String)
    }

    var listener: OnKeyListener? = null

    private var page: KeyboardPage = KeyboardLayouts.letters
    private var shiftOn = false
    private var capsLock = false

    private var measuredRowHeight = 58 * resources.displayMetrics.density

    private val rowHeight
        get() = measuredRowHeight

    private val keyMargin = 3 * resources.displayMetrics.density

    private val keyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeColor(R.color.kb_key_bg)
        }
    private val specialKeyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeColor(R.color.kb_key_bg_special)
        }
    private val pressedPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeColor(R.color.kb_key_pressed)
        }
    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeColor(R.color.kb_key_text)
            textSize = 20 * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
        }

    private fun themeColor(resId: Int) = resources.getColor(resId, context.theme)

    /** Flattened hit-test geometry, rebuilt whenever the page or view size changes. */
    private data class Hit(val key: Key, val rect: RectF)

    private var hitRects: List<Hit> = emptyList()
    private var pressedHit: Hit? = null
    private val pointerHits = mutableMapOf<Int, Hit>()
    private var primaryPointerId = MotionEvent.INVALID_POINTER_ID

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var popup: PopupWindow? = null
    private val keyPreviewPopups = mutableMapOf<Int, PopupWindow>()
    private var lastDoubleTapTime = 0L
    private var longPressDuration = 350L
    private var longPressSymbols = true
    private var keyPopups = true
    private var keypressSound = false
    private var keypressVibration = false
    private var showEmojiKey = true
    private var cursorControl = true
    private var quickDelete = true
    private var quickDeleteTriggered = false
    private var cursorDragLastX = 0f
    private var gestureActive = false
    private var gestureCancelled = false
    private var gestureDistance = 0f
    private var gestureLastX = 0f
    private var gestureLastY = 0f
    private val gestureLabels = mutableListOf<String>()
    private var deleteRepeatRunnable: Runnable? = null
    private var deleteRepeatTriggered = false
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var toneGenerator: ToneGenerator? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        if (heightMode != MeasureSpec.UNSPECIFIED && availableHeight > 0) {
            val density = resources.displayMetrics.density
            val preferredMinimum = 48 * density
            val compactMinimum = 36 * density
            val maximum = 64 * density
            val availablePerRow = availableHeight / page.rows.size.toFloat()
            measuredRowHeight =
                if (availablePerRow >= preferredMinimum) {
                    availablePerRow.coerceAtMost(maximum)
                } else {
                    availablePerRow.coerceAtLeast(compactMinimum.coerceAtMost(availablePerRow))
                }
        }
        val desiredHeight = (rowHeight * page.rows.size).toInt()
        val measuredHeight =
            if (heightMode == MeasureSpec.AT_MOST && availableHeight > 0) {
                desiredHeight.coerceAtMost(availableHeight)
            } else {
                desiredHeight
            }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), measuredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildHitRects(w)
    }

    /** Swap the visible page (letters <-> symbols) and re-layout. */
    fun setPage(newPage: KeyboardPage) {
        page = newPage
        rebuildHitRects(width.takeIf { it > 0 } ?: return)
        requestLayout()
        invalidate()
    }

    fun applyPreferences() {
        longPressDuration = KeyboardPreferences.getLongPressDuration(context).toLong()
        longPressSymbols =
            KeyboardPreferences.getBoolean(context, KeyboardPreferences.LONG_PRESS_SYMBOLS)
        keyPopups = KeyboardPreferences.getBoolean(context, KeyboardPreferences.KEY_POPUPS)
        val keyTextSize =
            if (KeyboardPreferences.getBoolean(context, KeyboardPreferences.LARGE_KEY_TEXT)) {
                22
            } else {
                20
            }
        textPaint.textSize = keyTextSize * resources.displayMetrics.scaledDensity
        keypressSound =
            KeyboardPreferences.getBoolean(context, KeyboardPreferences.SOUND_ON_KEYPRESS)
        keypressVibration =
            KeyboardPreferences.getBoolean(context, KeyboardPreferences.VIBRATION_ON_KEYPRESS)
        showEmojiKey =
            KeyboardPreferences.getBoolean(context, KeyboardPreferences.DEDICATED_EMOJI_KEY)
        cursorControl = KeyboardPreferences.getBoolean(context, KeyboardPreferences.CURSOR_CONTROL)
        quickDelete = KeyboardPreferences.getBoolean(context, KeyboardPreferences.QUICK_DELETE)
        rebuildHitRects(width.takeIf { it > 0 } ?: return)
        invalidate()
    }

    fun setShiftState(on: Boolean, lock: Boolean = false) {
        shiftOn = on
        capsLock = lock
        invalidate()
    }

    fun cancelInteractions() {
        cancelPopup()
        cancelDeleteRepeat()
        clearGestureState()
        pressedHit = null
        pointerHits.clear()
        primaryPointerId = MotionEvent.INVALID_POINTER_ID
        quickDeleteTriggered = false
        deleteRepeatTriggered = false
        invalidate()
    }

    private fun rebuildHitRects(width: Int) {
        val hits = mutableListOf<Hit>()
        page.rows.forEachIndexed { rowIndex, row: KeyRow ->
            val visibleKeys = row.keys.filter { it.type != KeyType.EMOJI || showEmojiKey }
            val totalWeight = visibleKeys.sumOf { it.flexWeight.toDouble() }.toFloat()
            var x = 0f
            val top = rowIndex * rowHeight
            visibleKeys.forEach { key ->
                val keyWidth = (key.flexWeight / totalWeight) * width
                hits +=
                    Hit(
                        key,
                        RectF(
                            x + keyMargin,
                            top + keyMargin,
                            x + keyWidth - keyMargin,
                            top + rowHeight - keyMargin,
                        ),
                    )
                x += keyWidth
            }
        }
        hitRects = hits
    }

    override fun onDraw(canvas: Canvas) {
        for (hit in hitRects) {
            val isSpecial =
                hit.key.type != KeyType.CHAR &&
                    hit.key.type != KeyType.COMMA &&
                    hit.key.type != KeyType.PERIOD
            val paint =
                when {
                    hit == pressedHit || pointerHits.values.any { it === hit } -> pressedPaint
                    isSpecial -> specialKeyPaint
                    else -> keyPaint
                }
            canvas.drawRoundRect(hit.rect, 8f, 8f, paint)

            val label = displayLabel(hit.key)
            if (label.isNotEmpty()) {
                val cy = hit.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(label, hit.rect.centerX(), cy, textPaint)
            }
        }
    }

    private fun displayLabel(key: Key): String {
        if (key.type != KeyType.CHAR || key.label.length != 1) return key.label
        val c = key.label[0]
        return if (shiftOn || capsLock) c.uppercaseChar().toString()
        else c.lowercaseChar().toString()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = hitAt(event.x, event.y) ?: return true
                pressedHit = hit
                primaryPointerId = event.getPointerId(0)
                pointerHits[primaryPointerId] = hit
                deleteRepeatTriggered = false
                if (hit.key.type == KeyType.BACKSPACE) {
                    scheduleDeleteRepeat()
                }
                if (
                    KeyboardPreferences.getGestureMode(context) == GestureMode.GESTURES &&
                        hit.key.type == KeyType.CHAR &&
                        hit.key.label.singleOrNull()?.isLetter() == true
                ) {
                    gestureActive = true
                    gestureCancelled = false
                    gestureDistance = 0f
                    gestureLastX = event.x
                    gestureLastY = event.y
                    gestureLabels.clear()
                    gestureLabels += hit.key.label
                }
                invalidate()
                cursorDragLastX = event.x
                quickDeleteTriggered = false
                if (keypressVibration) {
                    performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                    )
                }
                if (keyPopups) {
                    showKeyPreview(primaryPointerId, hit)
                }
                schedulePopup(hit)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val hit = hitAt(event.getX(index), event.getY(index))
                if (hit != null) {
                    val pointerId = event.getPointerId(index)
                    pointerHits[pointerId] = hit
                    if (keyPopups) showKeyPreview(pointerId, hit)
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val hit = hitAt(event.x, event.y)
                if (gestureActive) {
                    gestureDistance +=
                        kotlin.math.hypot(
                            (event.x - gestureLastX).toDouble(),
                            (event.y - gestureLastY).toDouble(),
                        ).toFloat()
                    gestureLastX = event.x
                    gestureLastY = event.y
                    val key = hit?.key
                    if (key?.type == KeyType.CHAR && key.label.singleOrNull()?.isLetter() == true) {
                        gestureLabels += key.label
                    } else {
                        gestureCancelled = true
                    }
                    cancelActivePopup(primaryPointerId)
                }
                if (
                    !gestureActive &&
                    quickDelete &&
                        !quickDeleteTriggered &&
                        pressedHit?.key?.type == KeyType.BACKSPACE &&
                        event.x - cursorDragLastX < -18
                ) {
                    quickDeleteTriggered = true
                    cancelDeleteRepeat()
                    listener?.onQuickDelete()
                    cancelActivePopup(primaryPointerId)
                }
                if (
                    !gestureActive &&
                    cursorControl &&
                        pressedHit?.key?.type == KeyType.SPACE &&
                        kotlin.math.abs(event.x - cursorDragLastX) >= 18
                ) {
                    listener?.onCursorMove(if (event.x > cursorDragLastX) 1 else -1)
                    cursorDragLastX = event.x
                }
                // Keep the original key during small finger jitter. Clearing the active hit when
                // the pointer briefly leaves a key causes a normal tap to be dropped on ACTION_UP.
                if (hit != null && hit != pressedHit) {
                    cancelActivePopup(primaryPointerId)
                    pressedHit = hit
                    if (primaryPointerId != MotionEvent.INVALID_POINTER_ID) {
                        pointerHits[primaryPointerId] = hit
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                dismissKeyPreview(pointerId)
                pointerHits.remove(pointerId)?.let { hit ->
                    if (hit.key.type != KeyType.BACKSPACE) {
                        handleKeyUp(hit.key)
                    }
                }
                if (pointerId == primaryPointerId) {
                    pressedHit = null
                }
            }
            MotionEvent.ACTION_UP -> {
                cancelPopup()
                cancelDeleteRepeat()
                if (pointerHits.size > 1 || pressedHit == null) {
                    pointerHits.values
                        .filter { it.key.type != KeyType.BACKSPACE }
                        .forEach { handleKeyUp(it.key) }
                } else if (gestureActive) {
                    val word =
                        recognizeGestureWord(
                            labels = gestureLabels,
                            distance = gestureDistance,
                            cancelled = gestureCancelled,
                        )
                    if (word != null) {
                        listener?.onGestureWord(word)
                    } else if (!gestureCancelled && gestureLabels.size == 1) {
                        pressedHit?.let { handleKeyUp(it.key) }
                    }
                    clearGestureState()
                } else if (!quickDeleteTriggered && !deleteRepeatTriggered) {
                    pressedHit?.let { handleKeyUp(it.key) }
                }
                pressedHit = null
                pointerHits.clear()
                primaryPointerId = MotionEvent.INVALID_POINTER_ID
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelPopup()
                cancelDeleteRepeat()
                clearGestureState()
                pressedHit = null
                pointerHits.clear()
                primaryPointerId = MotionEvent.INVALID_POINTER_ID
                invalidate()
            }
        }
        return true
    }

    private fun clearGestureState() {
        gestureActive = false
        gestureCancelled = false
        gestureDistance = 0f
        gestureLabels.clear()
    }

    private fun scheduleDeleteRepeat() {
        cancelDeleteRepeat()
        deleteRepeatTriggered = false
        val runnable =
            object : Runnable {
                override fun run() {
                    if (pressedHit?.key?.type != KeyType.BACKSPACE) return
                    deleteRepeatTriggered = true
                    if (quickDelete) {
                        listener?.onQuickDelete()
                    } else {
                        listener?.onBackspace()
                    }
                    longPressHandler.postDelayed(this, if (quickDelete) 250L else 90L)
                }
            }
        deleteRepeatRunnable = runnable
        longPressHandler.postDelayed(runnable, 450L)
    }

    private fun cancelDeleteRepeat() {
        deleteRepeatRunnable?.let(longPressHandler::removeCallbacks)
        deleteRepeatRunnable = null
    }

    private fun hitAt(x: Float, y: Float): Hit? = hitRects.firstOrNull { it.rect.contains(x, y) }

    private fun schedulePopup(hit: Hit) {
        if (!longPressSymbols || hit.key.popupChars.isEmpty()) return
        if (
            hit.key.type == KeyType.CHAR &&
                !KeyboardPreferences.getBoolean(context, KeyboardPreferences.ACCENTED_CHARACTERS)
        ) {
            return
        }
        val runnable = Runnable { showPopup(hit) }
        longPressRunnable = runnable
        longPressHandler.postDelayed(runnable, longPressDuration)
    }

    private fun showKeyPreview(pointerId: Int, hit: Hit) {
        val label = displayLabel(hit.key)
        if (label.isEmpty()) return
        dismissKeyPreview(pointerId)
        val preview =
            TextView(context).apply {
                text = label
                textSize = 28f
                gravity = android.view.Gravity.CENTER
                setTextColor(themeColor(R.color.kb_key_text))
                setBackgroundColor(themeColor(R.color.kb_key_bg_special))
                setPadding(22, 12, 22, 12)
            }
        val window =
            PopupWindow(
                preview,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                false,
            )
        val loc = IntArray(2)
        getLocationInWindow(loc)
        preview.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        window.showAtLocation(
            this,
            android.view.Gravity.NO_GRAVITY,
            loc[0] + hit.rect.centerX().toInt() - preview.measuredWidth / 2,
            popupTop(loc[1], hit.rect.top, preview.measuredHeight),
        )
        keyPreviewPopups[pointerId] = window
    }

    private fun cancelPopup() {
        cancelLongPressPopup()
        keyPreviewPopups.values.forEach(PopupWindow::dismiss)
        keyPreviewPopups.clear()
    }

    private fun cancelActivePopup(pointerId: Int) {
        cancelLongPressPopup()
        dismissKeyPreview(pointerId)
    }

    private fun cancelLongPressPopup() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
        popup?.dismiss()
        popup = null
    }

    private fun dismissKeyPreview(pointerId: Int) {
        keyPreviewPopups.remove(pointerId)?.dismiss()
    }

    /** Long-press symbol picker: a row of alternate characters above the key. */
    private fun showPopup(hit: Hit) {
        popup?.dismiss()
        popup = null
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(themeColor(R.color.kb_key_bg_special))
                setPadding(12, 12, 12, 12)
            }
        hit.key.popupChars.forEach { ch ->
            row.addView(
                TextView(context).apply {
                    text = ch
                    textSize = 20f
                    setTextColor(themeColor(R.color.kb_key_text))
                    setPadding(24, 8, 24, 8)
                    setOnClickListener {
                        listener?.onKey(hit.key, ch)
                        cancelPopup()
                    }
                }
            )
        }
        val pw =
            PopupWindow(
                row,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                false,
            )
        val loc = IntArray(2)
        getLocationInWindow(loc)
        pw.showAtLocation(
            this,
            android.view.Gravity.NO_GRAVITY,
            loc[0] + hit.rect.left.toInt(),
            popupTop(loc[1], hit.rect.top, row.measuredHeight),
        )
        popup = pw
    }

    private fun popupTop(windowTop: Int, keyTop: Float, popupHeight: Int): Int =
        (windowTop + keyTop.toInt() - popupHeight - (8 * resources.displayMetrics.density).toInt())
            .coerceAtLeast(windowTop)

    private fun handleKeyUp(key: Key) {
        if (keypressSound) {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
        }
        when (key.type) {
            KeyType.CHAR,
            KeyType.COMMA,
            KeyType.PERIOD -> {
                val out = displayLabel(key)
                listener?.onKey(key, out)
                if (shiftOn && !capsLock) {
                    shiftOn = false
                    listener?.onShiftToggled(false, false)
                }
            }
            KeyType.SHIFT -> {
                val now = System.currentTimeMillis()
                if (now - lastDoubleTapTime < 300) {
                    capsLock = true
                    shiftOn = true
                } else if (capsLock) {
                    capsLock = false
                    shiftOn = false
                } else {
                    shiftOn = !shiftOn
                }
                lastDoubleTapTime = now
                listener?.onShiftToggled(shiftOn, capsLock)
            }
            KeyType.BACKSPACE -> listener?.onBackspace()
            KeyType.ENTER -> listener?.onEnter()
            KeyType.SPACE -> listener?.onKey(key, " ")
            KeyType.SYMBOLS -> {
                setPage(
                    KeyboardLayouts.symbols(
                        KeyboardPreferences.getBoolean(
                            context,
                            KeyboardPreferences.SHOW_NUMBER_ROW,
                        ),
                    ),
                )
                listener?.onSwitchToSymbols()
            }
            KeyType.SYMBOLS_SECONDARY -> {
                setPage(
                    KeyboardLayouts.symbolsSecondary(
                        KeyboardPreferences.getBoolean(
                            context,
                            KeyboardPreferences.SHOW_NUMBER_ROW,
                        ),
                    ),
                )
                listener?.onSwitchToSymbols()
            }
            KeyType.LETTERS -> {
                setPage(
                    if (
                        KeyboardPreferences.getBoolean(
                            context,
                            KeyboardPreferences.SHOW_NUMBER_ROW,
                        )
                    ) {
                        KeyboardLayouts.letters
                    } else {
                        KeyboardLayouts.lettersWithoutNumberRow
                    },
                )
                listener?.onSwitchToLetters()
            }
            KeyType.EMOJI -> listener?.onEmoji()
            KeyType.SWITCH_NUMBER_ROW -> Unit
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelPopup()
        cancelDeleteRepeat()
        toneGenerator?.release()
        toneGenerator = null
        super.onDetachedFromWindow()
    }
}
