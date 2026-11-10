/*
 * SPDX-FileCopyrightText: 2026 ChenIME contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.handwriting

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import com.c2cwl.chenime.R
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.unrolled.window.FlexboxUnrolledCandidateWindow
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ime.window.ResidentWindow
import org.kodein.di.DI
import org.kodein.di.instance
import java.util.concurrent.Executors

class HandwritingWindow(di: DI) :
    BoardWindow.BarBoardWindow(di),
    InputBroadcastReceiver,
    ResidentWindow {
    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key get() = HandwritingWindow
    override val showTitle: Boolean = false

    var mode: HandwritingMode = HandwritingMode.Full

    private val service: TrimeInputMethodService by instance()
    private val scope: ThemeScope by instance()
    private val windowManager: BoardWindowManager by instance()
    private val rime: RimeSession by instance()

    private val recognizer = HandwritingRecognizer()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var recognizeGeneration = 0

    private lateinit var layout: HandwritingLayout
    private lateinit var candidateBar: HandwritingCandidateBar

    override fun onCreateBarView(): View {
        if (!::candidateBar.isInitialized) {
            candidateBar = HandwritingCandidateBar(context, scope) { commitCharacter(it.text, dismissAfter = mode == HandwritingMode.FindChar) }
        }
        return candidateBar
    }

    override fun onCreateView(): View {
        layout = HandwritingLayout(context, scope, mode, ::onAction)
        layout.canvasView.onStrokeFinished = { recognizeCurrentInk() }
        return layout
    }

    override fun onAttached() {
        if (::layout.isInitialized) {
            layout.bindMode(mode)
            layout.refreshColors()
        }
        if (::candidateBar.isInitialized) {
            candidateBar.setCandidates(emptyList())
            candidateBar.refreshColors()
        }
        val assets = context.assets
        worker.execute {
            val ok = recognizer.ensureLoaded(assets)
            if (!ok) {
                main.post {
                    Toast.makeText(service, R.string.handwriting_load_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDetached() {
        recognizeGeneration += 1
        layout.canvasView.clearInk()
        if (::candidateBar.isInitialized) candidateBar.setCandidates(emptyList())
    }

    override fun refreshColors() {
        if (::layout.isInitialized) layout.refreshColors()
        if (::candidateBar.isInitialized) candidateBar.refreshColors()
    }

    override fun onDestroyed() {
        worker.shutdownNow()
        recognizer.close()
    }

    private fun onAction(action: HandwritingAction) {
        when (action) {
            HandwritingAction.Back -> dismiss()

            HandwritingAction.Clear -> {
                layout.canvasView.clearInk()
                if (::candidateBar.isInitialized) candidateBar.setCandidates(emptyList())
            }

            HandwritingAction.Backspace -> {
                if (layout.canvasView.ink.canUndo) {
                    layout.canvasView.undoStroke()
                    recognizeCurrentInk()
                } else {
                    service.sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL)
                }
            }

            HandwritingAction.Space -> service.commitText(" ")

            HandwritingAction.Confirm -> {
                val text = if (::candidateBar.isInitialized) candidateBar.selectedCandidate?.text else null
                if (!text.isNullOrEmpty()) commitCharacter(text, dismissAfter = true)
            }

            HandwritingAction.Enter -> {
                val text = if (::candidateBar.isInitialized) candidateBar.selectedCandidate?.text else null
                if (!text.isNullOrEmpty()) {
                    commitCharacter(text, dismissAfter = false)
                } else {
                    service.sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER)
                }
            }
        }
    }

    private fun recognizeCurrentInk() {
        if (!::layout.isInitialized) return
        if (layout.canvasView.ink.isEmpty) {
            if (::candidateBar.isInitialized) candidateBar.setCandidates(emptyList())
            return
        }
        val raster = layout.canvasView.rasterize() ?: return
        val (gray, size) = raster
        val gen = ++recognizeGeneration
        val assets = context.assets
        worker.execute {
            if (!recognizer.ensureLoaded(assets)) return@execute
            val results = recognizer.predict(gray, size.first, size.second)
            main.post {
                if (gen != recognizeGeneration) return@post
                if (::candidateBar.isInitialized) candidateBar.setCandidates(results)
            }
        }
    }

    private fun commitCharacter(text: String, dismissAfter: Boolean) {
        rime.launchOnReady {
            it.clearComposition()
            service.commitText(text)
            if (dismissAfter) {
                main.post { dismiss() }
            } else {
                main.post {
                    layout.canvasView.clearInk()
                    if (::candidateBar.isInitialized) candidateBar.setCandidates(emptyList())
                }
            }
        }
    }

    private fun dismiss() {
        when (mode) {
            HandwritingMode.FindChar -> windowManager.attachWindow(FlexboxUnrolledCandidateWindow(di))
            HandwritingMode.Full -> windowManager.attachWindow(KeyboardWindow)
        }
    }
}
