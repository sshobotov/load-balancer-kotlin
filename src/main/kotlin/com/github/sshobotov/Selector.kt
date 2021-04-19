package com.github.sshobotov

import kotlin.random.Random

interface Selector<T> {
    fun fillWithOptions(options: List<T>)

    fun select(): T?
}

class RandomSelector<T>(private val rnd: Random = Random.Default) : Selector<T> {
    private var options: ArrayList<T> = arrayListOf()

    override fun fillWithOptions(options: List<T>) {
        this.options = ArrayList(options)
    }

    override fun select(): T? =
        if (options.isEmpty()) null else options[rnd.nextInt(options.size)]
}

class RoundRobinSelector<T: Comparable<T>> : Selector<T> {
    private var options: ArrayList<T> = arrayListOf()

    private var previouslySelected: Int = -1

    override fun fillWithOptions(options: List<T>) {
        this.options = ArrayList(options.sorted())
    }

    override fun select(): T? =
        if (options.isEmpty()) null
        else {
            previouslySelected = (previouslySelected + 1) % options.size
            options[previouslySelected]
        }
}