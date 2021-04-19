package com.github.sshobotov

import java.time.Duration
import java.util.*
import java.lang.Exception
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.timerTask
import kotlinx.coroutines.*

typealias Check = suspend () -> Boolean
typealias Cancellation = () -> Unit

interface HeartBeatChecker {
    fun <T> start(checklist: () -> Map<T, Check>, notify: (T, Boolean) -> Unit): Cancellation
}

class TimedHeartBeatChecker(
    private val scheduler: Scheduler,
    private val checkInterval: Duration
) : HeartBeatChecker {
    override fun <T> start(checklist: () -> Map<T, Check>, notify: (T, Boolean) -> Unit): Cancellation {
        val comebackEntries = ConcurrentHashMap.newKeySet<T>()

        return scheduler.schedule(
            {
                val itemsToCheck = checklist()
                val countDownLatch = CountDownLatch(itemsToCheck.size)

                for ((key, check) in itemsToCheck) {
                    launch {
                        countDownLatch.countDown()
                        val checked =
                            try {
                                check()
                            } catch (_: Throwable) {
                                false
                            }
                        if (checked) {
                            val wasPresent = comebackEntries.remove(key)
                            if (!wasPresent) notify(key, checked)
                        } else {
                            comebackEntries.add(key)
                            notify(key, checked)
                        }
                    }
                }
                countDownLatch.await()
            },
            checkInterval
        )
    }

    companion object Default {
        fun invoke(timer: Timer, interval: Duration): TimedHeartBeatChecker =
            TimedHeartBeatChecker(ClockTimeScheduler(timer), interval)
    }
}

interface Scheduler {
    fun schedule(action: CoroutineScope.() -> Unit, interval: Duration): Cancellation
}

class ClockTimeScheduler(
    private val timer: Timer,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : Scheduler {
    private val runCtx = GlobalScope.plus(dispatcher)

    override fun schedule(action: CoroutineScope.() -> Unit, interval: Duration): Cancellation {
        val scheduledTask = timerTask {
            try {
                runCtx.action()
            } catch (_: Exception) {}
        }
        val intervalMillis = interval.toMillis()

        timer.scheduleAtFixedRate(scheduledTask, intervalMillis, intervalMillis)

        return { scheduledTask.cancel() }
    }
}
