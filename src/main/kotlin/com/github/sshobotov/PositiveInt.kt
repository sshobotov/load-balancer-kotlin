package com.github.sshobotov

import kotlin.jvm.JvmInline

@JvmInline
value class PositiveInt private constructor(val value: Int) {
    companion object {
        fun from(value: Int): PositiveInt? =
            if (value > 0) PositiveInt(value) else null
    }
}

fun Int.positiveOrNull(): PositiveInt? = PositiveInt.from(this)

fun Int.positiveOrThrow(): PositiveInt =
    when (val n = this.positiveOrNull()) {
        null -> throw IllegalArgumentException("$this is not positive Int")
        else -> n
    }