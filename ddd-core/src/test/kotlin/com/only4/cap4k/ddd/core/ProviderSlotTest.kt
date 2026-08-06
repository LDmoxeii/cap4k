package com.only4.cap4k.ddd.core

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProviderSlotTest {
    @Test
    fun `duplicate registration is rejected without replacing the active provider`() {
        val slot = ProviderSlot<TestProvider>("test-provider")
        val active = FirstProvider()
        val conflicting = SecondProvider()
        slot.configure(active)

        val failure = assertThrows<IllegalStateException> {
            slot.configure(conflicting)
        }

        assertTrue(failure.message.orEmpty().contains("cap4k provider 'test-provider' is already configured"))
        assertTrue(failure.message.orEmpty().contains(FirstProvider::class.java.name))
        assertTrue(failure.message.orEmpty().contains(SecondProvider::class.java.name))
        assertSame(active, slot.get())
    }

    @Test
    fun `only the active provider can release a registration`() {
        val slot = ProviderSlot<TestProvider>("test-provider")
        val active = FirstProvider()
        slot.configure(active)

        slot.release(SecondProvider())
        assertSame(active, slot.get())

        slot.release(active)
        val replacement = SecondProvider()
        slot.configure(replacement)
        assertSame(replacement, slot.get())
    }

    private interface TestProvider

    private class FirstProvider : TestProvider

    private class SecondProvider : TestProvider
}
