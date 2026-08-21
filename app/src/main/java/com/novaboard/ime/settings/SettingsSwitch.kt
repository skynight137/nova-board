package com.novaboard.ime.settings

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import com.novaboard.ime.R

/** A native-View equivalent of the compact outlined switch used in the settings design. */
class SettingsSwitch(context: Context) : View(context) {
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.5f
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()
    private var knobPosition = 0f
    private var animator: ValueAnimator? = null
    private var isChecked = false

    var onCheckedChange: ((Boolean) -> Unit)? = null

    var checked: Boolean
        get() = isChecked
        set(value) {
            setChecked(value, animate = false)
        }

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        applyColors(
            color(R.color.settings_switch_off_track),
            color(R.color.settings_switch_off_border),
            color(R.color.settings_switch_off_knob),
            checked = false,
        )
        setOnClickListener { setChecked(!isChecked, animate = true) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val width = 52f * density
        val height = 30f * density
        val left = (this.width - width) / 2f
        val top = (this.height - height) / 2f
        val radius = height / 2f
        val knobSize = height - 6f * density
        val knobTravel = width - knobSize - 6f * density

        trackRect.set(left, top, left + width, top + height)
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)
        canvas.drawRoundRect(trackRect, radius, radius, borderPaint)
        canvas.drawCircle(
            left + 3f * density + knobTravel * knobPosition + knobSize / 2f,
            top + 3f * density + knobSize / 2f,
            knobSize / 2f,
            knobPaint,
        )
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Switch::class.java.name
        info.isCheckable = true
        info.isChecked = isChecked
    }

    private fun setChecked(value: Boolean, animate: Boolean) {
        if (isChecked == value && animator == null) return
        isChecked = value
        val target = if (value) 1f else 0f
        val start = knobPosition
        val trackStart = color(R.color.settings_switch_off_track)
        val trackEnd = color(R.color.settings_switch_on_track)
        val borderStart = color(R.color.settings_switch_off_border)
        val borderEnd = color(R.color.settings_switch_on_border)
        val knobStart = color(R.color.settings_switch_off_knob)
        val knobEnd = color(R.color.settings_switch_on_knob)

        animator?.cancel()
        if (!animate) {
            knobPosition = target
            applyColors(trackEnd, borderEnd, knobEnd, value)
        } else {
            animator =
                ValueAnimator.ofFloat(start, target).apply {
                    duration = 200
                    addUpdateListener {
                        knobPosition = it.animatedValue as Float
                        trackPaint.color =
                            ArgbEvaluator().evaluate(knobPosition, trackStart, trackEnd) as Int
                        borderPaint.color =
                            ArgbEvaluator().evaluate(knobPosition, borderStart, borderEnd) as Int
                        knobPaint.color =
                            ArgbEvaluator().evaluate(knobPosition, knobStart, knobEnd) as Int
                        invalidate()
                    }
                    doOnEnd { animator = null }
                    start()
                }
        }
        invalidate()
        refreshDrawableState()
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED)
        onCheckedChange?.invoke(value)
    }

    private fun applyColors(track: Int, border: Int, knob: Int, checked: Boolean) {
        trackPaint.color = if (checked) color(R.color.settings_switch_on_track) else track
        borderPaint.color = if (checked) color(R.color.settings_switch_on_border) else border
        knobPaint.color = if (checked) color(R.color.settings_switch_on_knob) else knob
    }

    private fun color(id: Int): Int = ContextCompat.getColor(context, id)

    private fun ValueAnimator.doOnEnd(action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = action()
        })
    }
}