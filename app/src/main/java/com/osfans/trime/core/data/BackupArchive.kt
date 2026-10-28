// SPDX-FileCopyrightText: 2026 Chen IME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The portable archive format. No Android dependencies, so validation can be tested on the JVM. */
object BackupArchive {
    private const val APPLICATION = "com.c2cwl.chenime"
    private const val MAX_ENTRIES = 100_000
    private const val MAX_BYTES = 1024L * 1024 * 1024
    private const val MAX_METADATA_BYTES = 2 * 1024 * 1024

    @Serializable
    data class PreferenceValue(
        val type: String,
        val value: String = "",
        val values: List<String> = emptyList(),
    ) {
        fun decode(): Any = when (type) {
            "string" -> value
            "boolean" -> value.toBooleanStrict()
            "int" -> value.toInt()
            "long" -> value.toLong()
            "float" -> value.toFloat().also { require(it.isFinite()) }
            "string_set" -> values.toSet()
            else -> error("Unsupported preference type")
        }

        companion object {
            fun encode(value: Any): PreferenceValue = when (value) {
                is String -> PreferenceValue("string", value)
                is Boolean -> PreferenceValue("boolean", value.toString())
                is Int -> PreferenceValue("int", value.toString())
                is Long -> PreferenceValue("long", value.toString())
                is Float -> PreferenceValue("float", value.toString())
                is Set<*> -> PreferenceValue("string_set", values = value.map { it as String })
                else -> error("Unsupported preference type")
            }
        }
    }

    @Serializable
    data class Metadata(
        val format: Int = 1,
        val applicationId: String = APPLICATION,
        val versionName: String,
        val exportedAt: Long = System.currentTimeMillis(),
        val preferences: Map<String, PreferenceValue>,
    )

    private val json = Json { encodeDefaults = true }

    fun write(userDir: File, metadata: Metadata, output: OutputStream) {
        val root = userDir.canonicalFile
        var total = 0L
        var entries = 2
        val rawMetadata = json.encodeToString(metadata).encodeToByteArray()
        require(rawMetadata.size <= MAX_METADATA_BYTES) { "Backup preferences are too large" }
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(rawMetadata)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("rime/"))
            zip.closeEntry()
            userDir.walkTopDown().forEach { file ->
                if (file == userDir) return@forEach
                // Do not follow links out of the user's Rime directory.
                require(file.canonicalPath.startsWith(root.path + File.separator)) { "File outside user directory" }
                require(++entries <= MAX_ENTRIES) { "Too many backup entries" }
                val name = "rime/${file.relativeTo(userDir).invariantSeparatorsPath}"
                zip.putNextEntry(ZipEntry(if (file.isDirectory) "$name/" else name))
                if (file.isFile) {
                    file.inputStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_BYTES) { "Backup exceeds 1 GiB" }
                            zip.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    /** Extracts only to an empty staging directory; callers must validate before replacing live data. */
    fun read(input: InputStream, staging: File, maxBytes: Long = MAX_BYTES): Metadata {
        require(staging.isDirectory && staging.list().orEmpty().isEmpty()) { "Staging directory must be empty" }
        val root = staging.canonicalPath + File.separator
        val names = mutableSetOf<String>()
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.removeSuffix("/")
                require(names.size < MAX_ENTRIES && names.add(name)) { "Duplicate or excessive backup entries" }
                require(name == "metadata.json" || name == "rime" || name.startsWith("rime/")) { "Unknown backup entry" }
                require(!name.contains('\\') && name.split('/').none { it == "." || it == ".." || it.isEmpty() }) {
                    "Unsafe backup entry"
                }
                require(name != "metadata.json" || !entry.isDirectory) { "Invalid metadata entry" }
                require(name != "rime" || entry.isDirectory) { "Invalid user directory" }
                val file = staging.resolve(name).canonicalFile
                require(file.path.startsWith(root)) { "Backup entry escapes staging" }
                if (entry.isDirectory) {
                    require(zip.read() == -1) { "Directory entry contains file data" }
                    check(file.mkdirs() || file.isDirectory)
                } else {
                    check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
                    var size = 0L
                    file.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            size += count
                            total += count
                            require(total <= maxBytes) { "Backup exceeds size limit" }
                            require(name != "metadata.json" || size <= MAX_METADATA_BYTES) { "Metadata exceeds size limit" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        require("rime" in names && staging.resolve("rime").isDirectory) { "Missing Rime user data" }
        val metadata = json.decodeFromString<Metadata>(staging.resolve("metadata.json").readText())
        require(metadata.format == 1 && metadata.applicationId == APPLICATION) { "Not a supported Chen IME backup" }
        // Validate every preference before callers touch live files or preferences.
        metadata.preferences.values.forEach { it.decode() }
        return metadata
    }
}
