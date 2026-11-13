package su.nnedition.nodal

/**
 * Represents an element which can have [Event] listeners assigned to it.
 */
interface EventHandler<T : Event> {
    fun eventNode(): EventNode<T>
}
