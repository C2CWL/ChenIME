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
import androidx.lifecycle.lifecycleScope
import com.c2cwl.chenime.R
import com.osfans.trime.core.CompositionProto
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
import com.osfans.trime.util.diagnostics.LagDiagnostics
import kotlinx.coroutines.launch
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
    private var lookupInput: String? = null
    private var lookupComposition: CompositionProto? = null

    private lateinit var layout: HandwritingLayout
    private lateinit var candidateBar: HandwritingCandidateBar

    override fun onCreateBarView(): View {
        if (!::candidateBar.isInitialized) {
            candidateBar = HandwritingCandidateBar(context, scope, ::pickCandidate)
        }
        return candidateBar
    }

    override fun onCreateView(): View {
        layout = HandwritingLayout(context, scope, mode, ::onAction)
        layout.canvasView.onStrokeStarted = {
            recognizeGeneration += 1
            lookupInput = null
            lookupComposition = null
            if (::candidateBar.isInitialized) candidateBar.setCandidates(emptyList())
        }
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
        LagDiagnostics.log("handwriting_open", "model_ready=${recognizer.isReady}")
        worker.execute {
            val ok = if (recognizer.isReady) true else LagDiagnostics.measure("handwriting_model_load") { recognizer.ensureLoaded(assets) }
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
        lookupInput = null
        lookupComposition = null
    }

    override fun onCompositionUpdate(data: CompositionProto) {
        if (mode != HandwritingMode.FindChar || !windowManager.isAttached(this)) return
        if (data == lookupComposition) return
        recognizeGeneration += 1
        lookupInput = null
        lookupComposition = null
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
                recognizeCurrentInk()
            }

            HandwritingAction.Backspace -> {
                if (layout.canvasView.ink.canUndo) {
                    layout.canvasView.undoStroke()
                    recognizeCurrentInk()
                } else if (mode == HandwritingMode.Full) {
                    service.sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL)
                }
            }

            HandwritingAction.Space -> service.commitText(" ")

            HandwritingAction.Confirm -> {
                if (::candidateBar.isInitialized) candidateBar.selectedCandidate?.let(::pickCandidate)
            }

            HandwritingAction.Enter -> {
                val candidate = if (::candidateBar.isInitialized) candidateBar.selectedCandidate else null
                if (candidate != null) {
                    pickCandidate(candidate)
                } else {
                    service.sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER)
                }
            }
        }
    }

    private fun recognizeCurrentInk() {
        if (!::layout.isInitialized) return
        val gen = ++recognizeGeneration
        lookupInput = null
        lookupComposition = null
        if (::candidateBar.isInitialized) candidateBar.setCandidates(emptyList())
        if (layout.canvasView.ink.isEmpty) return
        val raster = layout.canvasView.rasterize() ?: return
        val (gray, size) = raster
        val composition = rime.run { compositionCached }
        val assets = context.assets
        worker.execute {
            if (!recognizer.ensureLoaded(assets)) return@execute
            val results = LagDiagnostics.measure("handwriting_predict", minMs = 50) {
                recognizer.predict(gray, size.first, size.second, if (mode == HandwritingMode.FindChar) 32 else HandwritingRecognizer.DEFAULT_K)
            }
            main.post {
                if (gen != recognizeGeneration) return@post
                if (mode == HandwritingMode.Full) {
                    candidateBar.setCandidates(results.map { HandwritingCandidate(it.text) })
                    return@post
                }
                service.lifecycleScope.launch {
                    val (input, matches) = rime.runOnReady {
                        getRawInput() to findHandwritingCandidates(results.map { it.text }) { start, count ->
                            getCandidates(start, count).map { it.text }
                        }
                    }
                    if (gen != recognizeGeneration || composition != rime.run { compositionCached }) return@launch
                    lookupInput = input
                    lookupComposition = composition
                    candidateBar.setCandidates(matches, emptyMessage = context.getString(R.string.handwriting_no_match))
                }
            }
        }
    }

    private fun pickCandidate(candidate: HandwritingCandidate) {
        if (mode == HandwritingMode.Full) {
            commitCharacter(candidate.text)
            return
        }
        val index = candidate.rimeIndex ?: return
        val input = lookupInput ?: return
        val composition = lookupComposition ?: return
        val gen = recognizeGeneration
        service.lifecycleScope.launch {
            val selected = rime.runOnReady {
                if (getRawInput() != input || compositionCached != composition) return@runOnReady false
                if (getCandidates(index, 1).firstOrNull()?.text != candidate.text) return@runOnReady false
                if (gen != recognizeGeneration || !windowManager.isAttached(this@HandwritingWindow)) return@runOnReady false
                // Let Rime consume only this candidate's segment, never clear the whole input.
                selectCandidate(index, global = true)
            }
            if (selected && windowManager.isAttached(this@HandwritingWindow)) {
                recognizeGeneration += 1
                layout.canvasView.clearInk()
                candidateBar.setCandidates(emptyList())
                lookupInput = null
                lookupComposition = null
                dismiss()
            }
        }
    }

    private fun commitCharacter(text: String) {
        rime.launchOnReady {
            it.clearComposition()
            service.commitText(text)
            main.post {
                recognizeGeneration += 1
                layout.canvasView.clearInk()
                if (::candidateBar.isInitialized) candidateBar.setCandidates(emptyList())
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
