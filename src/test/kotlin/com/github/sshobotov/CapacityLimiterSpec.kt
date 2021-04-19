package com.github.sshobotov

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.assertThrows
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals

typealias Blocking = suspend () -> Unit

object CapacityLimiterSpec : Spek({
    /**
     * Runs all suspended actions in parallel, collects results wrapped in Result and ensures
     * actions will await each other at the point of Blocking call (Semaphore acquisition)
     */
    suspend fun <R> parResults(vararg actions: suspend (Blocking) -> R): List<Result<R>> {
        return supervisorScope {
            val asyncStarted = Mutex(locked = true)
            val waitForOther = Semaphore(actions.size, actions.size)

            val results = actions.map { fn ->
                async {
                    if (asyncStarted.isLocked) asyncStarted.unlock()
                    runCatching { fn { waitForOther.acquire() } }
                }
            }

            asyncStarted.lock()
            for (i in 1..actions.size) {
                waitForOther.release()
            }

            results.awaitAll()
        }
    }

    describe("CapacityLimiter") {
        it("should return the same result as original request if limit wasn't reached") {
            val subject = PerKeyCapacityLimiter<Int>(10.positiveOrThrow())
            val results = runBlocking {
                subject.pipe(1, suspend { 42 })
            }
            assertEquals(42, results)
        }

        it("should fail with expected error if limit exceeded for particular key") {
            val subject = PerKeyCapacityLimiter<Int>(1.positiveOrThrow())
            val results = runBlocking {
                parResults(
                    { waitAllStarted -> subject.pipe(1, suspend { waitAllStarted(); 1 }) },
                    { waitAllStarted -> subject.pipe(1, suspend { waitAllStarted(); 2 }) },
                    { waitAllStarted -> subject.pipe(2, suspend { waitAllStarted(); 42 }) }
                )
            }

            assertEquals(1, results.firstOrNull()?.getOrThrow())
            assertThrows<CapacityLimiter.Error.LimitExceeded> { results.getOrNull(1)?.getOrThrow() }
            assertEquals(42, results.lastOrNull()?.getOrThrow())
        }

        it("should correctly handle limit lose") {
            val subject = PerKeyCapacityLimiter<Int>(2.positiveOrThrow())
            val results = runBlocking {
                val waitForSecondWave = Mutex(locked = true)
                val waitForConcurrent = Mutex(locked = true)

                parResults(
                    { waitAllStarted -> subject.pipe(1, suspend { waitAllStarted(); 1 }) },
                    { waitAllStarted -> subject.pipe(1, suspend { waitAllStarted(); waitForSecondWave.lock(); 2 }) },
                    { waitAllStarted -> subject.pipe(1, suspend { waitAllStarted(); 3 }) },
                    // Second wave
                    { waitAllStarted ->
                        try {
                            waitAllStarted()
                            subject.pipe(1, suspend { waitForConcurrent.lock(); 4 })
                        } finally {
                            waitForSecondWave.unlock()
                        }
                    },
                    { waitAllStarted ->
                        try {
                            waitAllStarted()
                            subject.pipe(1, suspend { 5 })
                        } finally {
                            waitForConcurrent.unlock()
                        }
                    }
                )
            }

            assertEquals(1, results.firstOrNull()?.getOrThrow())
            assertEquals(2, results.getOrNull(1)?.getOrThrow())
            assertThrows<CapacityLimiter.Error.LimitExceeded> { results.getOrNull(2)?.getOrThrow() }
            assertEquals(4, results.getOrNull(3)?.getOrThrow())
            assertThrows<CapacityLimiter.Error.LimitExceeded> { results.getOrNull(4)?.getOrThrow() }
        }
    }
})