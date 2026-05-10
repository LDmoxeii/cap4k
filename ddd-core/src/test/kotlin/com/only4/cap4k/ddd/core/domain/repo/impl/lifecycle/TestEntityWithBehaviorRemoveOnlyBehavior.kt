package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

fun TestEntityWithBehaviorRemoveOnly.onRemove() {
    behaviorRemoveCallCount++
}
