/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.quickphrase

import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.c2cwl.chenime.R
import com.osfans.trime.data.db.QuickPhraseHelper
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.ResidentWindow
import com.osfans.trime.ui.main.QuickPhraseEditActivity
import org.kodein.di.DI
import org.kodein.di.instance

/**
 * 常用语窗口：展示用户维护的短语，点击即上屏，并支持添加、编辑、删除。
 *
 * 与剪贴板窗口不同，这里直接使用原生 View 和 SharedPreferences，
 * 不依赖 Paging / Room 数据链路，也不改动既有数据库结构。
 * 编辑走独立 Activity，避免 IME 弹窗无法调起软键盘。
 */
class QuickPhraseWindow(di: DI) :
    BoardWindow.BarBoardWindow(di),
    ResidentWindow {
    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key get() = QuickPhraseWindow

    override val title: String
        get() = context.getString(R.string.quick_phrase_title)

    private val service: TrimeInputMethodService by instance()
    private val scope: ThemeScope by instance()

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: TextView

    private val textColor: Int
        get() = scope.colors.keyTextColor

    private val cardColor: Int
        get() = scope.colors.keyBackColor

    private val buttonTextColor: Int
        get() = scope.colors.labelColor

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private val changeListener: () -> Unit = { refreshList() }

    override fun onCreateView(): View {
        val ctx = context
        listContainer =
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
            }
        emptyView =
            TextView(ctx).apply {
                text = ctx.getString(R.string.quick_phrase_empty)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(24), dp(16), dp(24))
            }
        val scroll = ScrollView(ctx)
        scroll.addView(listContainer)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                emptyView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                scroll,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }

    override fun onCreateBarView(): View = TextView(context).apply {
        text = context.getString(R.string.quick_phrase_add)
        gravity = Gravity.CENTER
        setTextColor(textColor)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        setOnClickListener { launchEdit(-1, "") }
    }

    override fun onAttached() {
        QuickPhraseHelper.addChangeListener(changeListener)
        refreshList()
    }

    override fun onDetached() {
        QuickPhraseHelper.removeChangeListener(changeListener)
    }

    override fun refreshColors() {
        if (::listContainer.isInitialized) refreshList()
    }

    private fun refreshList() {
        if (!::listContainer.isInitialized) return
        val items = QuickPhraseHelper.list()
        listContainer.removeAllViews()
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        emptyView.setTextColor(textColor)
        items.forEachIndexed { index, phrase ->
            listContainer.addView(buildRow(index, phrase))
        }
    }

    private fun buildRow(
        index: Int,
        phrase: String,
    ): View {
        val ctx = context
        val row =
            LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background =
                    GradientDrawable().apply {
                        setColor(cardColor)
                        cornerRadius = dp(12).toFloat()
                    }
                setPadding(dp(14), dp(12), dp(8), dp(12))
            }
        val label =
            TextView(ctx).apply {
                text = phrase
                textSize = 15f
                setTextColor(textColor)
                setPadding(0, 0, dp(8), 0)
                setOnClickListener { service.commitText(phrase) }
            }
        row.addView(
            label,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            actionButton(ctx.getString(R.string.quick_phrase_edit)) {
                launchEdit(index, phrase)
            },
        )
        row.addView(
            actionButton(ctx.getString(R.string.quick_phrase_delete)) {
                confirmDelete(index)
            },
        )
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(8) },
            )
        }
    }

    private fun actionButton(
        label: String,
        onClick: () -> Unit,
    ): TextView = TextView(context).apply {
        text = label
        textSize = 13f
        setTextColor(buttonTextColor)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        setOnClickListener { onClick() }
    }

    private fun launchEdit(
        index: Int,
        initial: String,
    ) {
        val intent =
            Intent(service, QuickPhraseEditActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
                putExtra(QuickPhraseEditActivity.EXTRA_INDEX, index)
                putExtra(QuickPhraseEditActivity.EXTRA_TEXT, initial)
            }
        service.startActivity(intent)
    }

    private fun confirmDelete(index: Int) {
        val dialog =
            AlertDialog.Builder(context)
                .setTitle(R.string.quick_phrase_delete)
                .setMessage(R.string.quick_phrase_delete_confirm)
                .setPositiveButton(R.string.quick_phrase_delete) { _, _ ->
                    QuickPhraseHelper.remove(index)
                    refreshList()
                }
                .setNegativeButton(R.string.quick_phrase_cancel, null)
                .create()
        service.showDialog(dialog)
    }
}
