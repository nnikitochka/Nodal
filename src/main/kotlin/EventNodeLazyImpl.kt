package su.nnedition.nodal

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.lang.ref.WeakReference
import java.util.*
import java.util.function.Consumer

internal class EventNodeLazyImpl<E : Event>(
    private val holder: EventNodeImpl<in E>,
    owner: Any,
    filter: EventFilter<E, *>,
) : EventNodeImpl<E>(owner.toString(), filter, null) {
    private val owner = WeakReference(owner)

    @Suppress("unused")
    private val mapped = false

    override fun addChild(child: EventNode<out E>): EventNode<E> {
        ensureMap()
        return super.addChild(child)
    }

    override fun addListener(listener: EventListener<out E>): EventNode<E> {
        ensureMap()
        return super.addListener(listener)
    }

    override fun <E1 : E> addListener(eventType: Class<E1>, listener: Consumer<E1>): EventNode<E> {
        ensureMap()
        return super.addListener<E1>(eventType, listener)
    }

    override fun <E1 : E, H> map(value: H?, filter: EventFilter<E1, H>): EventNode<E1> {
        val owner = retrieveOwner()
        require(owner === value) { "Cannot map an object to an already mapped node." }
        @Suppress("UNCHECKED_CAST")
        return this as EventNode<E1>
    }

    override fun register(binding: EventBinding<out E>) {
        ensureMap()
        super.register(binding)
    }

    // owner map mixes capture types by design
    @Suppress("UNCHECKED_CAST")
    private fun ensureMap() {
        if (MAPPED.compareAndSet(this, false, true)) {
            synchronized(GLOBAL_CHILD_LOCK) {
                val rawHolder = this.holder as EventNodeImpl<Event>
                val registered = WeakHashMap<Any, WeakReference<EventNodeLazyImpl<Event>>>(rawHolder.registeredMappedNode)
                val previous = registered.putIfAbsent(
                    retrieveOwner(),
                    WeakReference(this as EventNodeLazyImpl<Event>)
                )
                rawHolder.registeredMappedNode = registered
                if (previous == null) invalidateEventsFor(holder)
            }
        }
    }

    private fun retrieveOwner(): Any = this.owner.get()
        ?: throw IllegalStateException("Node handle is null. Be sure to never cache a local node.")

    companion object {
        private val MAPPED: VarHandle

        init {
            try {
                MAPPED = MethodHandles.lookup().findVarHandle(
                    EventNodeLazyImpl::class.java,
                    "mapped",
                    Boolean::class.javaPrimitiveType
                )
            } catch (e: NoSuchFieldException) {
                throw IllegalStateException(e)
            } catch (e: IllegalAccessException) {
                throw IllegalStateException(e)
            }
        }
    }
}
