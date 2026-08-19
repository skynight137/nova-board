package com.novaboard.ime.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
import com.novaboard.ime.model.Key
import com.novaboard.ime.model.KeyRow
import com.novaboard.ime.model.KeyType
import com.novaboard.ime.model.KeyboardLayouts
import com.novaboard.ime.model.KeyboardPage

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

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var popup: PopupWindow? = null
    private var lastDoubleTapTime = 0L

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY && availableHeight > 0) {
            measuredRowHeight =
                (availableHeight / page.rows.size.toFloat()).coerceIn(
                    48 * resources.displayMetrics.density,
                    64 * resources.displayMetrics.density,
                )
        }
        val height = (rowHeight * page.rows.size).toInt()
        setMeasuredDimension(width, height)
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

    fun setShiftState(on: Boolean, lock: Boolean = false) {
        shiftOn = on
        capsLock = lock
        invalidate()
    }

    private fun rebuildHitRects(width: Int) {
        val hits = mutableListOf<Hit>()
        page.rows.forEachIndexed { rowIndex, row: KeyRow ->
            val totalWeight = row.keys.sumOf { it.flexWeight.toDouble() }.toFloat()
            var x = 0f
            val top = rowIndex * rowHeight
            row.keys.forEach { key ->
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
                    hit == pressedHit -> pressedPaint
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
                invalidate()
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                schedulePopup(hit)
            }
            MotionEvent.ACTION_MOVE -> {
                val hit = hitAt(event.x, event.y)
                if (hit != pressedHit) {
                    cancelPopup()
                    pressedHit = hit
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                cancelPopup()
                pressedHit?.let { handleKeyUp(it.key) }
                pressedHit = null
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelPopup()
                pressedHit = null
                invalidate()
            }
        }
        return true
    }

    private fun hitAt(x: Float, y: Float): Hit? = hitRects.firstOrNull { it.rect.contains(x, y) }

    private fun schedulePopup(hit: Hit) {
        if (hit.key.popupChars.isEmpty()) return
        val runnable = Runnable { showPopup(hit) }
        longPressRunnable = runnable
        longPressHandler.postDelayed(runnable, 350)
    }

    private fun cancelPopup() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
        popup?.dismiss()
        popup = null
    }

    /** Long-press symbol picker: a row of alternate characters above the key. */
    private fun showPopup(hit: Hit) {
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
            loc[1] + hit.rect.top.toInt() - 100,
        )
        popup = pw
    }

    private fun handleKeyUp(key: Key) {
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
                setPage(KeyboardLayouts.symbols)
                listener?.onSwitchToSymbols()
            }
            KeyType.LETTERS -> {
                setPage(KeyboardLayouts.letters)
                listener?.onSwitchToLetters()
            }
            KeyType.EMOJI -> listener?.onEmoji()
            KeyType.SWITCH_NUMBER_ROW -> Unit
        }
        invalidate()
    }
}
