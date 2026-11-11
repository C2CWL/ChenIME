// SPDX-FileCopyrightText: 2026 ChenIME contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util.diagnostics

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class LagLogCoreTest :
    BehaviorSpec({
        given("a lag log line") {
            then("it round-trips and exposes duration and SLOW") {
                val line = LagLine.format("2026-11-12 00:30:01.123", "main", "input_view_build", LagLine.durationDetails(180, 50, "n=2 reason=theme_changed"))
                line shouldBe "2026-11-12 00:30:01.123 [main] input_view_build dur_ms=180 SLOW n=2 reason=theme_changed"
                val parsed = LagLine.parse(line)!!
                parsed.category shouldBe "input_view_build"
                parsed.thread shouldBe "main"
                parsed.durationMs shouldBe 180L
                parsed.slow shouldBe true
            }
            then("a fast event is not marked SLOW and has no trailing space") {
                LagLine.durationDetails(12, 50) shouldBe "dur_ms=12"
                LagLine.parse(LagLine.format("t t", "rime-main", "rime_startup", "dur_ms=12"))!!.slow shouldBe false
                LagLine.format("t t", "main", "expanded_panel_open", "") shouldBe "t t [main] expanded_panel_open"
            }
            then("details can never split one event into several lines") {
                LagLine.format("t t", "main", "x", "a\nb\rc") shouldBe "t t [main] x a b c"
            }
            then("an event without a duration parses with a null duration") {
                LagLine.parse("t t [main] window_shown first=true java_heap_mb=20/256")!!.durationMs shouldBe null
                LagLine.parse("garbage") shouldBe null
            }
        }

        given("the capped two-file store") {
            then("it rotates once the current file is full and keeps only two files") {
                val dir = Files.createTempDirectory("lag").toFile()
                val store = LagLogStore(dir, maxBytes = 20)
                store.append(listOf("first-line-aaaaaaaaaaaa")) // 24 bytes: already full
                store.append(listOf("second")) // rotates first -> lag.log.1
                store.readAll() shouldBe listOf("first-line-aaaaaaaaaaaa", "second")
                store.append(listOf("third-line-bbbbbbbbbbbb"))
                store.append(listOf("fourth")) // drops "first", rotates second+third
                store.readAll() shouldBe listOf("second", "third-line-bbbbbbbbbbbb", "fourth")
                dir.list()!!.sorted() shouldBe listOf(LagLogStore.CURRENT, LagLogStore.PREVIOUS)
                dir.deleteRecursively()
            }
            then("clear removes everything and size reports zero") {
                val dir = Files.createTempDirectory("lag").toFile()
                val store = LagLogStore(dir)
                store.append(listOf("a", "b"))
                store.sizeBytes() shouldBe 4L
                store.clear()
                store.sizeBytes() shouldBe 0L
                store.readAll() shouldBe emptyList()
                dir.deleteRecursively()
            }
            then("it survives a missing directory, as after clearing app data") {
                val dir = Files.createTempDirectory("lag").toFile().resolve("nested/diagnostics")
                LagLogStore(dir).apply { append(listOf("x")) }.readAll() shouldBe listOf("x")
                dir.parentFile.parentFile.deleteRecursively()
            }
        }

        given("duration statistics") {
            then("nearest-rank percentiles match the obvious answers") {
                val values = (1L..100L).toList()
                DurationStats.percentileOf(values, 0.50) shouldBe 50L
                DurationStats.percentileOf(values, 0.95) shouldBe 95L
                DurationStats.percentileOf(listOf(7L), 0.95) shouldBe 7L
                DurationStats.percentileOf(emptyList(), 0.5) shouldBe 0L
            }
            then("the sample is bounded but count and max cover everything") {
                val stats = DurationStats(capacity = 3)
                listOf(500L, 1L, 2L, 3L).forEach(stats::add)
                stats.count shouldBe 4L
                stats.max shouldBe 500L
                stats.percentile(1.0) shouldBe 3L // 500 was evicted from the window
                stats.describe("keys") shouldBe "keys=4 keys_p50_ms=2 keys_p95_ms=3 keys_max_ms=500"
                stats.reset()
                stats.describe("keys") shouldBe "keys=0"
            }
        }

        given("the main-thread stall detector") {
            then("a prompt heartbeat is not a stall") {
                val d = StallDetector(300)
                d.posted(1_000)
                d.check(1_100) shouldBe null
                d.acknowledged(1_120) shouldBe null
                d.waiting shouldBe false
            }
            then("a blocked main thread is reported once, then its total length on recovery") {
                val d = StallDetector(300)
                d.posted(1_000)
                d.check(1_299) shouldBe null
                d.check(1_350) shouldBe 350L
                d.check(1_900) shouldBe null // already reported
                d.acknowledged(2_400) shouldBe 1_400L
                d.posted(2_500)
                d.check(2_600) shouldBe null // the next heartbeat starts clean
            }
            then("nothing is reported before a heartbeat is posted") {
                StallDetector(300).check(10_000) shouldBe null
                StallDetector(300).acknowledged(10_000) shouldBe null
            }
        }

        given("frame statistics at 60 Hz") {
            val period = 16_666_667L
            then("normal frames are counted but not janky or logged") {
                val f = FrameStats()
                f.record(period, period) shouldBe false
                f.record(period * 2, period) shouldBe false
                f.janky shouldBe 0L
                f.frames shouldBe 2L
            }
            then("dropped frames are janky, long ones are logged and the worst is kept") {
                val f = FrameStats()
                f.record(40_000_000, period) shouldBe false // janky, under the log threshold
                f.record(60_000_000, period) shouldBe true
                f.record(250_000_000, period) shouldBe true
                f.janky shouldBe 3L
                f.severe shouldBe 1L
                f.maxMs shouldBe 250L
                f.describe() shouldBe "frames=3 janky_frames=3 frames_over_100ms=1 max_frame_ms=250"
            }
            then("a non-positive interval is ignored") {
                FrameStats().apply { record(0, period) }.frames shouldBe 0L
            }
        }

        given("the exported summary") {
            then("an empty log tells the user how to record one") {
                LagReport.summarize(emptyList()) shouldContain "卡顿诊断"
            }
            then("it groups durations by category, counts stalls and lists the slowest first") {
                val lines = listOf(
                    "2026-11-12 00:00:00.000 [main] process_start pid=1",
                    "2026-11-12 00:00:00.100 [rime-main] rime_startup dur_ms=900 SLOW",
                    "2026-11-12 00:00:01.000 [main] input_view_build dur_ms=120 SLOW n=1 reason=create_input_view",
                    "2026-11-12 00:00:02.000 [main] input_view_build dur_ms=30 n=2 reason=theme_changed",
                    "2026-11-12 00:00:03.000 [lag-watchdog] main_stall waited_ms=320 stack=Foo.bar:1",
                )
                val summary = LagReport.summarize(lines, topN = 2)
                summary shouldContain "记录条数: 5"
                summary shouldContain "2026-11-12 00:00:00.000 ~ 2026-11-12 00:00:03.000"
                summary shouldContain "主线程卡死(≥300ms)次数: 1"
                // count / slow / p50 / p95 / max
                Regex("""input_view_build\s+2 /\s+1 /\s+30 /\s+120 /\s+120""").containsMatchIn(summary) shouldBe true
                // worst category first
                (summary.indexOf("rime_startup") < summary.indexOf("input_view_build")) shouldBe true
                summary shouldContain "最慢的 2 次"
                summary shouldNotContain "theme_changed" // only the top 2 are listed
            }
        }
    })
