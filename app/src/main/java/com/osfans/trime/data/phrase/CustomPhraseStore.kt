// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.data.phrase

import android.content.Context
import androidx.core.util.AtomicFile
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.data.UserDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CustomPhraseStore {
    fun editEntry(text: String, code: String, weight: String): CustomPhraseDocument.Entry = CustomPhraseDocument.Entry(text, code.trim(' '), weight.ifBlank { "1" }.toIntOrNull() ?: -1).also {
        CustomPhraseDocument("").validateEdit(it)
    }

    private val file get() = UserDataManager.userDataDir.resolve("custom_phrase.txt")

    private fun read(context: Context): String = if (file.exists()) file.readText() else context.assets.open("shared/custom_phrase.txt").bufferedReader().use { it.readText() }

    suspend fun load(context: Context): CustomPhraseDocument = withContext(Dispatchers.IO) {
        CustomPhraseDocument(read(context))
    }

    suspend fun save(context: Context, rime: RimeApi, row: CustomPhraseDocument.Row?, entry: CustomPhraseDocument.Entry?) {
        rime.withUserDataClosed {
            val document = CustomPhraseDocument(read(context))
            // Refuse stale edits instead of replacing another entry after a concurrent change.
            if (row != null) check(document.entries().contains(row)) { "Phrase changed; reopen the editor" }
            document.replace(row?.line, entry)
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try {
                output.write(document.toString().toByteArray(Charsets.UTF_8))
                atomic.finishWrite(output)
            } catch (e: Exception) {
                atomic.failWrite(output)
                throw e
            }
        }
        rime.deploy()
    }
}
