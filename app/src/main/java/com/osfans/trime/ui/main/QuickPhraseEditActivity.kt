/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.c2cwl.chenime.R
import com.osfans.trime.data.db.QuickPhraseHelper
import splitties.systemservices.inputMethodManager

/**
 * 常用语编辑页：用独立 Activity 提供正常软键盘输入，
 * 避免 IME 弹窗里 EditText 因 FLAG_ALT_FOCUSABLE_IM 无法调起键盘。
 */
class QuickPhraseEditActivity : Activity() {
    private var index = -1
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes.gravity = Gravity.TOP
        index = intent.getIntExtra(EXTRA_INDEX, -1)
        val initial = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        title = getString(if (index < 0) R.string.quick_phrase_add else R.string.quick_phrase_edit)

        val padding = (20 * resources.displayMetrics.density).toInt()
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding)
            }
        content.addView(
            TextView(this).apply {
                text = getString(R.string.quick_phrase_hint)
                textSize = 14f
            },
        )
        input =
            EditText(this).apply {
                setText(initial)
                setSelection(initial.length)
                hint = getString(R.string.quick_phrase_hint)
                maxLines = 6
            }
        content.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = padding / 2 },
        )
        val actions =
            LinearLayout(this).apply {
                gravity = Gravity.END
            }
        actions.addView(
            Button(this).apply {
                text = getString(R.string.quick_phrase_cancel)
                setOnClickListener { finish() }
            },
        )
        actions.addView(
            Button(this).apply {
                text = getString(R.string.quick_phrase_save)
                setOnClickListener { save() }
            },
        )
        content.addView(
            actions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = padding },
        )
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onPostResume() {
        super.onPostResume()
        input.requestFocus()
        input.post { inputMethodManager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun save() {
        val value = input.text.toString().trim()
        if (value.isEmpty()) {
            input.error = getString(R.string.quick_phrase_hint)
            return
        }
        if (index < 0) {
            QuickPhraseHelper.add(value)
        } else {
            QuickPhraseHelper.update(index, value)
        }
        finish()
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    companion object {
        const val EXTRA_INDEX = "index"
        const val EXTRA_TEXT = "text"
    }
}
