package net.mullvad.mullvadvpn.service

import android.os.DeadObjectException
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.sendBlocking
import net.mullvad.mullvadvpn.util.Intermittent
import net.mullvad.talpid.ConnectivityListener

class ServiceHandler(
    looper: Looper,
    val intermittentDaemon: Intermittent<MullvadDaemon>,
    connectivityListener: ConnectivityListener
) : Handler(looper) {
    private val listeners = mutableListOf<Messenger>()
    private val registrationQueue = startRegistrator()

    val settingsListener = SettingsListener(intermittentDaemon).apply {
        subscribe(this@ServiceHandler) { settings ->
            sendEvent(Event.SettingsUpdate(settings))
        }
    }

    val locationInfoCache =
        LocationInfoCache(connectivityListener, settingsListener, intermittentDaemon).apply {
            onNewLocation = { location ->
                sendEvent(Event.NewLocation(location))
            }
        }

    override fun handleMessage(message: Message) {
        val request = Request.fromMessage(message)

        when (request) {
            is Request.RegisterListener -> registrationQueue.sendBlocking(message.replyTo)
        }
    }

    fun onDestroy() {
        registrationQueue.close()

        locationInfoCache.onDestroy()
        settingsListener.onDestroy()
    }

    private fun startRegistrator() = GlobalScope.actor<Messenger>(
        Dispatchers.Default,
        Channel.UNLIMITED
    ) {
        try {
            while (true) {
                val listener = channel.receive()

                intermittentDaemon.await()

                registerListener(listener)
            }
        } catch (exception: ClosedReceiveChannelException) {
            // Registration queue closed; stop registrator
        }
    }

    private fun registerListener(listener: Messenger) {
        listeners.add(listener)

        listener.apply {
            send(Event.SettingsUpdate(settingsListener.settings).message)
            send(Event.NewLocation(locationInfoCache.location).message)
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
