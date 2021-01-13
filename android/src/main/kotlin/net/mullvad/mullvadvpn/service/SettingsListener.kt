package net.mullvad.mullvadvpn.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.sendBlocking
import net.mullvad.mullvadvpn.model.DnsOptions
import net.mullvad.mullvadvpn.model.RelaySettings
import net.mullvad.mullvadvpn.model.Settings
import net.mullvad.mullvadvpn.util.Intermittent
import net.mullvad.talpid.util.EventNotifier

class SettingsListener(val daemon: Intermittent<MullvadDaemon>) {
    private sealed class Command {
        class SetAllowLan(val allow: Boolean) : Command()
        class SetWireGuardMtu(val mtu: Int?) : Command()
    }

    private val commandChannel = spawnActor()

    val accountNumberNotifier = EventNotifier<String?>(null)
    val dnsOptionsNotifier = EventNotifier<DnsOptions?>(null)
    val relaySettingsNotifier = EventNotifier<RelaySettings?>(null)
    val settingsNotifier = EventNotifier<Settings?>(null)

    var settings by settingsNotifier.notifiable()
        private set

    var allowLan: Boolean
        get() = settings?.allowLan ?: false
        set(value) = commandChannel.sendBlocking(Command.SetAllowLan(value))

    var wireguardMtu: Int?
        get() = settings?.tunnelOptions?.wireguard?.mtu
        set(value) = commandChannel.sendBlocking(Command.SetWireGuardMtu(value))

    init {
        daemon.registerListener(this) { maybeNewDaemon ->
            maybeNewDaemon?.let { newDaemon ->
                newDaemon.onSettingsChange.subscribe(this@SettingsListener) { maybeSettings ->
                    synchronized(this@SettingsListener) {
                        maybeSettings?.let { newSettings -> handleNewSettings(newSettings) }
                    }
                }

                synchronized(this@SettingsListener) {
                    newDaemon.getSettings()?.let { newSettings ->
                        handleNewSettings(newSettings)
                    }
                }
            }
        }
    }

    fun onDestroy() {
        commandChannel.close()
        daemon.unregisterListener(this)

        accountNumberNotifier.unsubscribeAll()
        dnsOptionsNotifier.unsubscribeAll()
        relaySettingsNotifier.unsubscribeAll()
        settingsNotifier.unsubscribeAll()
    }

    fun subscribe(id: Any, listener: (Settings) -> Unit) {
        settingsNotifier.subscribe(id) { maybeSettings ->
            maybeSettings?.let { settings ->
                listener(settings)
            }
        }
    }

    fun unsubscribe(id: Any) {
        settingsNotifier.unsubscribe(id)
    }

    private fun handleNewSettings(newSettings: Settings) {
        synchronized(this) {
            if (settings?.accountToken != newSettings.accountToken) {
                accountNumberNotifier.notify(newSettings.accountToken)
            }

            if (settings?.tunnelOptions?.dnsOptions != newSettings.tunnelOptions.dnsOptions) {
                dnsOptionsNotifier.notify(newSettings.tunnelOptions.dnsOptions)
            }

            if (settings?.relaySettings != newSettings.relaySettings) {
                relaySettingsNotifier.notify(newSettings.relaySettings)
            }

            settings = newSettings
        }
    }

    private fun spawnActor() = GlobalScope.actor<Command>(Dispatchers.Default, Channel.UNLIMITED) {
        try {
            while (true) {
                val command = channel.receive()

                when (command) {
                    is Command.SetAllowLan -> daemon.await().setAllowLan(command.allow)
                    is Command.SetWireGuardMtu -> daemon.await().setWireguardMtu(command.mtu)
                }
            }
        } catch (exception: ClosedReceiveChannelException) {
            // Closed sender, so stop the actor
        }
    }
}
