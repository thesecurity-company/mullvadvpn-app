package net.mullvad.mullvadvpn.service

import android.os.DeadObjectException
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import net.mullvad.mullvadvpn.util.Intermittent

class ServiceHandler(
    looper: Looper,
    intermittentDaemon: Intermittent<MullvadDaemon>
) : Handler(looper) {
    private val listeners = mutableListOf<Messenger>()

    val settingsListener = SettingsListener(intermittentDaemon)

    override fun handleMessage(message: Message) {
        val request = Request.fromMessage(message)

        when (request) {
            is Request.RegisterListener -> registerListener(message.replyTo)
        }
    }

    fun onDestroy() {
        settingsListener.onDestroy()
    }

    private fun registerListener(listener: Messenger) {
        listeners.add(listener)

        listener.apply {
            send(Event.ListenerReady().message)
        }
    }

    private fun sendEvent(event: Event) {
        val deadListeners = mutableListOf<Messenger>()

        for (listener in listeners) {
            try {
                listener.send(event.message)
            } catch (_: DeadObjectException) {
                deadListeners.add(listener)
            }
        }

        for (deadListener in deadListeners) {
            listeners.remove(deadListener)
        }
    }
}
