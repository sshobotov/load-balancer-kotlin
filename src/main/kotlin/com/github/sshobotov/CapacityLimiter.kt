package com.github.sshobotov

import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.Throws

interface CapacityLimiter<K> {
    /**
     * Will check capacity and try to execute request otherwise will throw Error.LimitExceeded
     */
    @Throws(LimitExceeded::class)
    suspend fun <T> pipe(key: K, request: suspend () -> T): T

    companion object Error {
        class LimitExceeded(message: String) : Exception(message)
    }
}

class PerKeyCapacityLimiter<K>(perKeyLimit: PositiveInt) : CapacityLimiter<K> {
    private val requests = ConcurrentHashMap<K, Int>()

    private val limit = perKeyLimit.value

    override suspend fun <T> pipe(key: K, request: suspend () -> T): T {
        val cnt = requests.compute(key) { _, value ->
            when (value) {
                null -> 1
                in 1..limit -> value + 1 // Switch ON requests discarding if limit is reached
                else -> value
            }
        }!!
        return if (cnt > limit)
            throw CapacityLimiter.Error.LimitExceeded("Couldn't exceed $limit per $key")
        else
            try {
                request()
            } finally {
                requests.compute(key) { _, value ->
                    when (value) {
                        null -> null
                        in 2..limit -> value - 1
                        limit + 1 -> limit - 1 // Switch OFF requests discarding
                        else -> null
                    }
                }
            }
    }
}