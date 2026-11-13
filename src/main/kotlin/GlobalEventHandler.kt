package su.nnedition.nodal

/**
 * Object containing all the global event listeners.
 */
class GlobalEventHandler : EventNodeImpl<Event>(
    "global",
    EventFilter.ALL,
    null
)
