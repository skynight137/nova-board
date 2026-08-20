package com.novaboard.ime.hotkeys

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView
import com.novaboard.ime.R

/**
 * CTRL/ALT/SHIFT are modifier toggles: tapping one arms it, and it's applied as metaState to the
 * *next* key press (e.g. CTRL then C sends Ctrl+C) then automatically clears, mirroring how
 * hardware-keyboard modifier chording feels on a software keyboard.
 */
class HotkeyController(private val getInputConnection: () -> InputConnection?) {

    private var ctrlArmed = false
    private var altArmed = false

    fun isCtrlArmed() = ctrlArmed

    fun isAltArmed() = altArmed

    fun disarm() {
        ctrlArmed = false
        altArmed = false
    }

    fun build(context: Context, row: LinearLayout, onModifierChanged: () -> Unit) {
        row.removeAllViews()
        row.addView(hotkeyButton(context, "ESC") { sendKey(KeyEvent.KEYCODE_ESCAPE) })
        row.addView(hotkeyButton(context, "TAB") { sendKey(KeyEvent.KEYCODE_TAB) })
        row.addView(
            toggleButton(context, "CTRL", { ctrlArmed }) {
                ctrlArmed = it
                onModifierChanged()
            }
        )
        row.addView(
            toggleButton(context, "ALT", { altArmed }) {
                altArmed = it
                onModifierChanged()
            }
        )
        row.addView(hotkeyButton(context, "DEL") { sendKey(KeyEvent.KEYCODE_FORWARD_DEL) })
        row.addView(hotkeyButton(context, "HOME") { sendKey(KeyEvent.KEYCODE_MOVE_HOME) })
        row.addView(hotkeyButton(context, "END") { sendKey(KeyEvent.KEYCODE_MOVE_END) })
        row.addView(hotkeyButton(context, "F5") { sendKey(KeyEvent.KEYCODE_F5) })
    }

    /**
     * Sends a plain character through as a key event with any armed modifiers applied, then clears
     * them.
     */
    fun sendCharWithModifiers(char: Char) {
        val normalized = char.lowercaseChar()
        val ic = getInputConnection()
        if (ctrlArmed && ic != null) {
            val handled =
                when (normalized) {
                    'a' -> ic.performContextMenuAction(android.R.id.selectAll)
                    'c' -> ic.performContextMenuAction(android.R.id.copy)
                    'x' -> ic.performContextMenuAction(android.R.id.cut)
                    'v' -> ic.performContextMenuAction(android.R.id.paste)
                    else -> false
                }
            if (handled) {
                ctrlArmed = false
                altArmed = false
                return
            }
        }
        var meta = 0
        if (ctrlArmed) meta = meta or KeyEvent.META_CTRL_ON
        if (altArmed) meta = meta or KeyEvent.META_ALT_ON
        val keyCode = KeyEvent.keyCodeFromString("KEYCODE_${char.uppercaseChar()}")
        if (meta != 0 && keyCode != KeyEvent.KEYCODE_UNKNOWN) {
            val now = android.os.SystemClock.uptimeMillis()
            val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta)
            ic?.sendKeyEvent(down)
            ic?.sendKeyEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP))
        } else {
            ic?.commitText(char.toString(), 1)
        }
        ctrlArmed = false
        altArmed = false
    }

    private fun sendKey(code: Int) {
        val ic = getInputConnection() ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun hotkeyButton(context: Context, label: String, onClick: () -> Unit) =
        TextView(context)
            .apply {
                text = label
                setOnClickListener { onClick() }
            }
            .also { applyHotkeyStyle(it) }

    private fun toggleButton(
        context: Context,
        label: String,
        isOn: () -> Boolean,
        onToggle: (Boolean) -> Unit,
    ) =
        TextView(context)
            .apply {
                text = label
                alpha = if (isOn()) 1f else 0.6f
                setOnClickListener {
                    onToggle(!isOn())
                    alpha = if (isOn()) 1f else 0.6f
                }
            }
            .also { applyHotkeyStyle(it) }

    private fun applyHotkeyStyle(view: TextView) {
        view.setBackgroundResource(R.drawable.bg_key_special)
        view.setTextColor(view.resources.getColor(R.color.kb_key_text, view.context.theme))
        val density = view.resources.displayMetrics.density
        view.setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
        val lp =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
        lp.setMargins(
            (3 * density).toInt(),
            (4 * density).toInt(),
            (3 * density).toInt(),
            (4 * density).toInt(),
        )
        view.layoutParams = lp
        view.gravity = android.view.Gravity.CENTER
        view.textSize = 12f
    }
}
