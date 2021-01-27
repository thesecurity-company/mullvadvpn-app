package net.mullvad.mullvadvpn.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.launch
import net.mullvad.mullvadvpn.model.TunnelState
import net.mullvad.mullvadvpn.ui.MainActivity
import net.mullvad.mullvadvpn.util.Intermittent
import net.mullvad.talpid.util.EventNotifier

class ConnectionProxy(val context: Context, val daemon: Intermittent<MullvadDaemon>) {
    private enum class Command {
        CONNECT,
        RECONNECT,
        DISCONNECT,
    }

    private val commandChannel = spawnActor()
    private val initialState = TunnelState.Disconnected()

    val vpnPermission = Intermittent<Boolean>()

    var onStateChange = EventNotifier<TunnelState>(initialState)

    var state by onStateChange.notifiable()
        private set

    private val fetchInitialStateJob = fetchInitialState()

    init {
        daemon.registerListener(this) { newDaemon ->
            newDaemon?.onTunnelStateChange = { newState -> state = newState }
        }
    }

    fun connect() {
        commandChannel.sendBlocking(Command.CONNECT)
    }

    fun reconnect() {
        commandChannel.sendBlocking(Command.RECONNECT)
    }

    fun disconnect() {
        commandChannel.sendBlocking(Command.DISCONNECT)
    }

    fun onDestroy() {
        commandChannel.close()
        fetchInitialStateJob.cancel()
        onStateChange.unsubscribeAll()
        daemon.unregisterListener(this)
    }

    private fun spawnActor() = GlobalScope.actor<Command>(Dispatchers.Default, Channel.UNLIMITED) {
        try {
            while (true) {
                val command = channel.receive()

                when (command) {
                    Command.CONNECT -> {
                        requestVpnPermission()
                        vpnPermission.await()
                        daemon.await().connect()
                    }
                    Command.RECONNECT -> daemon.await().reconnect()
                    Command.DISCONNECT -> daemon.await().disconnect()
                }
            }
        } catch (exception: ClosedReceiveChannelException) {
            // Closed sender, so stop the actor
        }
    }

    private suspend fun requestVpnPermission() {
        val intent = VpnService.prepare(context)

        vpnPermission.update(null)

        if (intent == null) {
            vpnPermission.update(true)
        } else {
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.KEY_SHOULD_CONNECT, true)
            }

            context.startActivity(activityIntent)
        }
    }

    private fun fetchInitialState() = GlobalScope.launch(Dispatchers.Default) {
        val currentState = daemon.await().getState()

        synchronized(this) {
            if (state === initialState && currentState != null) {
                state = currentState
            }
        }
    }
}
