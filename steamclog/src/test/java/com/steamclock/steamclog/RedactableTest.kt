package com.steamclock.steamclog

import org.junit.Assert.assertEquals
import org.junit.Test

class RedactableTest {

    class Node(val name: String) {
        var next: Node? = null
    }

    enum class Kind { A, B }

    data class Boxed(val count: Int?, val kind: Kind, val id: Long)

    data class Leaves(val letter: Char, val flag: Boolean, val text: CharSequence, val ratio: Double)

    class Throwing {
        @Suppress("unused")
        val boom: String
            get() = throw IllegalStateException("getter failed")
    }

    class Outer(val inner: Throwing, val name: String)

    data class User(val name: String, val email: String) : Redactable {
        override val safeProperties = setOf("name")
    }

    @Test
    fun selfReferenceTerminatesWithPlaceholder() {
        val a = Node("a").also { it.next = it }
        assertEquals("Node(name=a, next=<visited Node>)", a.getRedactedDescription(false))
    }

    @Test
    fun indirectCycleTerminatesWithPlaceholder() {
        val a = Node("a")
        val b = Node("b")
        a.next = b
        b.next = a
        assertEquals(
            "Node(name=a, next=Node(name=b, next=<visited Node>))",
            a.getRedactedDescription(false)
        )
    }

    @Test
    fun boxedPrimitivesAndEnumsPrintTheirValue() {
        assertEquals("Boxed(count=3, id=7, kind=B)", Boxed(3, Kind.B, 7L).getRedactedDescription(false))
    }

    @Test
    fun nullBoxedPrimitivePrintsNull() {
        assertEquals("Boxed(count=null, id=7, kind=A)", Boxed(null, Kind.A, 7L).getRedactedDescription(false))
    }

    @Test
    fun charBooleanCharSequenceAndNumberPrintTheirValue() {
        val leaves = Leaves('x', true, StringBuilder("hi"), 1.5)
        assertEquals("Leaves(flag=true, letter=x, ratio=1.5, text=hi)", leaves.getRedactedDescription(false))
    }

    @Test
    fun throwingGetterDoesNotThrow() {
        assertEquals(
            "Throwing(<description failed: IllegalStateException>)",
            Throwing().getRedactedDescription(false)
        )
    }

    @Test
    fun throwingChildKeepsSiblingProperties() {
        assertEquals(
            "Outer(inner=Throwing(<description failed: IllegalStateException>), name=n)",
            Outer(Throwing(), "n").getRedactedDescription(false)
        )
    }

    @Test
    fun redactableHidesUnsafeProperties() {
        assertEquals(
            "User(email=<redacted>, name=shayla)",
            User("shayla", "me@email.com").getRedactedDescription(false)
        )
    }

    @Test
    fun topLevelLeafValuesPrintTheirValue() {
        assertEquals("5", 5.getRedactedDescription(false))
        assertEquals("B", Kind.B.getRedactedDescription(false))
        assertEquals("true", true.getRedactedDescription(false))
        assertEquals("x", 'x'.getRedactedDescription(false))
        assertEquals("hi", "hi".getRedactedDescription(false))
    }

    @Test
    fun requireRedactedHidesTopLevelLeafValues() {
        assertEquals("<redacted>", 5.getRedactedDescription(true))
        assertEquals("<redacted>", Kind.B.getRedactedDescription(true))
        assertEquals("<redacted>", "secret".getRedactedDescription(true))
    }

    @Test
    fun requireRedactedHidesPropertiesOfNonRedactableClasses() {
        assertEquals("Boxed(count=<redacted>, id=<redacted>, kind=<redacted>)", Boxed(3, Kind.B, 7L).getRedactedDescription(true))
    }
}
