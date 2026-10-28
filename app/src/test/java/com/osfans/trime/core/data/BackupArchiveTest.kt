// SPDX-FileCopyrightText: 2026 Chen IME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core.data

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class BackupArchiveTest :
    StringSpec({
        "round trip preserves nested user files, empty directories, and preference types" {
            val temp = createTempDirectory().toFile()
            try {
                val user = temp.resolve("user").apply { mkdirs() }
                user.resolve("themes/empty").mkdirs()
                user.resolve("sync/device/userdb.txt").apply {
                    parentFile!!.mkdirs()
                    writeText("尘输入法\tchen shu ru fa\n")
                }
                val preferences = mapOf(
                    "string" to "iflytek",
                    "boolean" to true,
                    "int" to 20,
                    "long" to Long.MAX_VALUE,
                    "float" to 0.75f,
                    "set" to setOf("白霜", "明月"),
                )
                val metadata = BackupArchive.Metadata(
                    versionName = "0.1.0-chen",
                    preferences = preferences.mapValues { BackupArchive.PreferenceValue.encode(it.value) },
                )
                val output = ByteArrayOutputStream()
                BackupArchive.write(user, metadata, output)
                val restored = temp.resolve("restored").apply { mkdirs() }
                val result = BackupArchive.read(ByteArrayInputStream(output.toByteArray()), restored)
                result.preferences.mapValues { it.value.decode() } shouldBe preferences
                restored.resolve("rime/themes/empty").isDirectory shouldBe true
                restored.resolve("rime/sync/device/userdb.txt").readText() shouldBe
                    user.resolve("sync/device/userdb.txt").readText()
            } finally {
                temp.deleteRecursively()
            }
        }

        "rejects traversal, absolute paths, alternate separators, and unknown roots" {
            for (name in listOf("rime/../../escaped", "/rime/file", "rime/../escaped", "rime/./file", "rime\\file", "shared_prefs/file")) {
                withStaging { staging ->
                    shouldThrowAny { BackupArchive.read(zipOf(name to "bad"), staging) }
                    staging.parentFile!!.resolve("escaped").exists() shouldBe false
                }
            }
        }

        "rejects unsupported format and application before restoration" {
            for (metadata in listOf(
                """{"format":2,"versionName":"future","preferences":{}}""",
                """{"applicationId":"another.ime","versionName":"other","preferences":{}}""",
            )) {
                withStaging { staging ->
                    shouldThrowAny {
                        BackupArchive.read(zipOf("rime/" to "", "metadata.json" to metadata), staging)
                    }
                }
            }
        }

        "rejects invalid preference values before restoration" {
            withStaging { staging ->
                val metadata = """{"versionName":"test","preferences":{"flag":{"type":"boolean","value":"invalid"}}}"""
                shouldThrowAny {
                    BackupArchive.read(zipOf("rime/" to "", "metadata.json" to metadata), staging)
                }
            }
        }

        "rejects archives missing metadata or user data" {
            for (entries in listOf(
                arrayOf("rime/" to ""),
                arrayOf("metadata.json" to """{"versionName":"test","preferences":{}}"""),
            )) {
                withStaging { staging ->
                    shouldThrowAny { BackupArchive.read(zipOf(*entries), staging) }
                }
            }
        }

        "limits actual decompressed bytes rather than trusting ZIP entry sizes" {
            withStaging { staging ->
                shouldThrowAny {
                    BackupArchive.read(zipOf("rime/" to "", "rime/large" to "x".repeat(1024)), staging, maxBytes = 32)
                }
            }
        }

        "rejects directory entries hiding compressed content" {
            withStaging { staging ->
                shouldThrowAny { BackupArchive.read(zipOf("rime/" to "hidden content"), staging) }
            }
        }

        "refuses to extract into existing user data" {
            withStaging { staging ->
                staging.resolve("keep").writeText("current data")
                shouldThrowAny { BackupArchive.read(zipOf("rime/" to ""), staging) }
                staging.resolve("keep").readText() shouldBe "current data"
            }
        }
    })

private fun withStaging(block: (File) -> Unit) {
    val temp = createTempDirectory().toFile()
    try {
        block(temp.resolve("staging").apply { mkdirs() })
    } finally {
        temp.deleteRecursively()
    }
}

private fun zipOf(vararg entries: Pair<String, String>): ByteArrayInputStream {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        entries.forEach { (name, text) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(text.toByteArray())
            zip.closeEntry()
        }
    }
    return ByteArrayInputStream(output.toByteArray())
}
