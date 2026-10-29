/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.SymbolHistory
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ime.window.ResidentWindow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance

class LiquidWindow(di: DI) :
    BoardWindow.BarBoardWindow(di),
    ResidentWindow {
    override val showTitle = false

    private val service: TrimeInputMethodService by instance()
    private val rime: RimeSession by instance()
    private val scope: ThemeScope by instance()
    private val windowManager: BoardWindowManager by instance()
    private val commonKeyboardActionListener: CommonKeyboardActionListener by instance()

    private lateinit var liquidLayout: LiquidLayout
    var isLocked = false
        private set

    private val supportsLock get() = "liquid_keyboard_lock" in scope.theme.liquidKeyboard.fixedKeyBar.keys

    fun toggleLock() {
        isLocked = !isLocked
        liquidLayout.updateLockLabel(isLocked)
    }

    private fun returnAfterCommit() {
        if (supportsLock && !isLocked) windowManager.attachWindow(KeyboardWindow)
    }

    private val symbolHistory = SymbolHistory.shared
    private var historyJob: Job? = null
    var currentDataType: LiquidKeyboard.DataType = LiquidKeyboard.DataType.SINGLE
        private set

    private val adapter by lazy {
        LiquidAdapter(scope) {
            when (currentDataType) {
                LiquidKeyboard.DataType.SYMBOL -> triggerSymbolInput(this.altText)

                LiquidKeyboard.DataType.TABS -> {
                    val realPosition = scope.theme.liquidKeyboard.getTagList()
                        .indexOfFirst { it.label == this.text }
                    setDataByIndex(realPosition)
                }

                else -> {
                    service.commitText(this.text)
                    if (currentDataType != LiquidKeyboard.DataType.HISTORY) {
                        symbolHistory.insert(this.text)
                        symbolHistory.save()
                    }
                    returnAfterCommit()
                }
            }
        }
    }

    private val mainLayoutManager by lazy {
        FlexboxLayoutManager(context).apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
        }
    }

    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key
        get() = LiquidWindow

    override fun onCreateView(): View = LiquidLayout(context, scope, commonKeyboardActionListener).apply {
        liquidLayout = this
        tabsUi.apply {
            setTags(scope.theme.liquidKeyboard.getTagList())
            setOnTabClickListener { i ->
                setDataByIndex(i)
            }
        }
        recyclerView.apply {
            layoutManager = mainLayoutManager
            setHasFixedSize(true)
            itemAnimator = null
            this.adapter = this@LiquidWindow.adapter
            if (supportsLock) {
                addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                        post { this@LiquidWindow.adapter.notifyItemRangeChanged(0, this@LiquidWindow.adapter.itemCount, Unit) }
                    }
                }
            }
        }
    }

    override fun onCreateBarView() = liquidLayout.tabsUi.root

    override fun onAttached() {
        liquidLayout.updateLockLabel(isLocked)
        liquidLayout.updateEnterLabel()
        if (currentDataType == LiquidKeyboard.DataType.HISTORY) loadHistory()
    }

    override fun onDetached() {
        historyJob?.cancel()
    }

    override fun refreshColors() {
        if (::liquidLayout.isInitialized) liquidLayout.refreshColors()
    }

    fun setDataByIndex(i: Int) {
        historyJob?.cancel()
        val tag = scope.theme.liquidKeyboard.getTagList().getOrNull(i) ?: return
        currentDataType = tag.type
        liquidLayout.tabsUi.activateTab(i)
        when (tag.type) {
            LiquidKeyboard.DataType.HISTORY -> {
                loadHistory()
            }

            else -> {
                val data = scope.theme.liquidKeyboard.getDataByIndex(i)
                submitData(data)
            }
        }
    }

    private fun loadHistory() {
        historyJob?.cancel()
        historyJob = service.lifecycleScope.launch {
            symbolHistory.load()
            submitData(symbolHistory.toOrderedList().map { LiquidKeyboard.KeyItem(it) })
        }
    }

    private var submittedData: List<LiquidKeyboard.KeyItem>? = null

    private fun submitData(data: List<LiquidKeyboard.KeyItem>) {
        // Compare with the latest request, including a diff still running off Main.
        if (submittedData == data) return
        submittedData = data
        adapter.submitList(data)
    }

    private fun triggerSymbolInput(symbol: String) {
        rime.launchOnReady {
            val (isAsciiMode, isAsciiPunch) = it.statusCached.run { isAsciiMode to isAsciiPunct }
            if (isAsciiMode) it.setRuntimeOption("ascii_mode", false)
            if (isAsciiPunch) it.setRuntimeOption("ascii_punct", false)
            it.clearComposition()
            it.simulateKeySequence(symbol)
            if (isAsciiPunch) it.setRuntimeOption("ascii_punct", true)
            ContextCompat.getMainExecutor(service).execute {
                if (!supportsLock || !isLocked) windowManager.attachWindow(KeyboardWindow)
            }
        }
    }
}
