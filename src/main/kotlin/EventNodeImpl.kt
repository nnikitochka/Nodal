package su.nnedition.nodal;

import su.nnedition.nodal.trait.AsyncEvent
import su.nnedition.nodal.trait.RecursiveEvent
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.function.BiConsumer
import java.util.function.BiPredicate
import java.util.function.Consumer
import kotlin.concurrent.Volatile

open class EventNodeImpl<T : Event> internal constructor(
    override val name: String,
    val filter: EventFilter<T, *>,
    val predicate: BiPredicate<T, Any>?,
) : EventNode<T> {
    override val eventType: Class<T> = filter.eventType()

    private val handleMap: MutableMap<Class<*>, Handle<T>> = ConcurrentHashMap()
    val listenerMap: MutableMap<Class<out T>, ListenerEntry<T>> = ConcurrentHashMap()
    override val children: MutableSet<EventNodeImpl<T>> = CopyOnWriteArraySet()

    // Used to store mapped nodes before any listener is added
    // Necessary to avoid creating multiple nodes for the same object
    // Always accessed through the global lock.
    val mappedNodeCache: MutableMap<Any, WeakReference<EventNodeLazyImpl<T>>> = WeakHashMap()

    // Store mapped nodes with at least one listener
    // Map is copied and mutated for each new active mapped node
    // Can be considered immutable.
    @Volatile
    var registeredMappedNode: MutableMap<Any, WeakReference<EventNodeLazyImpl<T>>> = WeakHashMap()

    @Volatile
    override var priority: Int = 0

    @Volatile
    override var parent: EventNodeImpl<in T>? = null
        get() {
            check(!NodalConfig.allowMultipleParents) {
                "Cannot use getParent when multiple parents are allowed"
            }
            return field
        }

    @Suppress("UNCHECKED_CAST")
    override fun <E : T> getHandle(handleType: Class<E>): ListenerHandle<E> {
        return handleMap.computeIfAbsent(handleType) { aClass ->
            Handle(aClass as Class<T>)
        } as ListenerHandle<E>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <E : T> findChildren(name: String, eventType: Class<E>): MutableList<EventNode<E>> {
        synchronized(GLOBAL_CHILD_LOCK) {
            val children = children
            if (children.isEmpty()) return mutableListOf()
            val result: MutableList<EventNode<E>> = ArrayList()
            for (child in children) {
                if (equals(child, name, eventType)) {
                    result.add(child as EventNode<E>)
                }
                result.addAll(child.findChildren(name, eventType))
            }
            return result
        }
    }

    override fun <E : T> replaceChildren(name: String, eventType: Class<E>, eventNode: EventNode<E>) {
        synchronized(GLOBAL_CHILD_LOCK) {
            val children = children
            if (children.isEmpty()) return
            for (child in children) {
                if (equals(child, name, eventType)) {
                    removeChild(child)
                    addChild(eventNode)
                    break
                }
                child.replaceChildren(name, eventType, eventNode)
            }
        }
    }

    override fun removeChildren(name: String, eventType: Class<out T>) {
        synchronized(GLOBAL_CHILD_LOCK) {
            val children = children
            if (children.isEmpty()) return
            for (child in children) {
                if (equals(child, name, eventType)) {
                    removeChild(child)
                    continue
                }
                child.removeChildren(name, eventType)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun addChild(child: EventNode<out T>): EventNode<T> {
        synchronized(GLOBAL_CHILD_LOCK) {
            val childImpl = child as EventNodeImpl<out T>
            check(NodalConfig.allowMultipleParents || childImpl.parent == null) {
                "Node already has a parent"
            }
            check(parent != child) { "Cannot have a child as parent" }
            if (!children.add((childImpl as EventNodeImpl<T>))) return this // Couldn't add the child (already present?)

            childImpl.parent = this
            childImpl.invalidateEventsFor(this)
        }
        return this
    }

    override fun removeChild(child: EventNode<out T>): EventNode<T> {
        synchronized(GLOBAL_CHILD_LOCK) {
            val childImpl = child as EventNodeImpl<out T>
            val result = this.children.remove(childImpl)
            if (!result) return this // Child not found

            childImpl.parent = null
            childImpl.invalidateEventsFor(this)
        }
        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun addListener(listener: EventListener<out T>): EventNode<T> {
        synchronized(GLOBAL_CHILD_LOCK) {
            val eventType = listener.eventType()
            val entry = getEntry(eventType)
            entry.listeners.add(listener as EventListener<T>)
            invalidateEvent(eventType)
        }
        return this
    }

    override fun removeListener(listener: EventListener<out T>): EventNode<T> {
        synchronized(GLOBAL_CHILD_LOCK) {
            val eventType = listener.eventType()
            val entry = listenerMap[eventType] ?: return this // There is no listener with such type
            if (entry.listeners.remove(listener)) invalidateEvent(eventType)
        }
        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun <E : T, H : Any> map(value: H, filter: EventFilter<E, H>): EventNode<E> {
        val node: EventNodeImpl<E>
        synchronized(GLOBAL_CHILD_LOCK) {
            node = EventNodeLazyImpl(this, value, filter)
            check(node.parent == null) { "Node already has a parent" }
            check(parent != node) { "Cannot map to self" }
            val previousRef = this.mappedNodeCache.putIfAbsent(
                value,
                WeakReference(node as EventNodeLazyImpl<T>)
            )
            previousRef?.get()?.let { return it as EventNode<E> }

            node.parent = this
        }
        return node
    }

    override fun unmap(value: Any) {
        synchronized(GLOBAL_CHILD_LOCK) {
            val registered: MutableMap<Any, WeakReference<EventNodeLazyImpl<T>>> =
                WeakHashMap(registeredMappedNode)

            val mappedNodeRef: WeakReference<EventNodeLazyImpl<T>>? = registered.remove(value)
            this.registeredMappedNode = registered

            mappedNodeRef?.get()?.invalidateEventsFor(this)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun register(binding: EventBinding<out T>) {
        synchronized(GLOBAL_CHILD_LOCK) {
            for (eventType in binding.eventTypes()) {
                val entry = getEntry(eventType as Class<out T>)
                val consumer = binding.consumer(eventType) as Consumer<T>
                val added = entry.bindingConsumers.add(consumer)
                if (added) invalidateEvent(eventType)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun unregister(binding: EventBinding<out T>) {
        synchronized(GLOBAL_CHILD_LOCK) {
            for (eventType in binding.eventTypes()) {
                val entry = listenerMap[eventType] ?: return
                val removed = entry.bindingConsumers.remove(binding.consumer(eventType))
                if (removed) invalidateEvent(eventType as Class<out T>)
            }
        }
    }

    override fun setPriority(priority: Int): EventNode<T> {
        this.priority = priority
        return this
    }

    override fun toString(): String {
        return createStringGraph(createGraph())
    }

    private fun createGraph(): Graph {
        synchronized(GLOBAL_CHILD_LOCK) {
            val children = this.children.map { node -> node.createGraph() }
            return Graph(name, eventType.getSimpleName(), priority, children)
        }
    }

    class Graph internal constructor (
        val name: String,
        val eventType: String,
        val priority: Int,
        children: List<Graph>
    ) {
        val children: List<Graph> = children
            .sortedBy { it.priority }
    }

    fun invalidateEventsFor(node: EventNodeImpl<in T>) {
        assert(Thread.holdsLock(GLOBAL_CHILD_LOCK))
        for (eventType in listenerMap.keys) {
            node.invalidateEvent(eventType)
        }
        // TODO bindings?
        for (child in children) {
            child.invalidateEventsFor(node)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun invalidateEvent(eventClass: Class<out T>) {
        forTargetEvents(eventClass) { type ->
            val handle = handleMap.computeIfAbsent(type) { aClass ->
                Handle(aClass as Class<T>)
            }
            handle.invalidate()
        }
        invalidateRecursiveSuperclasses(eventClass)

        parent?.invalidateEvent(eventClass)
    }

    private fun invalidateRecursiveSuperclasses(eventClass: Class<*>) {
        if (!RecursiveEvent::class.java.isAssignableFrom(eventClass)) return
        for (clazz in handleMap.keys) {
            if (!eventClass.isAssignableFrom(clazz)) continue
            handleMap[clazz]!!.invalidate()
        }
    }

    private fun getEntry(type: Class<out T>): ListenerEntry<T> {
        return listenerMap.computeIfAbsent(type) { ListenerEntry() }
    }

    class ListenerEntry<T : Event> internal constructor() {
        val listeners: MutableList<EventListener<T>> = CopyOnWriteArrayList()
        val bindingConsumers: MutableSet<Consumer<T>> = CopyOnWriteArraySet()
    }

    inner class Handle<E : Event> internal constructor(
        private val eventType: Class<E>
    ) : ListenerHandle<E> {
        private var listener: Consumer<E>? = null

        @Volatile
        private var updated = false

        override fun call(event: E) {
            assert(
                event !is AsyncEvent || Thread.currentThread().isVirtual
            ) { "AsyncEvent must be called within a Virtual Thread, got " + Thread.currentThread() }
            val listener = updatedListener() ?: return
            try {
                listener.accept(event)
            } catch (e: Throwable) {
                e.printStackTrace(System.err)
            }
        }

        override fun hasListener(): Boolean {
            return updatedListener() != null
        }

        fun invalidate() {
            this.updated = false
            this.listener = null
        }

        fun updatedListener(): Consumer<E>? {
            if (updated) return listener
            synchronized(GLOBAL_CHILD_LOCK) {
                if (updated) return listener
                val listener = createConsumer()
                this.listener = listener
                this.updated = true
                return listener
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun createConsumer(): Consumer<E>? {
            val node = this@EventNodeImpl as EventNodeImpl<E>
            // Standalone listeners
            val listeners: MutableList<Consumer<E>> = ArrayList()
            forTargetEvents(eventType) { type: Class<*> ->
                val entry = node.listenerMap[type] ?: return@forTargetEvents

                listenersConsumer(entry)?.let { result ->
                    listeners.add(result)
                }
            }
            val listenersArray: Array<Consumer<E>> = listeners.toTypedArray()
            // Mapped
            val mappedListener = mappedConsumer()
            // Children
            val childrenListeners: Array<Consumer<E>> = node.children
                .filter { child -> child.eventType.isAssignableFrom(eventType) } // Invalid event type
                .sortedBy { it.priority }
                .mapNotNull { child ->
                    (child.getHandle(eventType) as Handle<E>).updatedListener()
                }
                .toTypedArray()

            // Empty check
            val predicate = node.predicate
            val filter = node.filter
            val hasPredicate = predicate != null
            val hasListeners = listenersArray.isNotEmpty()
            val hasMap = mappedListener != null
            val hasChildren = childrenListeners.isNotEmpty()
            if (!hasListeners && !hasMap && !hasChildren) {
                // No listener
                return null
            }
            return Consumer { e ->
                // Filtering
                if (hasPredicate) {
                    val value = filter.getHandler(e)!!
                    if (!predicate.test(e, value)) return@Consumer
                }
                // Normal listeners
                if (hasListeners) {
                    for (listener in listenersArray) {
                        listener.accept(e)
                    }
                }
                // Mapped nodes
                if (hasMap) mappedListener.accept(e)
                // Children
                if (hasChildren) {
                    for (childHandle in childrenListeners) {
                        childHandle.accept(e)
                    }
                }
            }
        }

        /**
         * Create a consumer calling all listeners from [EventNode.addListener] and
         * [EventNode.register].
         *
         * Most computation should ideally be done outside the consumers as a one-time cost.
         */
        private fun listenersConsumer(entry: ListenerEntry<E>): Consumer<E>? {
            val listenersCopy: Array<EventListener<E>> = entry.listeners.toTypedArray()
            val bindingsCopy: Array<Consumer<E>> = entry.bindingConsumers.toTypedArray()
            val listenersEmpty = listenersCopy.isEmpty()
            val bindingsEmpty = bindingsCopy.isEmpty()
            if (listenersEmpty && bindingsEmpty) return null
            if (bindingsEmpty && listenersCopy.size == 1) {
                // Only one normal listener
                val listener = listenersCopy[0]
                return Consumer { e -> callListener(listener, e) }
            }
            // Worse case scenario, try to run everything
            return Consumer { event ->
                if (!listenersEmpty) {
                    for (listener in listenersCopy) {
                        callListener(listener, event)
                    }
                }
                if (!bindingsEmpty) {
                    for (eConsumer in bindingsCopy) {
                        eConsumer.accept(event)
                    }
                }
            }
        }

        /**
         * Create a consumer handling [EventNode.map].
         * The goal is to limit the amount of map lookup.
         */
        @Suppress("UNCHECKED_CAST")
        private fun mappedConsumer(): Consumer<E>? {
            val node = this@EventNodeImpl as EventNodeImpl<E>
            val mappedNodeCache = node.registeredMappedNode
            if (mappedNodeCache.isEmpty()) return null
            val filters: MutableSet<EventFilter<E, *>> = HashSet(mappedNodeCache.size)
            val handlers: MutableMap<Any, WeakReference<Handle<E>>> = WeakHashMap(mappedNodeCache.size)

            // Retrieve all filters used to retrieve potential handlers
            for ((key, mappedNodeRef) in mappedNodeCache) {
                val mappedNode = mappedNodeRef.get() ?: continue // Weak reference collected

                val handle = mappedNode.getHandle(eventType) as Handle<E>
                if (!handle.hasListener()) continue  // Implicit update

                filters.add(mappedNode.filter)
                handlers[key] = WeakReference(handle)
            }
            // If at least one mapped node listen to this handle type,
            // loop through them and forward to mapped node if there is a match
            if (filters.isEmpty()) return null
            val filterList = filters.toTypedArray()
            val mapper = BiConsumer { filter: EventFilter<E, *>, event: E ->
                val handler: Any? = filter.castHandler(event)
                val handleRef = handlers[handler]
                val handle = handleRef?.get()
                handle?.call(event)
            }
            // Specialize the consumer depending on the number of filters to avoid looping
            return when (filterList.size) {
                1 -> Consumer { event -> mapper.accept(filterList[0], event) }
                2 -> Consumer { event ->
                    mapper.accept(filterList[0], event)
                    mapper.accept(filterList[1], event)
                }

                3 -> Consumer { event ->
                    mapper.accept(filterList[0], event)
                    mapper.accept(filterList[1], event)
                    mapper.accept(filterList[2], event)
                }

                else -> Consumer { event ->
                    for (filter in filterList) {
                        mapper.accept(filter, event)
                    }
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun callListener(listener: EventListener<E>, event: E) {
            val node = this@EventNodeImpl as EventNodeImpl<E>
            val result = listener.run(event)
            if (result == EventListener.Result.EXPIRED) {
                node.removeListener(listener)
                invalidate()
            }
        }
    }

    companion object {
        val GLOBAL_CHILD_LOCK: Any = Any()

        fun createStringGraph(graph: Graph): String {
            val buffer = StringBuilder()
            genToStringTree(buffer, "", "", graph)
            return buffer.toString()
        }

        private fun genToStringTree(buffer: StringBuilder, prefix: String, childrenPrefix: String, graph: Graph) {
            buffer.append(prefix)
            buffer.append(
                "${graph.name} - EventType: ${graph.eventType} - Priority: ${graph.priority}"
            )
            buffer.append('\n')
            val nextNodes = graph.children
            val iterator = nextNodes.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (iterator.hasNext()) {
                    genToStringTree(buffer,  "$childrenPrefix├─ ", "$childrenPrefix│   ", next)
                } else {
                    genToStringTree(buffer, "$childrenPrefix└─ ", "$childrenPrefix    ", next)
                }
            }
        }

        private fun equals(node: EventNode<*>, name: String, eventType: Class<*>): Boolean {
            return node.name == name && eventType.isAssignableFrom(node.eventType)
        }

        private fun forTargetEvents(type: Class<*>, consumer: Consumer<Class<*>>) {
            consumer.accept(type)
            // Recursion
            if (!RecursiveEvent::class.java.isAssignableFrom(type)) return
            val superclass = type.getSuperclass() ?: return
            if (!RecursiveEvent::class.java.isAssignableFrom(superclass)) return

            forTargetEvents(superclass, consumer)
        }
    }
}
