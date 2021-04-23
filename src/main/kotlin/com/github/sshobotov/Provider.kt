package com.github.sshobotov

import kotlin.random.Random

abstract class Provider {
    abstract val id: String

    abstract suspend fun get(): String

    abstract suspend fun check(): Boolean

    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean = id == other
}

class DummyProvider(override val id: String, private val checkLogic: () -> Boolean = { true }) : Provider() {
    override suspend fun get(): String = id

    override suspend fun check(): Boolean = checkLogic()
}