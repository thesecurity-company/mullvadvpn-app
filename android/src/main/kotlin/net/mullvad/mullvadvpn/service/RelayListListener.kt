package net.mullvad.mullvadvpn.service

import kotlin.properties.Delegates.observable
import net.mullvad.mullvadvpn.model.RelayList
import net.mullvad.mullvadvpn.util.Intermittent
import net.mullvad.talpid.util.EventNotifier

class RelayListListener(val daemon: Intermittent<MullvadDaemon>) {
    val relayListNotifier = EventNotifier<RelayList?>(null)

    var relayList by relayListNotifier.notifiable()
        private set

    init {
        daemon.registerListener(this) { newDaemon ->
            newDaemon?.let { daemon ->
                setUpListener(daemon)
                fetchInitialRelayList(daemon)
            }
        }
    }

    fun onDestroy() {
        relayListNotifier.unsubscribeAll()
        daemon.unregisterListener(this)
    }

    private fun setUpListener(daemon: MullvadDaemon) {
        daemon.onRelayListChange = { relayLocations ->
            relayList = relayLocations
        }
    }

    private fun fetchInitialRelayList(daemon: MullvadDaemon) {
        synchronized(this) {
            if (relayList == null) {
                relayList = daemon.getRelayLocations()
            }
        }
    }
}
