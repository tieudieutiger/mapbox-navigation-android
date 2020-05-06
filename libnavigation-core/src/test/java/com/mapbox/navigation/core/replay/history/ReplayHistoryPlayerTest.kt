package com.mapbox.navigation.core.replay.history

import android.os.SystemClock
import com.mapbox.base.common.logger.Logger
import com.mapbox.navigation.testing.MainCoroutineRule
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class ReplayHistoryPlayerTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private val mockLambda: (List<ReplayEventBase>) -> Unit = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxUnitFun = true)

    private var deviceElapsedTimeNanos = TimeUnit.HOURS.toNanos(11)

    @Test
    fun `should play start transit and location in order`() = coroutineRule.runBlockingTest {
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(listOf(
                ReplayEventGetStatus(1580777612.853),
                ReplayEventUpdateLocation(1580777612.89,
                    ReplayEventLocation(
                        lat = 49.2492411,
                        lon = 8.8512315,
                        provider = "fused",
                        time = 1580777612.892,
                        altitude = 212.4732666015625,
                        accuracyHorizontal = 4.288000106811523,
                        bearing = 243.31265258789063,
                        speed = 0.5585000514984131)
                )
            ))
        replayHistoryPlayer.registerObserver(mockLambda)

        val job = replayHistoryPlayer.play()
        advanceTimeMillis(5000)
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        val replayUpdates = mutableListOf<List<ReplayEventBase>>()
        coVerify { mockLambda(capture(replayUpdates)) }
        val events = replayUpdates.flatten()
        assertEquals(2, events.size)
        assertEquals(1580777612.853, events[0].eventTimestamp, 0.001)
        assertEquals(1580777612.89, events[1].eventTimestamp, 0.001)
    }

    @Test
    fun `should play 2 of 3 locations that include time window`() = coroutineRule.runBlockingTest {
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(listOf(
                ReplayEventUpdateLocation(1580777820.952,
                    ReplayEventLocation(
                        lat = 49.2450478,
                        lon = 8.8682922,
                        time = 1580777820.952,
                        speed = 30.239412307739259,
                        bearing = 108.00135040283203,
                        altitude = 222.47210693359376,
                        accuracyHorizontal = 3.9000000953674318,
                        provider = "fused")),
                ReplayEventUpdateLocation(1580777822.959,
                    ReplayEventLocation(
                        lat = 49.2448858,
                        lon = 8.8690847,
                        time = 1580777822.958,
                        speed = 29.931121826171876,
                        bearing = 106.001953125,
                        altitude = 221.9241943359375,
                        accuracyHorizontal = 3.9000000953674318,
                        provider = "fused")),
                ReplayEventUpdateLocation(1580777824.953,
                    ReplayEventLocation(
                        lat = 49.2447354,
                        lon = 8.8698759,
                        time = 1580777824.89,
                        speed = 29.96711540222168,
                        bearing = 106.00138092041016,
                        altitude = 221.253662109375,
                        accuracyHorizontal = 3.9000000953674318,
                        provider = "fused"))
            ))
        replayHistoryPlayer.registerObserver(mockLambda)

        val job = replayHistoryPlayer.play()
        advanceTimeMillis(3000)
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        // Note that it only played 2 of the 3 locations
        val replayUpdates = mutableListOf<List<ReplayEventBase>>()
        coVerify { mockLambda(capture(replayUpdates)) }
        val events = replayUpdates.flatten()
        assertEquals(2, events.size)
        assertEquals(1580777820.952, events[0].eventTimestamp, 0.001)
        assertEquals(1580777822.959, events[1].eventTimestamp, 0.001)
    }

    @Test
    fun `should resume playing after completing events`() = coroutineRule.runBlockingTest {
        val testEvents = List(12) { ReplayEventGetStatus(it.toDouble()) }
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(testEvents)
        val timeCapture = mutableListOf<Pair<ReplayEventBase, Long>>()
        replayHistoryPlayer.registerObserver { replayEvents ->
            replayEvents.forEach { timeCapture.add(Pair(it, currentTime)) }
        }

        val job = replayHistoryPlayer.play()
        advanceTimeMillis(20000)
        val extraEvents = List(7) { ReplayEventGetStatus(it.toDouble()) }
        replayHistoryPlayer.pushEvents(extraEvents)
        advanceTimeMillis(20000)
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        // 12 events at the beginning
        // 7 events later
        assertEquals(12 + 7, timeCapture.size)
        timeCapture.slice(0..11).forEach { assertTrue(it.second < 20000) }
        timeCapture.slice(12..18).forEach { assertTrue(it.second > 20000) }
    }

    @Test
    fun `should not delay player when consumer takes time`() = coroutineRule.runBlockingTest {
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(listOf(
                ReplayEventGetStatus(1000.000),
                ReplayEventGetStatus(1001.000),
                ReplayEventGetStatus(1003.000)
            ))
        val timeCapture = mutableListOf<Long>()
        replayHistoryPlayer.registerObserver { replayEvents ->
            if (replayEvents.isNotEmpty()) {
                timeCapture.add(currentTime)
                advanceTimeMillis(75)
            }
        }

        val job = replayHistoryPlayer.play()
        for (i in 0..3000) {
            advanceTimeMillis(1)
        }
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        assertEquals(3, timeCapture.size)
        assertEquals(0L, timeCapture[0])
        assertEquals(1000L, timeCapture[1])
        assertEquals(3000L, timeCapture[2])
    }

    @Test
    fun `should allow custom events`() = coroutineRule.runBlockingTest {
        data class CustomReplayEvent(
            override val eventTimestamp: Double,
            val customValue: String
        ) : ReplayEventBase
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(listOf(
                CustomReplayEvent(1580777612.853, "custom value"),
                ReplayEventUpdateLocation(1580777613.89,
                    ReplayEventLocation(
                        lat = 49.2492411,
                        lon = 8.8512315,
                        time = null,
                        provider = null,
                        altitude = null,
                        accuracyHorizontal = null,
                        bearing = null,
                        speed = null))
            ))
        replayHistoryPlayer.registerObserver(mockLambda)

        val job = replayHistoryPlayer.play()
        advanceTimeMillis(5000)
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        val replayUpdates = mutableListOf<List<ReplayEventBase>>()
        coVerify { mockLambda(capture(replayUpdates)) }
        val events = replayUpdates.flatten()
        assertEquals(events.size, 2)
        assertTrue(events[0] is CustomReplayEvent)
        assertEquals("custom value", (events[0] as CustomReplayEvent).customValue)
        assertEquals(1580777613.89, events[1].eventTimestamp, 0.001)
    }

    @Test
    fun `should not crash if history data is empty`() {
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)

        replayHistoryPlayer.play()
        replayHistoryPlayer.finish()
    }

    @Test
    fun `playFirstLocation should ignore events before the first location`() {
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(listOf(
                ReplayEventGetStatus(1580777612.853),
                ReplayEventUpdateLocation(1580777612.89,
                    ReplayEventLocation(
                        lat = 49.2492411,
                        lon = 8.8512315,
                        provider = "fused",
                        time = 1580777612.892,
                        altitude = 212.4732666015625,
                        accuracyHorizontal = 4.288000106811523,
                        bearing = 243.31265258789063,
                        speed = 0.5585000514984131))
            ))
        replayHistoryPlayer.registerObserver(mockLambda)

        replayHistoryPlayer.playFirstLocation()

        val replayUpdates = mutableListOf<List<ReplayEventBase>>()
        verify { mockLambda(capture(replayUpdates)) }
        val events = replayUpdates.flatten()
        assertEquals(1, events.size)
        assertEquals(1580777612.89, events[0].eventTimestamp, 0.001)
    }

    @Test
    fun `playFirstLocation should handle history events without locations`() {
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(listOf(
                ReplayEventGetStatus(1580777612.853),
                ReplayEventGetStatus(1580777613.452),
                ReplayEventGetStatus(1580777614.085)
            ))
        replayHistoryPlayer.registerObserver(mockLambda)

        replayHistoryPlayer.playFirstLocation()

        verify { mockLambda(any()) wasNot Called }
    }

    @Test
    fun `should seekTo an event`() = coroutineRule.runBlockingTest {
        val seekToEvent = ReplayEventGetStatus(2.452)
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(listOf(
                ReplayEventGetStatus(1.853),
                seekToEvent,
                ReplayEventGetStatus(3.085)
            ))
        replayHistoryPlayer.registerObserver(mockLambda)
        replayHistoryPlayer.seekTo(seekToEvent)

        val job = replayHistoryPlayer.play()
        advanceTimeMillis(5000)
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        val replayUpdates = mutableListOf<List<ReplayEventBase>>()
        coVerify { mockLambda(capture(replayUpdates)) }
        val events = replayUpdates.flatten()
        assertEquals(events.size, 2)
        assertEquals(2.452, events[0].eventTimestamp, 0.001)
        assertEquals(3.085, events[1].eventTimestamp, 0.001)
    }

    @Test(expected = Exception::class)
    fun `should crash when seekTo event is missing`() = coroutineRule.runBlockingTest {
        val seekToEvent = ReplayEventGetStatus(2.452)
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(listOf(
                ReplayEventGetStatus(1.853),
                ReplayEventGetStatus(3.085)
            ))
        replayHistoryPlayer.registerObserver(mockLambda)
        replayHistoryPlayer.seekTo(seekToEvent)
    }

    @Test
    fun `should seekTo an event time`() = coroutineRule.runBlockingTest {
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(listOf(
                ReplayEventGetStatus(0.0),
                ReplayEventGetStatus(2.0),
                ReplayEventGetStatus(4.0)
            ))
        replayHistoryPlayer.registerObserver(mockLambda)
        replayHistoryPlayer.seekTo(1.0)

        val job = replayHistoryPlayer.play()
        advanceTimeMillis(5000)
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        val replayUpdates = mutableListOf<List<ReplayEventBase>>()
        coVerify { mockLambda(capture(replayUpdates)) }
        val events = replayUpdates.flatten()
        assertEquals(events.size, 2)
        assertEquals(2.0, events[0].eventTimestamp, 0.001)
        assertEquals(4.0, events[1].eventTimestamp, 0.001)
    }

    @Test
    fun `should seekTo a time relative to total time`() = coroutineRule.runBlockingTest {
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(listOf(
                ReplayEventGetStatus(1580777611.853),
                ReplayEventGetStatus(1580777613.452),
                ReplayEventGetStatus(1580777614.085)
            ))
        replayHistoryPlayer.registerObserver(mockLambda)
        replayHistoryPlayer.seekTo(1.0)

        val job = replayHistoryPlayer.play()
        advanceTimeMillis(5000)
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        val replayUpdates = mutableListOf<List<ReplayEventBase>>()
        coVerify { mockLambda(capture(replayUpdates)) }
        val events = replayUpdates.flatten()
        assertEquals(2, events.size)
        assertEquals(1580777613.452, events[0].eventTimestamp, 0.001)
        assertEquals(1580777614.085, events[1].eventTimestamp, 0.001)
    }

    @Test
    fun `playbackSpeed should play one event per second at 1_0 playbackSpeed`() = coroutineRule.runBlockingTest {
        val testEvents = List(20) { ReplayEventGetStatus(it.toDouble()) }
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(testEvents)

        replayHistoryPlayer.playbackSpeed(1.0)
        val job = replayHistoryPlayer.play()
        val timeCapture = mutableListOf<Pair<ReplayEventBase, Long>>()
        replayHistoryPlayer.registerObserver { replayEvents ->
            replayEvents.forEach { timeCapture.add(Pair(it, currentTime)) }
        }
        advanceTimeMillis(3000)
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        assertEquals(3, timeCapture.size)
    }

    @Test
    fun `playbackSpeed should play four events per second at 4_0 playbackSpeed`() = coroutineRule.runBlockingTest {
        val testEvents = List(20) { ReplayEventGetStatus(it.toDouble()) }
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(testEvents)

        replayHistoryPlayer.playbackSpeed(4.0)
        val job = replayHistoryPlayer.play()
        val timeCapture = mutableListOf<Pair<ReplayEventBase, Long>>()
        replayHistoryPlayer.registerObserver { replayEvents ->
            replayEvents.forEach { timeCapture.add(Pair(it, currentTime)) }
        }
        advanceTimeMillis(4000)
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        assertEquals(16, timeCapture.size)
    }

    @Test
    fun `playbackSpeed should play one event every four seconds at 0_25 playbackSpeed`() = coroutineRule.runBlockingTest {
        val testEvents = List(20) { ReplayEventGetStatus(it.toDouble()) }
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(testEvents)

        replayHistoryPlayer.playbackSpeed(0.25)
        val job = replayHistoryPlayer.play()
        val timeCapture = mutableListOf<Pair<ReplayEventBase, Long>>()
        replayHistoryPlayer.registerObserver { replayEvents ->
            replayEvents.forEach { timeCapture.add(Pair(it, currentTime)) }
        }
        advanceTimeMillis(40000)
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        assertEquals(10, timeCapture.size)
    }

    @Test
    fun `playbackSpeed should update play speed while playing`() = coroutineRule.runBlockingTest {
        val testEvents = List(20) { ReplayEventGetStatus(it.toDouble()) }
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(testEvents)

        replayHistoryPlayer.playbackSpeed(1.0)
        val job = replayHistoryPlayer.play()
        val timeCapture = mutableListOf<Pair<ReplayEventBase, Long>>()
        replayHistoryPlayer.registerObserver { replayEvents ->
            replayEvents.forEach { timeCapture.add(Pair(it, currentTime)) }
        }
        advanceTimeMillis(2000)
        replayHistoryPlayer.playbackSpeed(3.0)
        advanceTimeMillis(1999) // advance a fraction to remove the equal events
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        // 2 events over 2 seconds at 1x speed.
        // 6 events over 2 seconds at 3x speed.
        assertEquals(2 + 6, timeCapture.size)
        timeCapture.slice(0..1).forEach { assertTrue(it.second < 2000) }
        timeCapture.slice(2..7).forEach { assertTrue(it.second > 2000) }
    }

    @Test
    fun `playbackSpeed should not crash when events are completed`() = coroutineRule.runBlockingTest {
        val testEvents = List(12) { ReplayEventGetStatus(it.toDouble()) }
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(testEvents)
        val timeCapture = mutableListOf<Pair<ReplayEventBase, Long>>()
        replayHistoryPlayer.registerObserver { replayEvents ->
            replayEvents.forEach { timeCapture.add(Pair(it, currentTime)) }
        }

        val job = replayHistoryPlayer.play()
        advanceTimeMillis(20000)
        replayHistoryPlayer.playbackSpeed(3.0)
        advanceTimeMillis(20000)
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        // 12 events at the beginning
        assertEquals(12, timeCapture.size)
    }

    @Test
    fun `should register multiple observers`() = coroutineRule.runBlockingTest {
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(listOf(
                ReplayEventGetStatus(1.0),
                ReplayEventGetStatus(2.0),
                ReplayEventGetStatus(3.0)
            ))
        val firstObserver: (List<ReplayEventBase>) -> Unit = mockk(relaxed = true)
        val secondObserver: (List<ReplayEventBase>) -> Unit = mockk(relaxed = true)
        replayHistoryPlayer.registerObserver(firstObserver)
        replayHistoryPlayer.registerObserver(secondObserver)

        val job = replayHistoryPlayer.play()
        advanceTimeMillis(5000)
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        val firstObserverEvents = mutableListOf<List<ReplayEventBase>>()
        coVerify { firstObserver(capture(firstObserverEvents)) }
        val secondObserverEvents = mutableListOf<List<ReplayEventBase>>()
        coVerify { secondObserver(capture(secondObserverEvents)) }
        val firstEvents = firstObserverEvents.flatten()
        val secondEvents = secondObserverEvents.flatten()
        assertEquals(3, firstEvents.size)
        assertEquals(firstEvents, secondEvents)
    }

    @Test
    fun `should unregister single observers`() = coroutineRule.runBlockingTest {
        val replayHistoryPlayer = ReplayHistoryPlayer(logger)
            .pushEvents(listOf(
                ReplayEventGetStatus(1.0),
                ReplayEventGetStatus(2.0),
                ReplayEventGetStatus(3.0)
            ))
        val firstObserver: (List<ReplayEventBase>) -> Unit = mockk(relaxed = true)
        val secondObserver: (List<ReplayEventBase>) -> Unit = mockk(relaxed = true)
        replayHistoryPlayer.registerObserver(firstObserver)
        replayHistoryPlayer.registerObserver(secondObserver)

        val job = replayHistoryPlayer.play()
        advanceTimeMillis(1000)
        replayHistoryPlayer.unregisterObserver(firstObserver)
        advanceTimeMillis(2000)
        replayHistoryPlayer.finish()
        job.cancelAndJoin()

        val firstObserverEvents = mutableListOf<List<ReplayEventBase>>()
        coVerify { firstObserver(capture(firstObserverEvents)) }
        val secondObserverEvents = mutableListOf<List<ReplayEventBase>>()
        coVerify { secondObserver(capture(secondObserverEvents)) }
        assertEquals(2, firstObserverEvents.flatten().size)
        assertEquals(3, secondObserverEvents.flatten().size)
    }

    /**
     * Helpers for moving the simulation clock
     */

    @Before
    fun setup() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtimeNanos() } returns deviceElapsedTimeNanos
    }

    @After
    fun teardown() {
        unmockkObject(SystemClock.elapsedRealtimeNanos())
    }

    private fun advanceTimeMillis(advanceMillis: Long) {
        deviceElapsedTimeNanos += TimeUnit.MILLISECONDS.toNanos(advanceMillis)
        every { SystemClock.elapsedRealtimeNanos() } returns deviceElapsedTimeNanos
        coroutineRule.testDispatcher.advanceTimeBy(advanceMillis)
    }
}
