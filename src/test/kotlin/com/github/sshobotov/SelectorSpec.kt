package com.github.sshobotov

import kotlinx.coroutines.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

object SelectorSpec: Spek({
    describe("RandomSelector") {
        it("returns no value if no options provided") {
            assertNull(RandomSelector<Int>().select())
        }

        it("picks randomly one of the options provided") {
            val valueRng = 1..100

            fun provide(): RandomSelector<Int> {
                val instance = RandomSelector<Int>()

                instance.fillWithOptions(valueRng.toList())

                return instance
            }
            val instance = provide()

            val r1 = instance.select()!!
            val r2 = instance.select()!!
            val r3 = instance.select()!!

            assertTrue(r1 in valueRng, "value in expected range")
            assertTrue(r2 in valueRng, "value in expected range")
            assertTrue(r3 in valueRng, "value in expected range")
            assertTrue(r1 != r2 || r2 != r3, "got different values")

            val r4 = provide().select()!!
            val r5 = provide().select()!!

            assertTrue(r1 != r4 || r4 != r5, "got different first values")
        }
    }

    describe("RoundRobinSelector") {
        it("returns no value if no options provided") {
            assertNull(RoundRobinSelector<Int>().select())
        }

        it("picks sequentially one of the options provided") {
            val instance = RoundRobinSelector<Int>()
            val sequence = listOf(1, 2, 3, 4)

            instance.fillWithOptions(sequence)
            assertEquals(sequence + sequence, (1..sequence.size * 2).map { instance.select() })
        }

        it("picks sequentially next one after removed option") {
            val instance = RoundRobinSelector<Int>()

            instance.fillWithOptions(listOf(1, 2, 3, 4))
            instance.select()
            instance.select()

            instance.fillWithOptions(listOf(1, 2, 4))
            assertEquals(4, instance.select())
        }

        it("picks sequentially right option even if sequence altered with options before current one") {
            val instance = RoundRobinSelector<Int>()

            instance.fillWithOptions(listOf(1, 3, 4, 5))
            instance.select()
            instance.select()

            instance.fillWithOptions(listOf(1, 2, 3, 4, 5))
            assertEquals(4, instance.select())
        }

        it("behaves in expected manner in the presence of concurrency") {
            val instance = RoundRobinSelector<Int>()
            val sequence = listOf(1, 2, 3, 4)

            instance.fillWithOptions(sequence)

            val results = runBlocking {
                withContext(Dispatchers.IO) {
                    (1..100).map {
                        async { instance.select()!! }
                    }.awaitAll()
                }
            }
            assertEquals(sequence.flatMap { x -> (1..25).map { x } }, results.sorted())
        }
    }
})