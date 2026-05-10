package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

class TestEntityWithMemberAndBehaviorHooks {
    var memberCreateCallCount = 0
    var behaviorCreateCallCount = 0

    fun onCreate() {
        memberCreateCallCount++
    }
}
