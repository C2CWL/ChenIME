/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

import android.content.Context
import com.c2cwl.chenime.R
import com.osfans.trime.core.data.FrostUpdateState
import com.osfans.trime.core.data.FrostUpdateStateStore
import com.osfans.trime.core.data.UserDataManager
import com.osfans.trime.util.customFormatTimeInDefault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@Serializable
data class FrostUpdateManifest(
    val version: String,
    val zipSha256: String,
    val fileCount: Int = 0,
    val files: List<FrostUpdateManifestEntry> = emptyList(),
)

@Serializable
data class FrostUpdateManifestEntry(
    val path: String,
    val sha256: String,
)

data class StagedFrostUpdate(
    val manifest: FrostUpdateManifest,
    val stageDir: File,
    val paths: List<String>,
)

sealed class FrostUpdateFetchResult {
    data class Staged(
        val update: StagedFrostUpdate,
    ) : FrostUpdateFetchResult()

    object UpToDate : FrostUpdateFetchResult()
}

object RimeResourceUpdater {
    private const val DOWNLOAD_DIR_NAME = "frost-update-download"
    private const val BACKUP_DIR_NAME = "frost-update-backup"
    private const val MANIFEST_NAME = "manifest.json"
    private const val ZIP_NAME = "frost-update.zip"
    private const val PREFS_NAME = "frost_update"
    private const val KEY_STATUS = "status"
    private const val KEY_DETAIL = "detail"
    private const val KEY_VERSION = "version"
    private const val KEY_APPLIED_AT = "applied_at"

    private const val RAW_BASE = "https://raw.githubusercontent.com/C2CWL/ChenIME/gh-pages/frost-update/"
    private const val CDN_BASE = "https://cdn.jsdelivr.net/gh/C2CWL/ChenIME@gh-pages/frost-update/"

    private val json = Json { ignoreUnknownKeys = true }
    private val sources =
        listOf(
            Source("raw.githubusercontent.com", RAW_BASE),
            Source("jsDelivr", CDN_BASE),
        )

    private data class Source(
        val name: String,
        val baseUrl: String,
    )

    private val allowedPrefixes =
        listOf(
            "cn_dicts/",
            "lua/",
            "rime_frost/",
            "opencc/",
        )

    private val allowedFiles =
        setOf(
            "rime_frost.dict.yaml",
            "rime_frost.schema.yaml",
            "rime_frost_aux.dict.yaml",
            "rime_frost_aux.schema.yaml",
            "symbols_v.yaml",
            "zh-moqi.gram",
        )

    suspend fun fetch(
        context: Context,
        onProgress: (String) -> Unit = {},
    ): FrostUpdateFetchResult = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, DOWNLOAD_DIR_NAME)
        if (workDir.exists() && !workDir.deleteRecursively()) {
            throw IOException("Cannot clean update cache: ${workDir.absolutePath}")
        }
        if (!workDir.mkdirs()) throw IOException("Cannot create update cache: ${workDir.absolutePath}")

        val currentState = FrostUpdateStateStore.read(context)
        var lastError: Exception? = null

        for (source in sources) {
            try {
                onProgress("正在从 ${source.name} 检查更新")
                val manifestFile = File(workDir, "manifest-${source.safeName()}.json")
                download(source.baseUrl + MANIFEST_NAME, manifestFile) {}
                val manifest =
                    runCatching {
                        json.decodeFromString<FrostUpdateManifest>(manifestFile.readText())
                    }.getOrElse {
                        throw IOException("更新清单解析失败（${source.name}）", it)
                    }
                if (manifest.version.isBlank() || manifest.zipSha256.isBlank()) {
                    throw IOException("更新清单不完整（${source.name}）")
                }
                if (
                    currentState != null &&
                    currentState.version == manifest.version &&
                    currentState.zipSha256.equals(manifest.zipSha256, ignoreCase = true)
                ) {
                    return@withContext FrostUpdateFetchResult.UpToDate
                }

                val zipFile = File(workDir, ZIP_NAME)
                if (zipFile.exists() && !zipFile.delete()) zipFile.writeText("")
                download(source.baseUrl + ZIP_NAME, zipFile) { percent ->
                    onProgress("正在从 ${source.name} 下载 $percent%")
                }

                val zipSha256 = sha256(zipFile)
                if (!zipSha256.equals(manifest.zipSha256, ignoreCase = true)) {
                    throw IOException("更新包校验失败（${source.name}）")
                }

                val stageDir = File(workDir, "stage-${source.safeName()}")
                if (stageDir.exists() && !stageDir.deleteRecursively()) {
                    throw IOException("Cannot clean staged update: ${stageDir.absolutePath}")
                }
                val paths = extract(zipFile, stageDir)
                verifyExtracted(stageDir, manifest)
                Timber.i("Frost update staged: ${manifest.version} from ${source.name}, ${paths.size} files")
                return@withContext FrostUpdateFetchResult.Staged(
                    StagedFrostUpdate(manifest, stageDir, paths),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Frost update failed from ${source.name}")
                lastError = e
            }
        }
        throw IOException("所有更新源均失败：${lastError?.localizedMessage ?: "未知错误"}", lastError)
    }

    /** Apply a staged update. Must run while Rime is closed. */
    fun apply(
        context: Context,
        staged: StagedFrostUpdate,
    ) {
        val userDataDir = UserDataManager.userDataDir
        if (!userDataDir.isDirectory && !userDataDir.mkdirs()) {
            throw IOException("Cannot create user data dir: ${userDataDir.absolutePath}")
        }

        val backupRoot = File(context.cacheDir, BACKUP_DIR_NAME)
        if (backupRoot.exists() && !backupRoot.deleteRecursively()) {
            throw IOException("Cannot clean update backup: ${backupRoot.absolutePath}")
        }

        val appliedFiles = mutableListOf<File>()
        try {
            for (relativePath in staged.paths) {
                val source = staged.stageDir.resolve(relativePath)
                val target = userDataDir.resolve(relativePath)
                val backup = backupRoot.resolve(relativePath)
                if (!source.isFile) throw IOException("更新包缺少文件：$relativePath")
                if (target.isFile) {
                    backup.parentFile?.mkdirs()
                    sourceCopy(target, backup)
                }
                target.parentFile?.mkdirs()
                sourceCopy(source, target)
                appliedFiles += target
            }

            FrostUpdateStateStore.write(
                context,
                FrostUpdateState(
                    version = staged.manifest.version,
                    zipSha256 = staged.manifest.zipSha256,
                    appliedAt = System.currentTimeMillis(),
                    paths = staged.paths,
                ),
            )
            recordSuccess(context, staged)
        } catch (e: Exception) {
            for (target in appliedFiles) {
                runCatching {
                    val relativePath = target.relativeTo(userDataDir).path
                    val backup = backupRoot.resolve(relativePath)
                    if (target.exists() && !target.delete()) throw IOException("Cannot remove $relativePath during rollback")
                    if (backup.isFile) {
                        target.parentFile?.mkdirs()
                        sourceCopy(backup, target)
                    }
                }
            }
            throw IOException("写入白霜更新失败，已尝试回滚", e)
        }
    }

    fun recordChecking(context: Context) {
        saveStatus(context, "checking")
    }

    fun recordUpToDate(context: Context) {
        saveStatus(context, "up_to_date")
    }

    fun recordFailure(
        context: Context,
        error: Throwable,
    ) {
        Timber.w(error, "Frost update failed")
        saveStatus(context, "failed", error.localizedMessage ?: error.javaClass.simpleName)
    }

    fun recordDownloading(context: Context) {
        saveStatus(context, "downloading")
    }

    fun statusSummary(context: Context): String {
        val prefs = statusPrefs(context)
        val status = prefs.getString(KEY_STATUS, "idle") ?: "idle"
        val detail = prefs.getString(KEY_DETAIL, "").orEmpty()
        val version = prefs.getString(KEY_VERSION, "").orEmpty()
        val appliedAt = prefs.getLong(KEY_APPLIED_AT, 0L)
        val lastUpdated =
            if (appliedAt > 0L) {
                "\n" + context.getString(R.string.frost_update_status_last_updated, customFormatTimeInDefault("yyyy-MM-dd HH:mm", appliedAt))
            } else {
                ""
            }
        return when (status) {
            "checking" -> context.getString(R.string.frost_update_status_checking)
            "downloading" -> context.getString(R.string.frost_update_status_downloading)
            "up_to_date" -> context.getString(R.string.frost_update_status_up_to_date) + lastUpdated
            "success" -> context.getString(R.string.frost_update_status_success, version) + lastUpdated
            "failed" -> context.getString(R.string.frost_update_status_failed, detail) + lastUpdated
            else -> context.getString(R.string.frost_update_status_idle)
        }
    }

    private fun recordSuccess(
        context: Context,
        staged: StagedFrostUpdate,
    ) {
        saveStatus(
            context,
            "success",
            version = staged.manifest.version,
            appliedAt = System.currentTimeMillis(),
        )
    }

    private fun saveStatus(
        context: Context,
        status: String,
        detail: String = "",
        version: String = statusPrefs(context).getString(KEY_VERSION, "").orEmpty(),
        appliedAt: Long = statusPrefs(context).getLong(KEY_APPLIED_AT, 0L),
    ) {
        statusPrefs(context).edit()
            .putString(KEY_STATUS, status)
            .putString(KEY_DETAIL, detail)
            .putString(KEY_VERSION, version)
            .putLong(KEY_APPLIED_AT, appliedAt)
            .apply()
    }

    private fun statusPrefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun Source.safeName(): String = name.replace(Regex("[^A-Za-z0-9]"), "_")

    private fun download(
        url: String,
        destination: File,
        onProgress: (Int) -> Unit,
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty("User-Agent", "ChenIME-FrostUpdater")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("下载失败（HTTP $code）：$url")
            val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        total += read
                        if (contentLength > 0L) {
                            onProgress(((total * 100) / contentLength).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extract(
        zipFile: File,
        stageDir: File,
    ): List<String> {
        if (!stageDir.mkdirs() && !stageDir.isDirectory) {
            throw IOException("Cannot create stage dir: ${stageDir.absolutePath}")
        }
        val paths = mutableListOf<String>()
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().toList().filterNot { it.isDirectory }
            if (entries.isEmpty()) throw IOException("更新包为空")
            for (entry in entries) {
                val relativePath = safeRelativePath(entry)
                val target = stageDir.resolve(relativePath)
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                paths += relativePath
            }
        }
        return paths.sorted()
    }

    private fun verifyExtracted(
        stageDir: File,
        manifest: FrostUpdateManifest,
    ) {
        if (manifest.files.isEmpty()) return
        for (entry in manifest.files) {
            val path = safeRelativePath(entry.path)
            val file = stageDir.resolve(path)
            if (!file.isFile) throw IOException("更新清单中的文件缺失：$path")
            val actual = sha256(file)
            if (!actual.equals(entry.sha256, ignoreCase = true)) {
                throw IOException("更新文件校验失败：$path")
            }
        }
    }

    private fun safeRelativePath(name: String): String {
        var path = name.replace('\\', '/')
        while (path.startsWith("./")) path = path.removePrefix("./")
        if (path.startsWith("/") || path.contains("..") || path.isBlank()) {
            throw IOException("更新包包含非法路径：$name")
        }
        val allowed =
            allowedPrefixes.any { path.startsWith(it) } ||
                path in allowedFiles
        if (!allowed) throw IOException("更新包包含未授权路径：$name")
        return path
    }

    private fun safeRelativePath(entry: ZipEntry): String = safeRelativePath(entry.name)

    /** java.io based copy; java.nio.file.Files requires API 26 but minSdk is 21. */
    private fun sourceCopy(
        source: File,
        target: File,
    ) {
        source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
    }
}
