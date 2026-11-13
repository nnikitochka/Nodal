package su.nnedition.nodal.trait

import su.nnedition.nodal.Event
import su.nnedition.nodal.EventDispatcher

/**
 * Represents an [Event] which can be cancelled.
 * Called using [EventDispatcher.callCancellable].
 */
interface CancellableEvent : Event {
    /**
     * Gets if the [Event] should be cancelled or not.
     *
     * Marks the [Event] as cancelled or not.
     *
     * @return true if the event should be cancelled
     */
    var isCancelled: Boolean
}
