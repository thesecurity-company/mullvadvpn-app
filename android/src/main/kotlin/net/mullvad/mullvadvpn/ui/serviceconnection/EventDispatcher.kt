package net.mullvad.mullvadvpn.ui.serviceconnection

import android.os.Handler
import android.os.Looper
import android.os.Message
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.reflect.KClass
import net.mullvad.mullvadvpn.service.Event

class EventDispatcher(looper: Looper) : Handler(looper) {
    private val handlers = HashMap<KClass<out Event>, (Event) -> Unit>()
    private val lock = ReentrantReadWriteLock()

    fun <E : Event> registerHandler(eventVariant: KClass<E>, handler: (E) -> Unit) {
        lock.writeLock().withLock {
            handlers.put(eventVariant) { event ->
                @Suppress("UNCHECKED_CAST")
                handler(event as E)
            }
        }
    }

    override fun handleMessage(message: Message) {
        lock.readLock().withLock {
            val event = Event.fromMessage(message)
            val handler = handlers.get(event::class)

            handler?.invoke(event)
        }
    }

    fun onDestroy() {
        lock.writeLock().withLock {
            handlers.clear()
        }
    }
}
