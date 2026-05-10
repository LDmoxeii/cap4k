package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

open class TestEntityWithBehaviorHooks {
    var onCreateCallCount = 0
    var onUpdateCallCount = 0
    var onDeleteCallCount = 0
}
