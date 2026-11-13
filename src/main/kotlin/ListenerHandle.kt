package su.nnedition.nodal

/**
 * Represents a key to a listenable event, retrievable from [EventNode.getHandle].
 * Useful to avoid map lookups.
 * 
 * 
 * It is recommended to store instances of this class in `static final` fields.
 * 
 * @param <E> the event type
</E> */
interface ListenerHandle<E : Event> {
    /**
     * Calls the given event.
     * Will try to fast exit the execution when possible if [.hasListener] return `false`.
     * 
     * 
     * Anonymous and subclasses are not supported, events must have the exact type `E`.
     * 
     * @param event the event to call
     */
    fun call(event: E)

    /**
     * Gets if any listener has been registered for the given handle.
     * May trigger an update if the cached data is not correct.
     * 
     * 
     * Useful if you are able to avoid expensive computation in the case where
     * the event is unused. Be aware that [.call]
     * has similar optimization built-in.
     * 
     * @return true if the event has 1 or more listeners
     */
    fun hasListener(): Boolean
}
