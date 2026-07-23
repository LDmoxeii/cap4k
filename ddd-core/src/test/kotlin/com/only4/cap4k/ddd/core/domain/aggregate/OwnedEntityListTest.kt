package com.only4.cap4k.ddd.core.domain.aggregate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OwnedEntityListTest {

    @Test
    fun `add mutates delegate and read operations use same delegate`() {
        val delegate = mutableListOf<TestChild>()
        val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.children")
        val child = TestChild("a")

        val added = children.add(child)

        assertTrue(added)
        assertEquals(listOf(child), delegate)
        assertEquals(1, children.size)
        assertSame(child, children[0])
    }

    @Test
    fun `remove mutates delegate without extra lifecycle side effects`() {
        val child = TestChild("a")
        val delegate = mutableListOf(child)
        val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.children")

        val removed = children.remove(child)

        assertTrue(removed)
        assertTrue(delegate.isEmpty())
        assertTrue(children.isEmpty())
    }

    @Test
    fun `remove returns false when entity is absent`() {
        val delegate = mutableListOf(TestChild("a"))
        val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.children")

        val removed = children.remove(TestChild("b"))

        assertFalse(removed)
        assertEquals(1, delegate.size)
    }

    @Test
    fun `iterator removal cannot mutate delegate`() {
        val child = TestChild("a")
        val delegate = mutableListOf(child)
        val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.children")
        @Suppress("UNCHECKED_CAST")
        val iterator = children.iterator() as MutableIterator<TestChild>

        iterator.next()

        assertThrows(UnsupportedOperationException::class.java) {
            iterator.remove()
        }
        assertEquals(listOf(child), delegate)
    }

    @Test
    fun `subList clearing cannot mutate delegate`() {
        val child = TestChild("a")
        val delegate = mutableListOf(child)
        val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.children")
        @Suppress("UNCHECKED_CAST")
        val subList = children.subList(0, 1) as MutableList<TestChild>

        assertThrows(UnsupportedOperationException::class.java) {
            subList.clear()
        }
        assertEquals(listOf(child), delegate)
    }

    @Test
    fun `singleOrNull returns null for empty delegate`() {
        val children = OwnedEntityList.of(mutableListOf<TestChild>(), TestChild::class, "Parent.child")

        assertEquals(null, children.singleOrNull())
    }

    @Test
    fun `singleOrNull returns the only child`() {
        val child = TestChild("a")
        val children = OwnedEntityList.of(mutableListOf(child), TestChild::class, "Parent.child")

        assertSame(child, children.singleOrNull())
    }

    @Test
    fun `singleOrNull fails when delegate has more than one child`() {
        val children = OwnedEntityList.of(
            mutableListOf(TestChild("a"), TestChild("b")),
            TestChild::class,
            "Parent.child",
        )

        val error = assertThrows(IllegalStateException::class.java) {
            children.singleOrNull()
        }

        assertEquals(
            "owned relation Parent.child expected at most one TestChild but found 2",
            error.message,
        )
    }

    @Test
    fun `replace clears delegate for null`() {
        val delegate = mutableListOf(TestChild("a"))
        val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.child")

        children.replace(null)

        assertTrue(delegate.isEmpty())
    }

    @Test
    fun `replace non null clears old child and uses add path`() {
        val old = TestChild("old")
        val new = TestChild("new")
        val delegate = mutableListOf(old)
        val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.child")

        children.replace(new)

        assertEquals(listOf(new), delegate)
        assertSame(new, children.singleOrNull())
    }

    @Test
    fun `replace fails before changing malformed multi child delegate`() {
        val first = TestChild("a")
        val second = TestChild("b")
        val replacement = TestChild("c")
        val delegate = mutableListOf(first, second)
        val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.child")

        val error = assertThrows(IllegalStateException::class.java) {
            children.replace(replacement)
        }

        assertEquals(
            "owned relation Parent.child expected at most one TestChild but found 2",
            error.message,
        )
        assertEquals(listOf(first, second), delegate)
    }

    private data class TestChild(val value: String)
}
