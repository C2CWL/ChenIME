// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.ui.main

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.c2cwl.chenime.R
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.phrase.CustomPhraseDocument
import com.osfans.trime.data.phrase.CustomPhraseStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import splitties.systemservices.inputMethodManager

class CustomPhraseEditActivity : Activity() {
    private val scope = MainScope()
    private val sessionName = "${javaClass.name}@${System.identityHashCode(this)}"
    private val rime by lazy { RimeDaemon.createSession(sessionName) }
    private lateinit var phrase: EditText
    private lateinit var code: EditText
    private lateinit var weight: EditText
    private lateinit var confirm: Button
    private lateinit var delete: Button
    private var row: CustomPhraseDocument.Row? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes.gravity = Gravity.TOP
        title = getString(R.string.custom_phrases)
        val text = intent.getStringExtra(TEXT).orEmpty()
        val input = intent.getStringExtra(CODE).orEmpty()
        val position = if (intent.hasExtra(WEIGHT)) intent.getIntExtra(WEIGHT, 1).toString() else ""
        if (intent.hasExtra(ROW)) {
            row = CustomPhraseDocument.Row(intent.getIntExtra(ROW, -1), CustomPhraseDocument.Entry(text, input, position.toIntOrNull() ?: 1))
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        layout.addView(TextView(this).apply { setText(R.string.custom_phrase_format) })
        fun field(id: Int, hint: Int, value: String) = EditText(this).apply {
            this.id = id
            setHint(hint)
            setText(value)
            layout.addView(this)
        }
        phrase = field(android.R.id.text1, R.string.custom_phrase_text, text)
        code = field(android.R.id.text2, R.string.custom_phrase_code, input)
        weight = field(android.R.id.input, R.string.custom_phrase_weight, position).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val buttons = LinearLayout(this).apply { gravity = Gravity.END }
        fun button(label: Int, action: () -> Unit) = Button(this).apply {
            setText(label)
            setOnClickListener { action() }
            buttons.addView(this)
        }
        delete = button(R.string.custom_phrase_delete) { save(true) }.apply {
            visibility = if (row != null) View.VISIBLE else View.GONE
        }
        button(android.R.string.cancel) { finish() }
        confirm = button(android.R.string.ok) { save(false) }
        layout.addView(buttons)
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    override fun onPostResume() {
        super.onPostResume()
        val target = listOf(phrase, code, weight).firstOrNull { it.text.isEmpty() } ?: phrase
        target.requestFocus()
        target.post { inputMethodManager.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun save(remove: Boolean) {
        val entry = if (remove) {
            null
        } else {
            runCatching {
                CustomPhraseStore.editEntry(phrase.text.toString(), code.text.toString(), weight.text.toString())
            }.getOrElse {
                phrase.error = getString(R.string.custom_phrase_format)
                return
            }
        }
        confirm.isEnabled = false
        delete.isEnabled = false
        scope.launch {
            try {
                rime.runOnReady { CustomPhraseStore.save(this@CustomPhraseEditActivity, this, row, entry) }
                finish()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(this@CustomPhraseEditActivity, R.string.custom_phrase_save_failed, Toast.LENGTH_LONG).show()
            } finally {
                confirm.isEnabled = true
                delete.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        RimeDaemon.destroySession(sessionName)
        super.onDestroy()
    }

    companion object {
        const val TEXT = "text"
        const val CODE = "code"
        const val WEIGHT = "weight"
        const val ROW = "row"
    }
}
