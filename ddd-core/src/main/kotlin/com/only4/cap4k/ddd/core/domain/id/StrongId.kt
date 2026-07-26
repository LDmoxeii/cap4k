package com.only4.cap4k.ddd.core.domain.id

interface StrongId<out V : Any> {
    val value: V
}
