package com.github.sshobotov

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.random.Random

interface Selector<T> {
    fun fillWithOptions(options: List<T>)

    fun select(): T?
}

class RandomSelector<T>(private val rnd: Random = Random.Default) : Selector<T> {
    @Volatile
    private var options: ArrayList<T> = arrayListOf()

    override fun fillWithOptions(options: List<T>) {
        this.options = ArrayList(options)
    }

    override fun select(): T? =
        if (options.isEmpty()) null else options[rnd.nextInt(options.size)]
}

class RoundRobinSelector<T : Comparable<T>> : Selector<T> {
    private var options = arrayListOf<T>()
    private var previouslySelected: Pair<Int, T?> = Pair(-1, null)

    private val lock = ReentrantLock()

    override fun fillWithOptions(options: List<T>) {
        lock.withLock {
            this.options = ArrayList(options.sorted())
            if (previouslySelected.second != null) {
                val newIndex = this.options.indexOf(previouslySelected.second)
                if (newIndex > -1) {
                    previouslySelected = previouslySelected.copy(first = newIndex)
                }
            }
        }
    }

    override fun select(): T? =
        lock.withLock {
            if (options.isEmpty()) null
            else {
                val selectedIdx = (previouslySelected.first + 1) % options.size
                val selectedVal = options[selectedIdx]

                previouslySelected = Pair(selectedIdx, selectedVal)

                selectedVal
            }
        }
}