package su.nnedition.nodal

import org.jetbrains.annotations.Contract
import su.nnedition.nodal.trait.CancellableEvent
import java.util.function.BiPredicate
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * Represents a single node in an event graph.
 * 
 * 
 * A node may contain any number of children and/or listeners. When an event is called,
 * the node will filter it based on the parameters given at creation and then propagate
 * it down to child nodes and listeners if it passes.
 * 
 * @param <T> The event type accepted by this node
 */
interface EventNode<T : Event> {
    /**
     * Calls an event starting from this node.
     * 
     * @param event the event to call
     */
    fun call(event: T) {
        getHandle(event.javaClass).call(event)
    }

    fun hasListener(type: Class<out T>): Boolean {
        return getHandle(type).hasListener()
    }

    /**
     * Gets the handle of an event type.
     * 
     * @param handleType the handle type
     * @param <E>        the event type
     * @return the handle linked to [handleType]
    </E> */
    fun <E : T> getHandle(handleType: Class<E>): ListenerHandle<E>

    /**
     * Execute a cancellable event with a callback to execute if the event is successful.
     * Event conditions and propagation is the same as [call].
     * 
     * @param event           The event to execute
     * @param successCallback A callback if the event is not cancelled
     */
    fun callCancellable(event: T, successCallback: Runnable) {
        call(event)
        if (event !is CancellableEvent || !event.isCancelled) {
            successCallback.run()
        }
    }

    @get:Contract(pure = true)
    val eventType: Class<T>

    @get:Contract(pure = true)
    val name: String

    @get:Contract(pure = true)
    val priority: Int

    @Contract(value = "_ -> this")
    fun setPriority(priority: Int): EventNode<T>

    @get:Contract(pure = true)
    val parent: EventNode<in T>?

    /**
     * Returns an unmodifiable view of the children in this node.
     *
     * @see [addChild]
     * @see [removeChild]
     */
    @get:Contract(pure = true)
    val children: Set<EventNode<T>>

    /**
     * Locates all child nodes with the given name and event type recursively starting at this node.
     * 
     * @param name      The event node name to filter for
     * @param eventType The event node type to filter for
     * @return All matching event nodes
     */
    @Contract(pure = true)
    fun <E : T> findChildren(name: String, eventType: Class<E>): MutableList<EventNode<E>>

    /**
     * Locates all child nodes with the given name and event type recursively starting at this node.
     * 
     * @param name The event name to filter for
     * @return All matching event nodes
     */
    @Contract(pure = true)
    fun findChildren(name: String): MutableList<EventNode<T>> {
        return findChildren(name, this.eventType)
    }

    /**
     * Replaces all children matching the given name and type recursively starting from this node.
     *
     * Node: The callee may not be replaced by this call.
     * 
     * @param name      The event name to filter for
     * @param eventType The event node type to filter for
     * @param eventNode The replacement node
     */
    fun <E : T> replaceChildren(name: String, eventType: Class<E>, eventNode: EventNode<E>)

    /**
     * Replaces all children matching the given name and type recursively starting from this node.
     * 
     * Node: The callee may not be replaced by this call.
     * 
     * @param name      The node name to filter for
     * @param eventNode The replacement node
     */
    fun replaceChildren(name: String, eventNode: EventNode<T>) {
        replaceChildren(name, this.eventType, eventNode)
    }

    /**
     * Recursively removes children with the given name and type starting at this node.
     *
     * @param name      The node name to filter for
     * @param eventType The node type to filter for
     */
    fun removeChildren(name: String, eventType: Class<out T>)

    /**
     * Recursively removes children with the given name starting at this node.
     *
     * @param name The node name to filter for
     */
    fun removeChildren(name: String) {
        removeChildren(name, eventType)
    }

    /**
     * Directly adds a child node to this node.
     * 
     * @param child The child to add
     * @return this, can be used for chaining
     */
    @Contract(value = "_ -> this")
    fun addChild(child: EventNode<out T>): EventNode<T>

    /**
     * Directly removes the given child from this node.
     * 
     * @param child The child to remove
     * @return this, can be used for chaining
     */
    @Contract(value = "_ -> this")
    fun removeChild(child: EventNode<out T>): EventNode<T>

    @Contract(value = "_ -> this")
    fun addListener(listener: EventListener<out T>): EventNode<T>

    @Contract(value = "_, _ -> this")
    fun <E : T> addListener(eventType: Class<E>, listener: Consumer<E>): EventNode<T> {
        return addListener(EventListener.of(eventType, listener))
    }

    @Contract(value = "_ -> this")
    fun removeListener(listener: EventListener<out T>): EventNode<T>

    /**
     * Maps a specific object to a node.
     * 
     * 
     * Be aware that such structure have huge performance penalty as they will
     * always require a map lookup. Use only at last resort.
     * 
     * @param value  the mapped value
     * @param filter the filter to use
     * @return the node (which may have already been registered) directly linked to `value`
     */
    fun <E : T, H> map(value: H, filter: EventFilter<E, H>): EventNode<E>

    /**
     * Prevents the node from [map] to be called.
     * 
     * @param value the value to unmap
     */
    fun unmap(value: Any)

    fun register(binding: EventBinding<out T>)

    fun unregister(binding: EventBinding<out T>)

    companion object {
        /**
         * Creates an event node which accepts any event type with no filtering.
         *
         * @param name The name of the node
         * @return An event node with no filtering
         */
        @JvmStatic
        @Contract(value = "_ -> new", pure = true)
        fun all(name: String): EventNode<Event> {
            return type(name, EventFilter.ALL)
        }

        /**
         * Creates an event node which accepts any event of the given type. The type is provided
         * by the [EventFilter].
         *
         * For example, you could create an event filter which only accepts player events with the following
         * ```
         * var playerEventNode = EventNode.type("demo", EventFilter.PLAYER);
         * ```
         * 
         * @param name   The name of the event node
         * @param filter The event type filter to apply
         * @param <E>    The resulting event type of the node
         * @return A node with just an event type filter
         */
        @Contract(value = "_, _ -> new", pure = true)
        fun <E : Event, V> type(
            name: String,
            filter: EventFilter<E, V>
        ): EventNode<E> {
            return create(name, filter, null)
        }

        /**
         * Creates an event node which accepts any event of the given type which passes
         * the provided condition. The condition is based on the event object itself.
         *
         * For example, you could create an event filter which only accepts player events
         * where the player is in the pos x/z quadrant of the world.
         * 
         * ```
         * var playerInPosXZNode = EventNode.event("abc", EventFilter.PLAYER, event -> {
         *    var position = event.getPlayer().getPosition();
         *    return position.getX() > 0 && position.getZ() > 0;
         * });
         * ```
         *
         * @param name      The name of the event node
         * @param filter    The event type filter to apply
         * @param predicate The event condition
         * @param <E>       The resulting event type of the node
         * @return A node with an event type filter as well as a condition on the event.
        </E> */
        @Contract(value = "_, _, _ -> new", pure = true)
        fun <E : Event, V> event(
            name: String,
            filter: EventFilter<E, V>,
            predicate: Predicate<E>
        ): EventNode<E> {
            return create(name, filter) { e, v -> predicate.test(e) }
        }

        /**
         * Creates an event node which accepts any event of the given type which passes
         * the provided condition. The condition is based on the event object as well as
         * the event handler type defined in the [EventFilter].
         * 
         * 
         * For example, you could create an event filter which only accepts player events
         * where the player is in the pos x/z quadrant of the world.
         * 
         * ```
         * var playerInPosXZNode = EventNode.type("abc", EventFilter.PLAYER, (event, player) -> {
         *    var position = player.getPosition();
         *    return position.getX() > 0 && position.getZ() > 0;
         * });
         * ```
         * 
         * @param name      The name of the event node
         * @param filter    The event type filter to apply
         * @param predicate The event condition
         * @param <E>       The resulting event type of the node
         * @param <V>       The handler type of the event filter
         * @return A node with an event type filter as well as a condition on the event.
         */
        @Contract(value = "_, _, _ -> new", pure = true)
        fun <E : Event, V> type(
            name: String,
            filter: EventFilter<E, V>,
            predicate: BiPredicate<E, V>,
        ): EventNode<E> {
            return create(name, filter, predicate)
        }

        /**
         * Creates an event node which accepts any event of the given type which passes
         * the provided condition. The condition is based on the event handler defined
         * by the [EventFilter].
         *
         * For example, you could create an event filter which only accepts player events
         * where the player is in creative mode.
         * ```
         * var playerIsCreative = EventNode.value("abc", EventFilter.PLAYER, Player::isCreative);
         * ```
         * 
         * @param name      The name of the event node
         * @param filter    The event type filter to apply
         * @param predicate The event condition
         * @param <E>       The resulting event type of the node
         * @param <V>       The handler type of the event filter
         * @return A node with an event type filter as well as a condition on the event.
         */
        @Contract(value = "_, _, _ -> new", pure = true)
        fun <E : Event, V> value(
            name: String,
            filter: EventFilter<E, V>,
            predicate: Predicate<V>
        ): EventNode<E> {
            return create(name, filter) { e, h -> predicate.test(h) }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <E : Event, V> create(
            name: String,
            filter: EventFilter<E, V>,
            predicate: BiPredicate<E, V>?
        ): EventNode<E> {
            return EventNodeImpl(
                name,
                filter,
                if (predicate != null) BiPredicate { e, o ->
                    predicate.test(e, o as V)
                } else null
            )
        }
    }
}
