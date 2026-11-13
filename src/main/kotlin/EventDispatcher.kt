package su.nnedition.nodal

import su.nnedition.nodal.trait.CancellableEvent

object EventDispatcher {
    /**
     * Base global event handler.
     */
    var globalHandler: GlobalEventHandler = GlobalEventHandler()

    fun call(event: Event) {
        globalHandler.call(event)
    }

    fun callCancellable(event: CancellableEvent, successCallback: Runnable) {
        globalHandler.callCancellable(event, successCallback)
    }
    inline fun callCancellable(event: CancellableEvent, crossinline successCallback: () -> Unit) {
        callCancellable(event, Runnable { successCallback() })
    }

    fun <E : Event> getHandle(handleType: Class<E>): ListenerHandle<E> {
        return globalHandler.getHandle(handleType)
    }
    inline fun <reified E : Event> getHandle(): ListenerHandle<E> {
        return getHandle(E::class.java)
    }
}