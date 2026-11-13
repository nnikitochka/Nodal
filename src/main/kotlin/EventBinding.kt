package su.nnedition.nodal

import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Predicate

interface EventBinding<E : Event> {
    fun eventTypes(): Collection<Class<out Event>>

    fun consumer(eventType: Class<out Event>): Consumer<E>

    class FilteredBuilder<E : Event, T> internal constructor(
        private val filter: EventFilter<E, T>,
        private val predicate: Predicate<T>
    ) {
        private val mapped: MutableMap<Class<out Event>, BiConsumer<Any, E>> = HashMap()

        @Suppress("UNCHECKED_CAST")
        fun <M : E> map(
            eventType: Class<M>,
            consumer: BiConsumer<T, M>
        ): FilteredBuilder<E, T> {
            this.mapped[eventType] = consumer as BiConsumer<Any, E>
            return this
        }

        fun build(): EventBinding<E> {
            val copy = mapped.toMap()
            val eventTypes = copy.keys

            val consumers = HashMap<Class<out Event>, Consumer<E>>(eventTypes.size)
            for (eventType in eventTypes) {
                val consumer = copy[eventType]!!
                consumers[eventType] = Consumer { event: E ->
                    val handler = filter.getHandler(event) ?: return@Consumer
                    if (!predicate.test(handler)) return@Consumer
                    consumer.accept(handler, event)
                }
            }
            return object : EventBinding<E> {
                override fun eventTypes(): Set<Class<out Event>> = eventTypes

                override fun consumer(eventType: Class<out Event>): Consumer<E> {
                    return consumers[eventType] ?: error("No consumer for event type $eventType")
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun <E : Event, T> filtered(
            filter: EventFilter<E, T>,
            predicate: Predicate<T>
        ): FilteredBuilder<E, T> = FilteredBuilder(filter, predicate)
    }
}
