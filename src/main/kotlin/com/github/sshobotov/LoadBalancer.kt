package com.github.sshobotov

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.jvm.Throws

class LoadBalancer(
    private val selectionStrategy: Selector<Provider>,
    private val providersLimit: PositiveInt = 10.positiveOrThrow(),
    heartBeatChecker: HeartBeatChecker? = null,
    private val capacityLimiter: CapacityLimiter<Provider>? = null
) {
    private val registry = mutableSetOf<Provider>()
    private val active = mutableSetOf<Provider>()
    private val lock = ReentrantLock()

    init {
        selectionStrategy.fillWithOptions(listOf())
        heartBeatChecker?.start(
            { registry.associateWith { suspend { it.check() } } },
            { provider, isHealthy -> if (isHealthy) include(provider) else exclude(provider) }
        )
    }

    fun register(provider: Provider): Boolean =
        lock.withLock {
            when {
                registry.contains(provider) -> true
                registry.size < providersLimit.value -> {
                    val registered = registry.add(provider)
                    if (registered) {
                        active.add(provider)
                        selectionStrategy.fillWithOptions(active.toList())
                    }

                    registered
                }
                else -> false
            }
        }

    fun exclude(provider: Provider): Boolean {
        return lock.withLock {
            val removed = active.remove(provider)
            if (removed) selectionStrategy.fillWithOptions(active.toList())

            removed
        }
    }

    fun include(provider: Provider): Boolean {
        return lock.withLock {
            if (registry.contains(provider)) {
                val added = active.add(provider)
                if (added) selectionStrategy.fillWithOptions(active.toList())

                added
            } else {
                false
            }
        }
    }

    @Throws(NoActiveProviders::class)
    suspend fun get(): String {
        val selected = selectionStrategy.select() ?: throw NoActiveProviders
        return when (capacityLimiter) {
            null -> selected.get()
            else -> capacityLimiter.pipe(selected, suspend { selected.get() })
        }
    }

    companion object Error {
        object NoActiveProviders: Exception("No registered or active providers left")
    }
}