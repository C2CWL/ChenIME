// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.data.phrase

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import com.c2cwl.chenime.R
import com.osfans.trime.daemon.RimeSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object CustomPhraseDialogs {
    fun edit(context: Context, scope: CoroutineScope, rime: RimeSession, text: String = "", code: String = "", row: CustomPhraseDocument.Row? = null, changed: () -> Unit = {}) {
        val fields = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        fun field(hintRes: Int, value: String) = EditText(context).apply {
            setHint(hintRes)
            setText(value)
            fields.addView(this)
        }
        val phrase = field(R.string.custom_phrase_text, row?.entry?.text ?: text)
        val input = field(R.string.custom_phrase_code, row?.entry?.code ?: code)
        val weight = field(R.string.custom_phrase_weight, row?.entry?.weight?.toString() ?: "")
        weight.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.custom_phrases)
            .setMessage(R.string.custom_phrase_format)
            .setView(fields)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .apply { if (row != null) setNeutralButton(R.string.custom_phrase_delete, null) }
            .create()
        dialog.show()
        fun save(delete: Boolean) {
            val entry = if (delete) {
                null
            } else {
                runCatching {
                    CustomPhraseStore.editEntry(phrase.text.toString(), input.text.toString(), weight.text.toString())
                }.getOrElse {
                    phrase.error = context.getString(R.string.custom_phrase_format)
                    return
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = false
            scope.launch {
                try {
                    rime.runOnReady { CustomPhraseStore.save(context, this, row, entry) }
                    dialog.dismiss()
                    changed()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.custom_phrase_save_failed, Toast.LENGTH_LONG).show()
                } finally {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = true
                }
            }
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save(false) }
        if (row != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { save(true) }
    }

    fun list(context: Context, scope: CoroutineScope, rime: RimeSession) {
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val search = EditText(context).apply { setHint(R.string.custom_phrase_search) }
        val list = ListView(context)
        layout.addView(search)
        layout.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        var rows = emptyList<CustomPhraseDocument.Row>()
        var filtered = rows
        fun filter() {
            val query = search.text.toString()
            filtered = rows.filter { it.entry.code.contains(query, true) || it.entry.text.contains(query, true) }
            list.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, filtered.map { "${it.entry.code} → ${it.entry.text} (${it.entry.weight})" })
        }
        fun reload() {
            scope.launch {
                try {
                    rows = CustomPhraseStore.load(context).entries()
                    filter()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.custom_phrase_save_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = filter()
            override fun afterTextChanged(s: Editable?) {}
        })
        list.setOnItemClickListener { _, _, position, _ -> edit(context, scope, rime, row = filtered[position], changed = ::reload) }
        val dialog = AlertDialog.Builder(context).setTitle(R.string.custom_phrases).setView(layout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.custom_phrase_add, null).create()
        dialog.show()
        dialog.window?.setLayout(-1, (context.resources.displayMetrics.heightPixels * 0.75).toInt())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { edit(context, scope, rime, changed = ::reload) }
        reload()
    }
}
