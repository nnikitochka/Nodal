package su.nnedition.nodal

import java.util.function.Function

/**
 * Represents a filter for a specific [Event] type.
 * 
 * 
 * The handler represents a "target" of the event. This can be used
 * to create filters for all events of a specific type using information
 * about the target.
 * 
 * 
 * 
 * @param <E> The event type to filter
 * @param <H> The handler type to filter on.
</H></E> */
interface EventFilter<E : Event, H> {
    /**
     * Gets the handler for the given event instance, or null if the event
     * type has no handler.
     * 
     * @param event The event instance
     * @return The handler, if it exists for the given event
     */
    fun getHandler(event: E): H?

    @Suppress("UNCHECKED_CAST")
    fun castHandler(event: Any): H? {
        return getHandler(event as E)
    }

    /**
     * The event type to filter on.
     *
     * @return The event type.
     */
    fun eventType(): Class<E>

    /**
     * The type returned by [.getHandler].
     * 
     * @return the handler type, null if not any
     */
    fun handlerType(): Class<H>?

    companion object {
        @JvmField
        val ALL: EventFilter<Event, *> = from<Event, Any>(Event::class.java, null, null)

        @JvmStatic
        fun <E : Event, H> from(
            eventType: Class<E>,
            handlerType: Class<H>?,
            handlerGetter: Function<E, H>?
        ): EventFilter<E, H> = object : EventFilter<E, H> {
            override fun getHandler(event: E): H? = handlerGetter?.apply(event)

            override fun eventType(): Class<E> = eventType

            override fun handlerType(): Class<H>? = handlerType
        }
    }
}
