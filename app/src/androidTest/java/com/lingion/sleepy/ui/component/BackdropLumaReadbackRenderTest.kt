package com.lingion.sleepy.ui.component

import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lingion.sleepy.ui.component.liquidglass.BackdropLumaSample
import com.lingion.sleepy.ui.component.liquidglass.BackdropContentSignal
import com.lingion.sleepy.ui.component.liquidglass.notifyBackdropDraw
import com.lingion.sleepy.ui.component.liquidglass.withContentSignal
import com.lingion.sleepy.ui.component.liquidglass.sampleBackdropLuma
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the actual Activity dispatcher, without Compose's virtual effect scheduler. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class BackdropLumaReadbackRenderTest {
    @Test fun realActivityReadsExternalPixelsAndFollowsTheConsumerCoordinates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "dock-appearance-94").apply { check(isDirectory || mkdirs()) }
        val samples = ConcurrentLinkedQueue<BackdropLumaSample>()
        val progress = AtomicReference("before-launch")
        val sourceDraws = AtomicInteger()
        val watchdog = Thread {
            try {
                Thread.sleep(8_000)
                File(directory, "real-activity-watchdog.json").writeText(JSONObject()
                    .put("stage", progress.get()).put("source_draws", sourceDraws.get())
                    .put("samples", JSONArray(samples.map(::sampleJson)))
                    .put("threads", Thread.getAllStackTraces().entries.joinToString("\n\n") {
                        (thread, stack) -> "${thread.name} ${thread.state}\n${stack.joinToString("\n")}"
                    }).toString(2))
            } catch (_: InterruptedException) { }
        }.apply { isDaemon = true; name = "LumaReadbackWatchdog"; start() }
        val stages = JSONArray()
        val expectedLuma = AtomicReference(64f / 255)
        val gate = AtomicReference(CountDownLatch(1))
        var sourceGray by mutableIntStateOf(64)
        var alpha by mutableIntStateOf(255)
        var pattern by mutableIntStateOf(0)
        var consumerX by mutableIntStateOf(44)
        var sourceIdentity by mutableIntStateOf(0)
        var fallback by mutableStateOf(Color.White)
        ActivityScenario.launch<ComponentActivity>(Intent(context, ComponentActivity::class.java)).use { scenario ->
            progress.set("before-initial-onActivity")
            scenario.onActivity { activity ->
                progress.set("initial-onActivity")
                activity.setContent {
                    val source = key(sourceIdentity) { rememberLayerBackdrop() }
                    Box(Modifier.requiredSize(300.dp, 210.dp).background(Color.Magenta)) {
                        Canvas(Modifier.fillMaxSize().layerBackdrop(source)) {
                            sourceDraws.incrementAndGet()
                            if (pattern == 0) drawRect(Color(sourceGray, sourceGray, sourceGray, alpha))
                            else {
                                drawRect(Color.Black)
                                drawRect(Color.White, Offset(150.dp.toPx(), 0f), Size(size.width, size.height))
                            }
                        }
                        Canvas(Modifier.offset(consumerX.dp, 81.dp).requiredSize(160.dp, 80.dp)
                            .sampleBackdropLuma(source, fallback, { size -> Rect(Offset.Zero, size) }, { sample ->
                                check(Looper.myLooper() == Looper.getMainLooper())
                                samples.add(sample)
                                if (abs(sample.meanLuma - expectedLuma.get()) < .012f) gate.get().countDown()
                            })) {
                            // This consumer and its magenta container must not enter the sample.
                            drawRect(Color.Green)
                        }
                    }
                }
            }
            fun awaitStage(name: String) {
                val arrived = gate.get().await(5, TimeUnit.SECONDS)
                stages.put(JSONObject().put("name", name).put("arrived", arrived)
                    .put("expected", expectedLuma.get()).put("observed", samples.lastOrNull()?.meanLuma))
                File(directory, "real-activity-samples.json").writeText(JSONObject().put("status", "running")
                    .put("stages", stages).put("samples", JSONArray(samples.map(::sampleJson))).toString(2))
                assertTrue("$name source sample must arrive on the real Activity dispatcher", arrived)
            }
            progress.set("after-initial-onActivity")
            awaitStage("opaque64")
            fun update(name: String, expected: Float, block: () -> Unit) {
                expectedLuma.set(expected)
                gate.set(CountDownLatch(1))
                progress.set("before-update-$name")
                scenario.onActivity { progress.set("inside-update-$name"); block() }
                progress.set("after-update-$name")
                awaitStage(name)
            }
            update("opaque230", 230f / 255) { sourceGray = 230 }
            update("half-black-over-white", 127f / 255) { sourceGray = 0; alpha = 128 }
            update("transparent-over-white", 1f) { alpha = 0 }
            update("five-external-spatial-taps", .4f) { pattern = 1 }
            assertEquals(listOf(android.graphics.Color.BLACK, android.graphics.Color.BLACK, android.graphics.Color.WHITE,
                android.graphics.Color.BLACK, android.graphics.Color.WHITE), samples.last().sampledArgb)
            update("translated-consumer", 1f) { consumerX = 144 }
            val oldGeneration = samples.last().generation
            update("source-replacement", 77f / 255) {
                sourceIdentity++; pattern = 0; alpha = 255; sourceGray = 77
            }
            assertTrue("Replacing a source must invalidate its previous generation", samples.last().generation > oldGeneration)
            update("fallback-replacement", 0f) { fallback = Color.Black; alpha = 0 }
            scenario.onActivity { }
            SystemClock.sleep(100)
            val quietSamples = samples.size
            val quietDraws = sourceDraws.get()
            SystemClock.sleep(250)
            assertEquals("An unchanged source must not keep polling itself", quietSamples, samples.size)
            assertEquals("Sampling must not redraw the sibling producer", quietDraws, sourceDraws.get())
            stages.put(JSONObject().put("name", "unchanged-source-idle").put("duration_ms", 250)
                .put("samples_before", quietSamples).put("samples_after", samples.size)
                .put("source_draws_before", quietDraws).put("source_draws_after", sourceDraws.get()))
        }
        watchdog.interrupt()
        File(directory, "real-activity-samples.json").writeText(JSONObject().put("status", "passed")
            .put("scope", "Real Activity source/transparency/translation and timing; not a native material or jank verdict")
            .put("stages", stages).put("samples", JSONArray(samples.map(::sampleJson))).toString(2))
    }

    @Test fun explicitSignalFollowsAnIndependentlyRetainedSourceWithoutPolling() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "dock-appearance-94").apply { check(isDirectory || mkdirs()) }
        var gray by mutableIntStateOf(26)
        var source: LayerBackdrop? = null
        val samples = ConcurrentLinkedQueue<BackdropLumaSample>()
        val gate = AtomicReference(CountDownLatch(1))
        val expected = AtomicReference(26f / 255)
        val draws = AtomicInteger()
        val stages = JSONArray()
        ActivityScenario.launch<ComponentActivity>(Intent(context, ComponentActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    val backdrop = rememberLayerBackdrop()
                    val signal = remember { BackdropContentSignal() }
                    val notifiedBackdrop = remember(backdrop, signal) { backdrop.withContentSignal(signal) }
                    source = backdrop
                    Box(Modifier.requiredSize(300.dp, 210.dp)) {
                        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                            Canvas(Modifier.fillMaxSize().clipToBounds().notifyBackdropDraw(signal)) {
                                draws.incrementAndGet()
                                drawRect(Color(gray, gray, gray))
                            }
                        }
                        Canvas(Modifier.offset(44.dp, 81.dp).requiredSize(160.dp, 80.dp)
                            .sampleBackdropLuma(notifiedBackdrop, Color.White, { Rect(Offset.Zero, it) }, { sample ->
                                check(Looper.myLooper() == Looper.getMainLooper())
                                samples.add(sample)
                                if (abs(sample.meanLuma - expected.get()) < .012f) gate.get().countDown()
                            })) { drawRect(Color.Green) }
                    }
                }
            }
            fun awaitStage(name: String) {
                val arrived = gate.get().await(5, TimeUnit.SECONDS)
                stages.put(JSONObject().put("name", name).put("arrived", arrived)
                    .put("expected", expected.get()).put("observed", samples.lastOrNull()?.meanLuma)
                    .put("parent_recording_revision", source?.recordingRevision).put("leaf_draws", draws.get()))
                File(directory, "explicit-source-samples.json").writeText(JSONObject().put("status", "running")
                    .put("stages", stages).put("samples", JSONArray(samples.map(::sampleJson))).toString(2))
                assertTrue("$name retained source must notify the sampler", arrived)
            }
            awaitStage("initial26")
            scenario.onActivity { }
            SystemClock.sleep(100)
            val originalParentRevision = checkNotNull(source).recordingRevision
            for (value in listOf(230, 77, 217, 26)) {
                expected.set(value / 255f)
                gate.set(CountDownLatch(1))
                scenario.onActivity { gray = value }
                awaitStage("retained$value")
                assertEquals("Changing the retained leaf must not re-record the parent",
                    originalParentRevision, checkNotNull(source).recordingRevision)
                assertEquals("An explicit signal is not a LayerBackdrop recording revision", null, samples.last().sourceRevision)
            }
            scenario.onActivity { }
            SystemClock.sleep(100)
            val quietSamples = samples.size
            val quietDraws = draws.get()
            SystemClock.sleep(250)
            assertEquals("Explicit notifications must stop when the source is unchanged", quietSamples, samples.size)
            assertEquals("A consumer notification must not redraw the source leaf", quietDraws, draws.get())
            stages.put(JSONObject().put("name", "unchanged-retained-source-idle").put("duration_ms", 250)
                .put("samples_before", quietSamples).put("samples_after", samples.size)
                .put("source_draws_before", quietDraws).put("source_draws_after", draws.get()))
        }
        File(directory, "explicit-source-samples.json").writeText(JSONObject().put("status", "passed")
            .put("scope", "Caller-notified retained leaf draw changes; not arbitrary subtree or layer-property detection")
            .put("stages", stages).put("samples", JSONArray(samples.map(::sampleJson))).toString(2))
    }

    private fun sampleJson(s: BackdropLumaSample) = JSONObject().put("luma", s.meanLuma)
        .put("sequence", s.requestSequence).put("generation", s.generation)
        .put("started_at_ns", s.startedAtNanos).put("completed_at_ns", s.completedAtNanos)
        .put("snapshot_completed_ns", s.snapshotCompletedAtNanos).put("end_to_end_ns", s.endToEndNanos)
        .put("snapshot_main_ns", s.snapshotMainNanos).put("readback_ns", s.readbackNanos)
        .put("source_dirty_while_busy", s.sourceDirtyWhileBusy).put("accepted_count", s.acceptedCount)
        .put("discarded_count", s.discardedCount).put("failed_count", s.failedCount)
        .put("source_revision", s.sourceRevision).put("callback_thread", "main-asserted")
        .put("argb", JSONArray(s.sampledArgb))
}
