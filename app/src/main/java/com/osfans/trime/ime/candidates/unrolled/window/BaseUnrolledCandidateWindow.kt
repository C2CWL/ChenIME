/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.unrolled.window

import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.RimeKeyEvent
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.bar.UnrollButtonStateMachine
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.compact.CompactCandidateDelegate
import com.osfans.trime.ime.candidates.unrolled.CandidatesPagingSource
import com.osfans.trime.ime.candidates.unrolled.FilteringCandidatesPagingSource
import com.osfans.trime.ime.candidates.unrolled.IndexedCandidate
import com.osfans.trime.ime.candidates.unrolled.PagingCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.StrokeOrderIndex
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateLayout
import com.osfans.trime.ime.candidates.unrolled.syllableInputEnd
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.handwriting.HandwritingMode
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.util.diagnostics.LagDiagnostics
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import splitties.dimensions.dp
import kotlin.math.max

@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseUnrolledCandidateWindow(di: DI) :
    BoardWindow.NoBarBoardWindow(di),
    InputBroadcastReceiver {
    protected val service: TrimeInputMethodService by instance()
    protected val rime: RimeSession by instance()
    protected val scope: ThemeScope by instance()
    private val inputView: InputView by instance()
    private val bar: InputBarDelegate by instance()
    private val windowManager: BoardWindowManager by instance()
    private val compactCandidate: CompactCandidateDelegate by instance()

    protected val theme: Theme
        get() = scope.theme

    private lateinit var lifecycleCoroutineScope: LifecycleCoroutineScope
    private lateinit var candidateLayout: UnrolledCandidateLayout

    // Evaluated by Paging on IO, never while creating or laying out the panel.
    private val strokeOrders by lazy {
        LagDiagnostics.measure("stroke_index_load") {
            context.assets.open("stroke/orders.tsv").reader(Charsets.UTF_8).use(StrokeOrderIndex::read)
        }
    }

    protected val separatorDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val spacing = theme.style.candidateSpacing
            val intrinsicSize = max(spacing, context.dp(spacing)).toInt()
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = scope.colors.candidateSeparatorColor
        }
    }

    override fun refreshColors() {
        if (!::candidateLayout.isInitialized) return
        // the decorations share this drawable, so re-coloring its paint repaints the dividers
        separatorDrawable.paint.color = scope.colors.candidateSeparatorColor
        candidateLayout.refreshColors()
        // visible rows re-apply their colors on rebind
        adapter.notifyDataSetChanged()
    }

    abstract fun onCreateCandidateLayout(): UnrolledCandidateLayout

    final override fun onCreateView(): View {
        val createAt = LagDiagnostics.start()
        candidateLayout =
            onCreateCandidateLayout().apply {
                onHandwriting = { windowManager.showHandwriting(HandwritingMode.FindChar) }
                onResetFilters = {
                    singleCharacter = false
                    clearSyllableFilter()
                    setStrokeExpanded(false)
                    reloadCandidates()
                }
                onSingleCharacterToggle = {
                    singleCharacter = !singleCharacter
                    resetPosition()
                    reloadCandidates()
                }
                onStrokeChange = { prefix ->
                    updateStrokePrefix(prefix)
                    resetPosition()
                    reloadCandidates()
                }
                onSyllableToggle = { index, syllables ->
                    filterJob?.cancel()
                    if (activeSyllable == index) {
                        clearSyllableFilter()
                    } else {
                        filterJob = lifecycleCoroutineScope.launch {
                            val target = rime.runOnReady {
                                val ends = getCandidateEnds(0, 300)
                                if (ends.size < 2) null else syllableInputEnd(getRawInput(), ends[0], syllables, index, ends.drop(2))
                            }
                            if (target != null) {
                                filterEnd = target
                                activeSyllable = index
                                resetPosition()
                                reloadCandidates()
                            }
                        }
                    }
                }
                onCompositionKey = { key ->
                    if (key == "BackSpace" && strokePrefix.isNotEmpty()) {
                        onStrokeChange?.invoke(strokePrefix.dropLast(1))
                    } else {
                        rime.launchOnReady { it.processKey(RimeKeyEvent.getKeycodeByName(key), 0u) }
                    }
                }
                recyclerView.apply {
                    // disable item cross-fade animation
                    itemAnimator = null
                    setHasFixedSize(true)
                }
            }
        LagDiagnostics.duration("expanded_panel_create", createAt)
        return candidateLayout
    }

    abstract val adapter: PagingCandidateViewAdapter
    abstract val layoutManager: RecyclerView.LayoutManager

    private var offsetJob: Job? = null
    private var filterJob: Job? = null
    private var filterEnd: Int? = null

    private fun clearSyllableFilter() {
        filterJob?.cancel()
        if (filterEnd == null && candidateLayout.activeSyllable == null && candidateLayout.strokePrefix.isEmpty()) return
        filterEnd = null
        candidateLayout.updateStrokePrefix("")
        candidateLayout.activeSyllable = null
        candidateLayout.resetPosition()
        reloadCandidates()
    }

    private val candidateRevision = MutableStateFlow(0L)
    private var composition: CompositionProto? = null

    private fun reloadCandidates() {
        candidateRevision.value += 1
    }

    private fun candidatesPager(): Pager<Int, IndexedCandidate> {
        val end = filterEnd
        val single = candidateLayout.singleCharacter
        val prefix = candidateLayout.strokePrefix
        val offset = adapter.offset
        val total = compactCandidate.adapter.total
        return Pager(
            config = PagingConfig(
                pageSize = 48,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                if (end != null || single || prefix.isNotEmpty()) {
                    FilteringCandidatesPagingSource(
                        rime,
                        end,
                        single,
                        prefix,
                        { strokeOrders },
                    )
                } else {
                    CandidatesPagingSource(
                        rime,
                        total = total,
                        offset = offset,
                    )
                }
            },
        )
    }

    private var candidatesSubmitJob: Job? = null

    override fun onAttached() {
        LagDiagnostics.log("expanded_panel_open")
        // Only alpha changes during the fade; do not animate panel geometry or layout.
        candidateLayout.alpha = 0f
        candidateLayout.animate().alpha(1f).setDuration(100L).withLayer().start()
        lifecycleCoroutineScope = candidateLayout.findViewTreeLifecycleOwner()!!.lifecycleScope
        composition = rime.run { compositionCached }
        candidateLayout.updatePreedit(composition?.preedit)
        bar.unrollButtonStateMachine.push(UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesAttached)
        offsetJob =
            lifecycleCoroutineScope.launch {
                compactCandidate.unrolledCandidateOffset.collect {
                    if (it <= 0) {
                        windowManager.attachWindow(KeyboardWindow)
                    } else {
                        candidateLayout.resetPosition()
                        adapter.refreshWith(
                            offset = it,
                            highlightedIndex = compactCandidate.adapter.highlightedIdx,
                        )
                        reloadCandidates()
                    }
                }
            }
        candidatesSubmitJob =
            lifecycleCoroutineScope.launch {
                candidateRevision.flatMapLatest { candidatesPager().flow }.collectLatest {
                    adapter.submitData(it)
                }
            }
    }

    override fun onCompositionUpdate(data: CompositionProto) {
        if (::candidateLayout.isInitialized && composition != data) {
            composition = data
            clearSyllableFilter()
            candidateLayout.updatePreedit(data.preedit)
        }
    }

    fun bindCandidateUiViewHolder(holder: CandidateViewHolder) {
        holder.itemView.run {
            setOnClickListener { _ ->
                rime.launchOnReady { it.selectCandidate(holder.idx, global = true) }
            }
            setOnLongClickListener { view ->
                inputView.showCandidateActionMenu(holder.idx, holder.text, view, global = true)
                true
            }
        }
    }

    override fun onDetached() {
        clearSyllableFilter()
        candidateLayout.animate().cancel()
        candidateLayout.alpha = 1f
        bar.unrollButtonStateMachine.push(
            UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesDetached,
            UnrollButtonStateMachine.BooleanKey.UnrolledCandidatesEmpty to
                (compactCandidate.adapter.total == adapter.offset),
        )
        offsetJob?.cancel()
        candidatesSubmitJob?.cancel()
    }
}
