@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle

fun TestEntityWithMemberAndBehaviorHooks.onCreate() {
    behaviorCreateCallCount++
}
