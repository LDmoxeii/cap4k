package com.only4.cap4k.ddd.application

internal class ObjectIdentityKey(private val entity: Any) {
    override fun equals(other: Any?): Boolean =
        other is ObjectIdentityKey && entity === other.entity

    override fun hashCode(): Int = System.identityHashCode(entity)
}
