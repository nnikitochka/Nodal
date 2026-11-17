package su.nnedition.nodal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import su.nnedition.nodal.trait.CancellableEvent
import su.nnedition.nodal.trait.RecursiveEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class EventNodeTest {

    class SimpleEvent(val message: String) : Event

    class TestCancellableEvent(
        val value: Int,
        override var isCancelled: Boolean = false
    ) : CancellableEvent

    open class BaseRecursiveEvent : RecursiveEvent
    class ChildRecursiveEvent : BaseRecursiveEvent()

    @Test
    fun testBasicDispatch() {
        val node = EventNode.all("test")
        var received = ""
        node.addListener(EventListener.of(SimpleEvent::class.java) {
            received = it.message
        })

        node.call(SimpleEvent("hello"))
        assertEquals("hello", received)
    }

    @Test
    fun testTreePropagation() {
        val root = EventNode.all("root")
        val child = EventNode.all("child")
        root.addChild(child)

        var count = 0
        child.addListener(EventListener.of(SimpleEvent::class.java) {
            count++
        })

        root.call(SimpleEvent("ping"))
        assertEquals(1, count)

        root.removeChild(child)
        root.call(SimpleEvent("ping"))
        assertEquals(1, count, "Detached child should not receive events")
    }

    @Test
    fun testCancellation() {
        val root = EventNode.all("root")
        root.addListener(EventListener.of(TestCancellableEvent::class.java) {
            if (it.value > 10) it.isCancelled = true
        })

        val successCalled = AtomicBoolean(false)
        root.callCancellable(TestCancellableEvent(15)) {
            successCalled.set(true)
        }
        assertFalse(successCalled.get(), "Callback should not run when event is cancelled")

        root.callCancellable(TestCancellableEvent(5)) {
            successCalled.set(true)
        }
        assertTrue(successCalled.get(), "Callback should run when event is not cancelled")
    }

    @Test
    fun testExpireCount() {
        val node = EventNode.all("expire-test")
        val counter = AtomicInteger(0)

        val listener = EventListener.builder(SimpleEvent::class.java)
            .expireCount(2)
            .handler { counter.incrementAndGet() }
            .build()

        node.addListener(listener)

        node.call(SimpleEvent("1"))
        node.call(SimpleEvent("2"))
        node.call(SimpleEvent("3"))

        assertEquals(2, counter.get(), "Listener should expire after 2 invocations")
    }

    @Test
    fun testRecursiveEvent() {
        val node = EventNode.all("recursive-test")
        val baseHandled = AtomicBoolean(false)

        node.addListener(EventListener.of(BaseRecursiveEvent::class.java) {
            baseHandled.set(true)
        })

        node.call(ChildRecursiveEvent())
        assertTrue(baseHandled.get(), "Superclass listener should handle recursive child event")
    }
}
