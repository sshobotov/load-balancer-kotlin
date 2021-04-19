package com.github.sshobotov

import java.time.Duration
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.*
import kotlin.test.assertEquals
import kotlinx.coroutines.*

object HeartBeatCheckerSpec: Spek({
    fun testDispatcher(): CoroutineDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    class TestScheduler : Scheduler {
        private val scheduled = mutableSetOf<CoroutineScope.() -> Unit>()
        private val triggerCtx = CoroutineScope(testDispatcher())

        fun trigger() {
            for (action in scheduled) {
                triggerCtx.action()
            }
        }

        override fun schedule(action: CoroutineScope.() -> Unit, interval: Duration): Cancellation {
            scheduled.add(action)
            return { scheduled.remove(action) }
        }
    }

    describe("HeartBeatChecker") {
        it("successful check will notify positive result") {
            val scheduler = TestScheduler()
            val checklist = mapOf(1 to suspend { true }, 2 to suspend { true })
            val notified = mutableListOf<Pair<Int, Boolean>>()
            val instance = TimedHeartBeatChecker(scheduler, Duration.ZERO)

            instance.start({ checklist }, { key, checked -> notified.add(key to checked) })

            scheduler.trigger()
            scheduler.trigger()

            val expected = listOf(1 to true, 2 to true, 1 to true, 2 to true)

            assertEquals(expected, notified, "all checkpoint should be checked")
        }

        it("failed check will notify negative result") {
            val scheduler = TestScheduler()
            val checklist = mapOf(1 to suspend { true }, 2 to suspend { throw Exception("Oops") })
            val notified = mutableListOf<Pair<Int, Boolean>>()
            val instance = TimedHeartBeatChecker(scheduler, Duration.ZERO)

            instance.start({ checklist }, { key, checked -> notified.add(key to checked) })

            scheduler.trigger()
            scheduler.trigger()

            val expected = listOf(1 to true, 2 to false, 1 to true, 2 to false)

            assertEquals(expected, notified, "all checkpoint should be checked even after errors")
        }

        it("cancellation will stop notifications") {
            val scheduler = TestScheduler()
            val checklist = mapOf(1 to suspend { true })
            val notified = mutableListOf<Pair<Int, Boolean>>()
            val instance = TimedHeartBeatChecker(scheduler, Duration.ZERO)

            val cancel = instance.start({ checklist }, { key, checked -> notified.add(key to checked) })

            scheduler.trigger()
            scheduler.trigger()

            cancel()
            scheduler.trigger()

            val expected = listOf(1 to true, 1 to true)

            assertEquals(expected, notified, "all checkpoint should be checked until cancellation")
        }

        it("only two successful subsequent checks after fail will notify success") {
            val scheduler = TestScheduler()
            val callCounter = AtomicInteger(0)
            fun checklist(answer: Boolean) = mapOf(1 to suspend { answer })

            val notified = mutableListOf<Pair<Int, Boolean>>()
            val instance = TimedHeartBeatChecker(scheduler, Duration.ZERO)

            instance.start({
                // a.k.a sequence f, t, f, t, t, ...
                val answer = when (callCounter.incrementAndGet()) {
                    1, 3 -> false
                    else -> true
                }
                checklist(answer)
            }, { key, checked -> notified.add(key to checked) })

            scheduler.trigger()
            scheduler.trigger()
            scheduler.trigger()
            scheduler.trigger()
            scheduler.trigger()

            val expected = listOf(1 to false, 1 to false, 1 to true)

            assertEquals(expected, notified)
        }
    }

    describe("ClockTimeScheduler") {
        it("clock time scheduler will trigger scheduled actions") {
            val timer = Timer("test")
            val scheduler = ClockTimeScheduler(timer, testDispatcher())
            val callCounter = AtomicInteger(0)

            scheduler.schedule({
                val cnt = callCounter.incrementAndGet()
                if (cnt == 2) throw Exception("Oops")
            }, Duration.ofMillis(50))

            Thread.sleep(170)
            timer.cancel()

            Thread.sleep(60)
            assertEquals(3, callCounter.get(), "number of scheduled action calls should match")
        }
    }
})