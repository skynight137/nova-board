package com.novaboard.ime.translation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.novaboard.ime.NovaBoardService

class TranslationResultActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = TranslationRequest(
            session = intent.getLongExtra(EXTRA_SESSION, -1L),
            requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1L),
            selectionStart = intent.getIntExtra(EXTRA_SELECTION_START, -1),
            selectionEnd = intent.getIntExtra(EXTRA_SELECTION_END, -1),
        )
        if (!request.isValid) {
            reportResult(request, null)
            return
        }

        val processText =
            Intent(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, intent.getStringExtra(EXTRA_TEXT))
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        if (processText.resolveActivity(packageManager) == null) {
            reportResult(request, null)
            return
        }
        startActivityForResult(Intent.createChooser(processText, "Translate"), REQUEST_PROCESS_TEXT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PROCESS_TEXT) return
        val request = TranslationRequest(
            session = intent.getLongExtra(EXTRA_SESSION, -1L),
            requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1L),
            selectionStart = intent.getIntExtra(EXTRA_SELECTION_START, -1),
            selectionEnd = intent.getIntExtra(EXTRA_SELECTION_END, -1),
        )
        val result = data?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        reportResult(request, resultCode.takeIf { it == RESULT_OK }?.let { result })
    }

    private fun reportResult(request: TranslationRequest, text: String?) {
        startService(
            Intent(this, NovaBoardService::class.java).apply {
                action = ACTION_TRANSLATION_RESULT
                putExtra(EXTRA_SESSION, request.session)
                putExtra(EXTRA_REQUEST_ID, request.requestId)
                putExtra(EXTRA_SELECTION_START, request.selectionStart)
                putExtra(EXTRA_SELECTION_END, request.selectionEnd)
                putExtra(EXTRA_RESULT, text)
            },
        )
        finish()
    }

    companion object {
        const val ACTION_TRANSLATION_RESULT = "com.novaboard.ime.TRANSLATION_RESULT"
        const val EXTRA_SESSION = "translation_session"
        const val EXTRA_REQUEST_ID = "translation_request_id"
        const val EXTRA_SELECTION_START = "translation_selection_start"
        const val EXTRA_SELECTION_END = "translation_selection_end"
        const val EXTRA_TEXT = "translation_text"
        const val EXTRA_RESULT = "translation_result"

        private const val REQUEST_PROCESS_TEXT = 41
    }
}