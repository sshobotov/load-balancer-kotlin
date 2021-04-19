package com.github.sshobotov

import kotlin.jvm.Throws

class LoadBalancer(
    private val selectionStrategy: Selector<Provider>,
    private val providersLimit: PositiveInt = 10.positiveOrThrow(),
    heartBeatChecker: HeartBeatChecker? = null,
    private val capacityLimiter: CapacityLimiter<Provider>? = null
) {
    private val registry: MutableSet<Provider> = mutableSetOf()
    private val active: MutableSet<Provider> = mutableSetOf()

    init {
        selectionStrategy.fillWithOptions(listOf())
        heartBeatChecker?.start(
            { registry.associateWith { suspend { it.check() } } },
            { provider, isHealthy -> if (isHealthy) include(provider) else exclude(provider) }
        )
    }

    fun register(provider: Provider): Boolean =
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

    fun exclude(provider: Provider): Boolean {
        val removed = active.remove(provider)
        if (removed) selectionStrategy.fillWithOptions(active.toList())

        return removed
    }

    fun include(provider: Provider): Boolean {
        if (registry.contains(provider)) {
            val added = active.add(provider)
            if (added) selectionStrategy.fillWithOptions(active.toList())

            return added
        }
        return false
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