// SPDX-FileCopyrightText: 2026 Chen IME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core.data

import android.os.Build
import androidx.preference.PreferenceManager
import com.c2cwl.chenime.BuildConfig
import com.osfans.trime.TrimeApplication
import com.osfans.trime.core.RimeApi
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

object UserDataBackup {
    private val context get() = TrimeApplication.getInstance().directBootAwareContext
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)

    suspend fun export(rime: RimeApi, output: OutputStream) {
        rime.withUserDataClosed {
            val snapshot = preferences.all.mapNotNull { (key, value) ->
                value?.let { key to BackupArchive.PreferenceValue.encode(it) }
            }.toMap()
            BackupArchive.write(
                UserDataManager.userDataDir,
                BackupArchive.Metadata(versionName = BuildConfig.VERSION_NAME, preferences = snapshot),
                output,
            )
        }
    }

    suspend fun restore(rime: RimeApi, input: InputStream) {
        val staging = withContext(Dispatchers.IO) {
            File.createTempFile("chenime-import-", "", context.cacheDir).apply {
                check(delete() && mkdir()) { "Cannot create backup staging directory" }
            }
        }
        try {
            val metadata = withContext(Dispatchers.IO) { BackupArchive.read(input, staging) }
            // Complete or roll back once mutation starts, even if the activity goes away.
            withContext(NonCancellable) {
                rime.withUserDataClosed {
                    val userDir = UserDataManager.userDataDir
                    val incoming = userDir.resolveSibling(".chenime-incoming-${UUID.randomUUID()}")
                    val previous = userDir.resolveSibling(".chenime-previous-${UUID.randomUUID()}")
                    val oldPreferences = preferences.all
                    var moved = false
                    var committed = false
                    try {
                        check(staging.resolve("rime").copyRecursively(incoming))
                        check(userDir.renameTo(previous)) { "Cannot preserve current user data" }
                        moved = true
                        check(incoming.renameTo(userDir)) { "Cannot install restored user data" }
                        val restored = metadata.preferences.mapValues { it.value.decode() }.toMutableMap()
                        // SAF grants are device specific. Prevent an old external folder from
                        // overwriting the restored files during deploy or background sync.
                        restored[AppPrefs.Profile.DATA_STORAGE_MODE] = DataStorageMode.APP_STORAGE.name
                        restored[AppPrefs.Profile.PERIODIC_BACKGROUND_SYNC] = false
                        restored.remove(AppPrefs.Profile.EXTERNAL_RIME_TREE_URI)
                        restored.remove(AppPrefs.Profile.EXTERNAL_RIME_DISPLAY_NAME)
                        oldPreferences[AppPrefs.Internal.PID]?.let { restored[AppPrefs.Internal.PID] = it }
                        check(replacePreferences(restored)) { "Cannot save restored preferences" }
                        committed = true
                    } finally {
                        incoming.deleteRecursively()
                        if (committed) {
                            previous.deleteRecursively()
                        } else if (moved) {
                            check(userDir.deleteRecursively() && previous.renameTo(userDir)) {
                                "Cannot roll back user data; previous data retained at ${previous.name}"
                            }
                            check(replacePreferences(oldPreferences)) { "Cannot roll back preferences" }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    AppPrefs.defaultInstance().syncToDeviceEncryptedStorage()
                    ThemeManager.syncToDeviceEncryptedStorage()
                }
                rime.deploy()
                ThemeManager.selectTheme(ThemeManager.prefs.selectedTheme.getValue())
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { staging.deleteRecursively() }
        }
    }

    private fun replacePreferences(values: Map<String, *>): Boolean {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
            }
        }
        return editor.commit()
    }
}
