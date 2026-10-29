/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.c2cwl.chenime.R
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.core.InputTabLayout
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.segments.SegmentsWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ime.window.ResidentWindow
import com.osfans.trime.ui.main.ClipEditActivity
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import splitties.views.recyclerview.verticalLayoutManager

class ClipboardWindow(di: DI) :
    BoardWindow.BarBoardWindow(di),
    ResidentWindow {
    companion object : ResidentWindow.Key
    override val key: ResidentWindow.Key get() = ClipboardWindow
    private var selectedTab = 0

    fun selectTab(index: Int) {
        selectedTab = index.coerceIn(0, 1)
        if (::clipboardLayout.isInitialized) clipboardLayout.viewPager.setCurrentItem(selectedTab, false)
    }

    private val service: TrimeInputMethodService by instance()
    private val windowManager: BoardWindowManager by instance()
    private val scope: ThemeScope by instance()
    private val theme: Theme get() = scope.theme

    private lateinit var clipboardLayout: ClipboardLayout
    private lateinit var clipboardPagesAdapter: ClipboardPagesAdapter

    private val prefs = AppPrefs.defaultInstance().clipboard
    private val clipboardReturnAfterPaste by prefs.clipboardReturnAfterPaste

    // Cache loaded pages across visibility changes, but release them with this theme's panel.
    private val dataScope = CoroutineScope(
        service.lifecycleScope.coroutineContext + SupervisorJob(service.lifecycleScope.coroutineContext[Job]),
    )

    private val clipboardBeansPager by lazy {
        Pager(PagingConfig(pageSize = 16)) { ClipboardHelper.allBeans() }.flow.cachedIn(dataScope)
    }
    private val collectionBeansPager by lazy {
        Pager(PagingConfig(pageSize = 16)) { CollectionHelper.allBeans() }.flow.cachedIn(dataScope)
    }
    override fun onDestroyed() {
        onDetached()
        dataScope.cancel()
    }

    private var clipboardBeansSubmitJob: Job? = null
    private var collectionBeansSubmitJob: Job? = null

    private val clipboardBeansAdapter by lazy {
        object : ClipboardAdapter(scope) {
            override fun onPaste(bean: DatabaseBean) {
                val text = bean.text ?: return
                service.commitText(text)
                if (clipboardReturnAfterPaste) {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }

            override fun onPin(id: Int) {
                service.lifecycleScope.launch { ClipboardHelper.pin(id) }
            }

            override fun onUnpin(id: Int) {
                service.lifecycleScope.launch { ClipboardHelper.unpin(id) }
            }

            override fun onEdit(id: Int) {
                AppUtils.launchClipEdit(context, id, ClipEditActivity.FROM_CLIPBOARD)
            }

            override fun onShare(bean: DatabaseBean) {
                val text = bean.text ?: return
                launchTextSharing(text)
            }

            override fun onSegment(bean: DatabaseBean) {
                val text = bean.text ?: return
                windowManager.attachWindow(SegmentsWindow(di, text))
            }

            override fun onCollect(bean: DatabaseBean) {
                service.lifecycleScope.launch {
                    CollectionHelper.addNewBean(bean.text ?: "")
                }
            }

            override fun onDelete(id: Int) {
                service.lifecycleScope.launch { ClipboardHelper.delete(id) }
            }

            override val enableCollection: Boolean = true
        }
    }

    private val collectionBeansAdapter by lazy {
        object : ClipboardAdapter(scope) {
            override fun onPaste(bean: DatabaseBean) {
                val text = bean.text ?: return
                service.commitText(text)
                if (clipboardReturnAfterPaste) {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }

            override fun onEdit(id: Int) {
                AppUtils.launchClipEdit(context, id, ClipEditActivity.FROM_COLLECTION)
            }

            override fun onShare(bean: DatabaseBean) {
                val text = bean.text ?: return
                launchTextSharing(text)
            }

            override fun onSegment(bean: DatabaseBean) {
                val text = bean.text ?: return
                windowManager.attachWindow(SegmentsWindow(di, text))
            }

            override fun onDelete(id: Int) {
                service.lifecycleScope.launch { CollectionHelper.delete(id) }
            }

            override val enableCollection: Boolean = false
        }
    }

    private val recycledPool = androidx.recyclerview.widget.RecyclerView.RecycledViewPool()

    private val clipboardPage by lazy {
        ClipboardPageUi(context).apply {
            recyclerView.apply {
                layoutManager = verticalLayoutManager().apply { initialPrefetchItemCount = 4 }
                setRecycledViewPool(recycledPool)
                setHasFixedSize(true)
                itemAnimator = null
                adapter = clipboardBeansAdapter
            }
        }
    }

    private val collectionPage by lazy {
        ClipboardPageUi(context).apply {
            recyclerView.apply {
                layoutManager = verticalLayoutManager().apply { initialPrefetchItemCount = 4 }
                setRecycledViewPool(recycledPool)
                setHasFixedSize(true)
                itemAnimator = null
                adapter = collectionBeansAdapter
            }
        }
    }

    private val cachedView by lazy { createView() }

    override fun onCreateView() = cachedView

    private fun createView() = ClipboardLayout(context, scope).apply {
        clipboardLayout = this
        clipboardPagesAdapter = object : ClipboardPagesAdapter() {
            override fun getItemCount(): Int = 2
            override fun onCreatePage(position: Int): ClipboardPageUi = when (position) {
                0 -> clipboardPage
                else -> collectionPage
            }
        }
        // Construct both page roots during warmup, rather than on the first tab switch.
        clipboardPage
        collectionPage
        viewPager.apply {
            offscreenPageLimit = 1
            adapter = clipboardPagesAdapter
        }
        startData(warmup = true)
        titleUi.apply {
            tabLayout.onConfigureTab(viewPager) { tabUi, position ->
                configureTab(tabUi, position)
            }
            deleteAllButton.setOnClickListener {
                val currentItem = viewPager.currentItem
                when (currentItem) {
                    0 -> promptDeleteAll {
                        ClipboardHelper.deleteAll(ClipboardHelper.haveUnpinned())
                    }

                    else -> promptDeleteAll {
                        CollectionHelper.deleteAll(CollectionHelper.haveUnpinned())
                    }
                }
            }
        }
    }

    private fun launchTextSharing(text: String) {
        val target = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(target, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(chooser)
    }

    private fun promptDeleteAll(action: suspend () -> Unit) {
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.delete_all)
            .setMessage(R.string.ask_to_delete_all)
            .setPositiveButton(R.string.ok) { _, _ ->
                service.lifecycleScope.launch {
                    action()
                }
            }.setNegativeButton(R.string.cancel, null)
            .create()
        service.showDialog(dialog)
    }

    private fun configureTab(
        tabUi: InputTabLayout.TabUi,
        position: Int,
    ) {
        val label =
            when (position) {
                0 -> R.string.clipboard
                else -> R.string.collection
            }
        tabUi.label.apply {
            setText(label)
            textSize = theme.style.candidateTextSize
            setTypeface(theme.fonts.candidate, Typeface.BOLD)
            setTextColor(scope.colors.keyTextColor)
        }
    }

    override fun onAttached() {
        clipboardLayout.viewPager.setCurrentItem(selectedTab, false)
        refreshColors()
        startData(warmup = false)
    }

    private fun startData(warmup: Boolean) {
        clipboardBeansSubmitJob?.cancel()
        collectionBeansSubmitJob?.cancel()
        clipboardBeansSubmitJob = service.lifecycleScope.launch {
            coroutineScope {
                val submit = launch {
                    clipboardBeansPager.collectLatest { clipboardBeansAdapter.submitData(it) }
                }
                if (warmup) {
                    clipboardBeansAdapter.onPagesUpdatedFlow.first()
                    submit.cancel()
                }
            }
        }
        collectionBeansSubmitJob = service.lifecycleScope.launch {
            coroutineScope {
                val submit = launch {
                    collectionBeansPager.collectLatest { collectionBeansAdapter.submitData(it) }
                }
                if (warmup) {
                    collectionBeansAdapter.onPagesUpdatedFlow.first()
                    submit.cancel()
                }
            }
        }
    }

    override fun onDetached() {
        clipboardBeansAdapter.dismissPopupMenu()
        collectionBeansAdapter.dismissPopupMenu()
        clipboardBeansSubmitJob?.cancel()
        collectionBeansSubmitJob?.cancel()
    }

    private var colorRevision = -1L

    override fun refreshColors() {
        if (!::clipboardLayout.isInitialized || colorRevision == scope.colorRevision) return
        colorRevision = scope.colorRevision
        clipboardLayout.titleUi.deleteAllButton.refreshColors()
        clipboardLayout.titleUi.tabLayout.reconfigureTabs { tabUi, position ->
            configureTab(tabUi, position)
        }
        clipboardBeansAdapter.refreshColors()
        collectionBeansAdapter.refreshColors()
    }

    override fun onCreateBarView(): View = clipboardLayout.titleUi.root
}
