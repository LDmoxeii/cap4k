package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

fun TestEntityWithBehaviorHooks.onCreate() {
    onCreateCallCount++
}

fun TestEntityWithBehaviorHooks.onDeleted() {
    onDeletedCallCount++
}
