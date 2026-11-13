package su.nnedition.nodal.trait

import su.nnedition.nodal.Event

/**
 * Marker interface for events whose listeners should be triggered polymorphically
 * up the inheritance hierarchy (superclasses).
 *
 * By default, event dispatching targets only the exact class type for O(1) performance.
 * Implementing [RecursiveEvent] instructs the dispatcher to recursively notify listeners
 * of all parent classes that also extend [RecursiveEvent].
 */
interface RecursiveEvent : Event

