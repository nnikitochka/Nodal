package su.nnedition.nodal

import su.nnedition.nodal.trait.CancellableEvent

object EventDispatcher {
    /**
     * Base global event handler.
     */
    @JvmStatic
    var globalHandler: GlobalEventHandler = GlobalEventHandler()

    @JvmStatic
    fun call(event: Event) {
        globalHandler.call(event)
    }

    @JvmStatic
    fun callCancellable(event: CancellableEvent, successCallback: Runnable) {
        globalHandler.callCancellable(event, successCallback)
    }

    inline fun callCancellable(event: CancellableEvent, crossinline successCallback: () -> Unit) {
        callCancellable(event, Runnable { successCallback() })
    }

    @JvmStatic
    fun <E : Event> getHandle(handleType: Class<E>): ListenerHandle<E> {
        return globalHandler.getHandle(handleType)
    }

    inline fun <reified E : Event> getHandle(): ListenerHandle<E> {
        return getHandle(E::class.java)
    }
}
