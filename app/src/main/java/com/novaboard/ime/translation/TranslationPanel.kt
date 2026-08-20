package com.novaboard.ime.translation

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.novaboard.ime.R

class TranslationPanel(
    context: Context,
    initialState: TranslationComposerState,
    private val onDismiss: () -> Unit,
    private val onUnavailable: () -> Unit,
    private val onPaste: (TranslationComposerState, Int) -> Unit,
    private val onReply: (TranslationComposerState, Int, Int) -> Unit,
) {
    private var state = initialState
    private val source = EditText(context)
    private val result = TextView(context)
    private val status = TextView(context)
    private val paste = Button(context)
    private val reply = Button(context)
    val view: View = buildView(context)

    init {
        source.setText(state.sourceText)
        source.setSelection(source.length())
        update()
    }

    private fun buildView(context: Context): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))
            setBackgroundColor(context.getColor(R.color.kb_background))

            addView(
                LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(label(context, state.sourceLanguage))
                    addView(
                        Button(context).apply {
                            text = "⇄"
                            contentDescription = context.getString(R.string.translation_swap)
                            setOnClickListener {
                                state = reduceTranslationComposer(
                                    state.copy(sourceText = source.text.toString()),
                                    TranslationComposerAction.SwapLanguages,
                                )
                                update()
                            }
                        },
                        weightedParams(context, 0f),
                    )
                    addView(label(context, state.targetLanguage))
                    addView(
                        Button(context).apply {
                            text = context.getString(R.string.translation_close)
                            contentDescription = context.getString(R.string.translation_close)
                            setOnClickListener { onDismiss() }
                        },
                        weightedParams(context, 1f),
                    )
                },
                ViewGroup.LayoutParams.MATCH_PARENT,
            )

            source.hint = context.getString(R.string.translation_source_hint)
            source.minLines = 2
            source.maxLines = 4
            source.gravity = Gravity.TOP
            source.contentDescription = context.getString(R.string.translation_source_hint)
            source.setTextColor(context.getColor(R.color.kb_key_text))
            addView(source, ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 72))

            addView(
                LinearLayout(context).apply {
                    addView(
                        Button(context).apply {
                            text = context.getString(R.string.translation_translate)
                            contentDescription = context.getString(R.string.translation_translate)
                            setOnClickListener {
                                state = reduceTranslationComposer(
                                    state.copy(sourceText = source.text.toString()),
                                    TranslationComposerAction.RequestTranslation,
                                )
                                update()
                                onUnavailable()
                            }
                        },
                        weightedParams(context, 1f),
                    )
                    addView(
                        Button(context).apply {
                            text = context.getString(R.string.translation_clear)
                            contentDescription = context.getString(R.string.translation_clear)
                            setOnClickListener {
                                state = reduceTranslationComposer(
                                    state,
                                    TranslationComposerAction.ClearSource,
                                )
                                update()
                            }
                        },
                        weightedParams(context, 1f),
                    )
                },
                ViewGroup.LayoutParams.MATCH_PARENT,
            )

            result.minLines = 2
            result.gravity = Gravity.TOP
            result.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            result.setTextColor(context.getColor(R.color.kb_key_text))
            result.setBackgroundColor(context.getColor(R.color.kb_key_bg_special))
            result.contentDescription = context.getString(R.string.translation_result)
            addView(result, ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 72))

            status.setTextColor(context.getColor(R.color.kb_suggestion_text))
            status.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4))
            addView(status, ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 32))

            addView(
                LinearLayout(context).apply {
                    addView(paste, weightedParams(context, 1f))
                    addView(reply, weightedParams(context, 1f))
                },
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

    private fun update() {
        val context = view.context
        source.setText(state.sourceText)
        source.setSelection(source.length())
        result.text = state.translatedText.orEmpty()
        status.text =
            when (state.status) {
                TranslationStatus.LOADING -> context.getString(R.string.translation_loading)
                TranslationStatus.UNAVAILABLE ->
                    context.getString(R.string.translation_unavailable)
                TranslationStatus.ERROR -> context.getString(R.string.translation_error)
                else -> ""
            }
        paste.text = context.getString(R.string.translation_paste)
        paste.contentDescription = context.getString(R.string.translation_paste)
        paste.isEnabled = !state.translatedText.isNullOrBlank()
        paste.setOnClickListener { onPaste(state, source.selectionStart) }
        reply.text = context.getString(R.string.translation_reply)
        reply.contentDescription = context.getString(R.string.translation_reply)
        reply.isEnabled = state.hasSelection && !state.translatedText.isNullOrBlank()
        reply.setOnClickListener { onReply(state, state.selectedStart, state.selectedEnd) }
    }

    private fun label(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text.uppercase()
            setTextColor(context.getColor(R.color.kb_key_text))
            setPadding(dp(context, 8), 0, dp(context, 8), 0)
        }

    private fun weightedParams(context: Context, weight: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(context, 48), weight).apply {
            marginStart = dp(context, 2)
            marginEnd = dp(context, 2)
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}