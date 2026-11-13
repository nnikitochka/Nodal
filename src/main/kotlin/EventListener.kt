package su.nnedition.nodal

import org.jetbrains.annotations.Contract
import su.nnedition.nodal.trait.CancellableEvent
import su.nnedition.nodal.trait.RecursiveEvent
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

/**
 * Represents an event listener (handler) in an event graph.
 *
 * A listener is responsible for executing some action based on an event triggering.
 *
 * @param <T> The event type being handled.
 */
interface EventListener<T : Event> {
    fun eventType(): Class<T>

    fun run(event: T): Result

    class Builder<T : Event>(
        private val eventType: Class<T>
    ) {
        private val filters: MutableList<Predicate<T>> = ArrayList()
        private var ignoreCancelled = true
        private var expireCount = 0
        private var expireWhen: Predicate<T>? = null
        private var handler: Consumer<T>? = null

        /**
         * Adds a filter to the executor of this listener. The executor will only
         * be called if this condition passes on the given event.
         */
        @Contract(value = "_ -> this")
        fun filter(filter: Predicate<T>): Builder<T> {
            this.filters.add(filter)
            return this
        }

        /**
         * Specifies if the handler should still be called if [CancellableEvent.isCancelled] returns `true`.
         *
         * Default is set to `true`.
         *
         * @param ignoreCancelled True to stop processing the event when cancelled
         */
        @Contract(value = "_ -> this")
        fun ignoreCancelled(ignoreCancelled: Boolean): Builder<T> {
            this.ignoreCancelled = ignoreCancelled
            return this
        }

        /**
         * Removes this listener after it has been executed the given number of times.
         *
         * @param expireCount The number of times to execute
         */
        @Contract(value = "_ -> this")
        fun expireCount(expireCount: Int): Builder<T> {
            this.expireCount = expireCount
            return this
        }

        /**
         * Expires this listener when it passes the given condition. The expiration will
         * happen before the event is executed.
         *
         * @param expireWhen The condition to test
         */
        @Contract(value = "_ -> this")
        fun expireWhen(expireWhen: Predicate<T>): Builder<T> {
            this.expireWhen = expireWhen
            return this
        }

        /**
         * Sets the handler for this event listener. This will be executed if the listener passes
         * all conditions.
         */
        @Contract(value = "_ -> this")
        fun handler(handler: Consumer<T>): Builder<T> {
            this.handler = handler
            return this
        }

        @Contract(value = "-> new", pure = true)
        fun build(): EventListener<T> {
            val ignoreCancelled = this.ignoreCancelled
            val expirationCount = AtomicInteger(this.expireCount)
            val hasExpirationCount = expirationCount.get() > 0

            val expireWhen = this.expireWhen

            val filters = ArrayList<Predicate<T>>(this.filters)
            val handler = this.handler
            return ListenerImpl(eventType) { event ->
                // Event cancellation
                if (ignoreCancelled && event is CancellableEvent &&
                    event.isCancelled
                ) {
                    return@ListenerImpl Result.INVALID
                }
                // Expiration predicate
                if (expireWhen != null && expireWhen.test(event)) {
                    return@ListenerImpl Result.EXPIRED
                }
                // Filtering
                if (filters.isNotEmpty()) {
                    for (filter in filters) {
                        if (!filter.test(event)) {
                            // Cancelled
                            return@ListenerImpl Result.INVALID
                        }
                    }
                }
                // Handler
                handler?.accept(event)
                // Expiration count
                if (hasExpirationCount && expirationCount.decrementAndGet() == 0) {
                    return@ListenerImpl Result.EXPIRED
                }
                Result.SUCCESS
            }
        }
    }

    enum class Result {
        SUCCESS,
        INVALID,
        EXPIRED,
        EXCEPTION
    }

    companion object {
        @JvmStatic
        @Contract(pure = true)
        fun <T : Event> builder(eventType: Class<T>): Builder<T> {
            return Builder(eventType)
        }

        inline fun <reified T : Event> builder(): Builder<T> {
            return Builder(T::class.java)
        }

        /**
         * Create an event listener without any special options. The given listener will be executed
         * if the event passes all parent filtering.
         *
         * @param eventType The event type to handle
         * @param listener  The handler function
         * @param <T>       The event type to handle
         * @return An event listener with the given properties
         */
        @JvmStatic
        @Contract(pure = true)
        fun <T : Event> of(eventType: Class<T>, listener: Consumer<T>): EventListener<T> {
            if (
                CancellableEvent::class.java.isAssignableFrom(eventType)
                || RecursiveEvent::class.java.isAssignableFrom(eventType)
            ) {
                return ListenerImpl(eventType) { event: T ->
                    if (event is CancellableEvent && event.isCancelled) {
                        return@ListenerImpl Result.INVALID
                    }
                    listener.accept(event)
                    Result.SUCCESS
                }
            } else {
                return ListenerImpl(eventType) { event: T ->
                    listener.accept(event)
                    Result.SUCCESS
                }
            }
        }

        inline fun <reified T : Event> of(noinline listener: (T) -> Unit): EventListener<T> {
            return of(T::class.java, Consumer(listener))
        }
    }
}

private data class ListenerImpl<T : Event>(
    private val eventType: Class<T>,
    private val function: Function<T, EventListener.Result>
) : EventListener<T> {
    override fun eventType(): Class<T> = eventType

    override fun run(event: T): EventListener.Result = function.apply(event)
}
