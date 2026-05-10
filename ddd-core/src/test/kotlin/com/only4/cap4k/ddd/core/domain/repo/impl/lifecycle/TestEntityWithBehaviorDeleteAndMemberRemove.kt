package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

class TestEntityWithBehaviorDeleteAndMemberRemove {
    var behaviorDeleteCallCount = 0
    var memberRemoveCallCount = 0

    fun onRemove() {
        memberRemoveCallCount++
    }
}
