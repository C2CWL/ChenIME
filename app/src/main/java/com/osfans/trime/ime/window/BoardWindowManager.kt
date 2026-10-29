/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.window

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import com.c2cwl.chenime.R
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.broadcast.InputBroadcaster
import com.osfans.trime.ime.clipboard.ClipboardWindow
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.switches.SwitchOptionWindow
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import timber.log.Timber

class BoardWindowManager(override val di: DI) : DIAware {
    private val context by instance<ContextThemeWrapper>()
    private val broadcaster by instance<InputBroadcaster>()

    private val cachedResidentWindows = mutableMapOf<ResidentWindow.Key, Pair<BoardWindow, View?>>()

    private val service: TrimeInputMethodService by instance()

    private val switchOptionWindow by lazy { SwitchOptionWindow(di).also { cacheResidentWindow(it) } }
    private val clipboardWindow by lazy { ClipboardWindow(di).also { cacheResidentWindow(it) } }

    fun showMore() = attachWindow(switchOptionWindow)

    fun showClipboard(tab: Int = 0) {
        clipboardWindow.selectTab(tab)
        attachWindow(clipboardWindow)
    }

    fun destroy() {
        destroyed = true
        currentWindow?.onDetached()
        cachedResidentWindows.values.forEach { (window, _) ->
            window.onDestroyed()
            broadcaster.removeReceiver(window)
        }
        cachedResidentWindows.clear()
        view.removeAllViews()
        currentWindow = null
        currentView = null
    }

    private var currentWindow: BoardWindow? = null
    private var currentView: View? = null

    private var destroyed = false

    /** Called after the first keyboard frame; resident roots stay measured but do not draw. */
    fun precreatePanels() {
        if (destroyed) return
        switchOptionWindow
        clipboardWindow
        cachedResidentWindows.toMap().forEach { (key, entry) ->
            if (entry.second == null) {
                val root = entry.first.onCreateView()
                root.visibility = View.INVISIBLE
                view.apply { add(root, lParams(matchParent, matchParent)) }
                cachedResidentWindows[key] = entry.first to root
                val bar: InputBarDelegate by instance()
                bar.precreateWindowBar(entry.first)
                entry.first.refreshColors()
            }
        }
    }

    fun <R> cacheResidentWindow(
        window: R,
        createView: Boolean = false,
    ) where R : BoardWindow, R : ResidentWindow {
        if (window.key in cachedResidentWindows) {
            if (cachedResidentWindows[window.key]!!.first === window) {
                Timber.d("Skip adding resident window $window")
                return
            } else {
                throw IllegalStateException("${window.key} is already occupied")
            }
        }
        broadcaster.addReceiver(window)
        val view = if (createView) window.onCreateView() else null
        cachedResidentWindows[window.key] = window to view
    }

    fun attachWindow(windowKey: ResidentWindow.Key) {
        cachedResidentWindows[windowKey]?.let { (window, _) ->
            attachWindow(window)
        } ?: throw IllegalStateException("$windowKey is not a known resident window key")
    }

    fun attachWindow(window: BoardWindow) {
        if (service.isInputViewShown) service.updatePreferredRefreshRate(true)
        if (window === currentWindow) {
            Timber.d("Skip attaching $window")
            return
        }
        val newView =
            if (window is ResidentWindow) {
                cachedResidentWindows[window.key]?.second ?: window
                    .onCreateView()
                    .also { cachedResidentWindows[window.key] = window to it }
            } else {
                broadcaster.addReceiver(window)
                window.onCreateView()
            }
        if (currentWindow != null) {
            val oldWindow = currentWindow!!
            val oldView = currentView!!
            oldWindow.onDetached()
            if (oldWindow is ResidentWindow) oldView.visibility = View.INVISIBLE else view.removeView(oldView)
            broadcaster.onWindowDetached(oldWindow)
            Timber.d("Detach $oldWindow")
            if (oldWindow !is ResidentWindow) {
                broadcaster.removeReceiver(oldWindow)
            }
        }
        if (window is ResidentWindow) {
            window.beforeAttached()
        }
        if (newView.parent == null) view.apply { add(newView, lParams(matchParent, matchParent)) }
        newView.visibility = View.VISIBLE
        currentView = newView
        Timber.d("Attach $window")
        window.onAttached()
        currentWindow = window
        broadcaster.onWindowAttached(window)
    }

    /** Restyles the window currently on screen after a scheme switch. */
    fun refreshColors() {
        cachedResidentWindows.values.forEach { (window, _) -> window.refreshColors() }
        if (currentWindow !is ResidentWindow) currentWindow?.refreshColors()
    }

    val view: FrameLayout by lazy { context.frameLayout(R.id.input_window) }

    fun isAttached(window: BoardWindow) = currentWindow === window
}
