package su.nnedition.nodal.trait

import su.nnedition.nodal.Event

/**
 * Represents an [Event] that is guaranteed to be called within a Virtual Thread.
 */
interface AsyncEvent : Event
