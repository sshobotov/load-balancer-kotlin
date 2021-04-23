package com.github.sshobotov

import kotlinx.coroutines.*
import org.junit.jupiter.api.assertThrows
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LoadBalancerSpec : Spek({
    class TestSelector : Selector<Provider> {
        @Volatile
        private var item: Provider? = null

        override fun fillWithOptions(options: List<Provider>) {
            item = options.firstOrNull()
        }

        override fun select(): Provider? = item
    }

    fun randomId() = UUID.randomUUID().toString()

    describe("LoadBalancer") {
        it("returns error if trying to get wth no registered providers") {
            assertThrows<LoadBalancer.Error.NoActiveProviders> {
                runBlocking {
                    LoadBalancer(TestSelector()).get()
                }
            }
        }

        it("allows to register not more than allowed") {
            val target = LoadBalancer(TestSelector(), providersLimit = 5.positiveOrThrow())
            val result = runBlocking {
                withContext(Dispatchers.IO) {
                    (1..10).map {
                        async { target.register(DummyProvider(randomId())) }
                    }.awaitAll()
                }
            }
            assertEquals(5, result.count { it })
            assertNotNull(runBlocking { target.get() })
        }

        it("delegates get call to underlying provider") {
            val target = LoadBalancer(TestSelector())
            val provider = DummyProvider(randomId())

            val result = runBlocking {
                target.register(provider)
                target.get()
            }
            assertEquals(provider.id, result)
        }

        it("allows to deactivate particular provider") {
            val target = LoadBalancer(TestSelector())
            val provider = DummyProvider(randomId())

            target.register(provider)
            assertEquals(true, target.exclude(provider))

            assertThrows<LoadBalancer.Error.NoActiveProviders> {
                runBlocking {
                    LoadBalancer(TestSelector()).get()
                }
            }
        }

        it("ignores deactivation of not registered provider") {
            val target = LoadBalancer(TestSelector())
            val provider1 = DummyProvider(randomId())
            val provider2 = DummyProvider(randomId())

            target.register(provider1)
            assertEquals(false, target.exclude(provider2))

            val result = runBlocking {
                target.get()
            }
            assertEquals(provider1.id, result)
        }

        it("allows to activate deactivated provider") {
            val target = LoadBalancer(TestSelector())
            val provider = DummyProvider(randomId())

            target.register(provider)
            target.exclude(provider)
            assertEquals(true, target.include(provider))

            val result = runBlocking {
                target.get()
            }
            assertEquals(provider.id, result)
        }

        it("ignores activation of not registered or already active provider") {
            val target = LoadBalancer(TestSelector())
            val provider1 = DummyProvider(randomId())
            val provider2 = DummyProvider(randomId())

            target.register(provider1)
            assertEquals(false, target.include(provider1))
            assertEquals(false, target.include(provider2))

            val result = runBlocking {
                target.get()
            }
            assertEquals(provider1.id, result)
        }
    }
})