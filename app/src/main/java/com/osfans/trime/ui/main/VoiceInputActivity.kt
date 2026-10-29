/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.speech.RecognizerIntent
import com.c2cwl.chenime.R
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.util.toast

/** Delegates microphone access to the installed speech recognizer. */
class VoiceInputActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val recognize = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(recognize, REQUEST_SPEECH)
        } catch (_: RuntimeException) {
            toast(R.string.no_voice_input_installed)
            finish()
        }
    }

    @Deprecated("Uses the platform activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SPEECH) return
        if (resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                @Suppress("DEPRECATION")
                val receiver = intent.getParcelableExtra<ResultReceiver>(EXTRA_RECEIVER)
                receiver?.send(RESULT_OK, Bundle().apply { putString(EXTRA_TEXT, text) })
            }
        }
        finish()
    }

    companion object {
        private const val REQUEST_SPEECH = 1
        private const val EXTRA_RECEIVER = "voice_receiver"
        private const val EXTRA_TEXT = "voice_text"

        fun launch(service: TrimeInputMethodService) {
            // Capture the originating editor before the transparent activity takes focus.
            val connection = service.currentInputConnection ?: return
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    resultData?.getString(EXTRA_TEXT)?.let { connection.commitText(it, 1) }
                }
            }
            try {
                service.startActivity(
                    Intent(service, VoiceInputActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(EXTRA_RECEIVER, receiver)
                    },
                )
            } catch (_: RuntimeException) {
                service.toast(R.string.no_voice_input_installed)
            }
        }
    }
}
