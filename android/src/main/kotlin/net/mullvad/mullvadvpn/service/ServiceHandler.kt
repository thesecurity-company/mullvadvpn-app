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
    val connectionProxy: ConnectionProxy,
    connectivityListener: ConnectivityListener,
    val splitTunneling: SplitTunneling
) : Handler(looper) {
    private val listeners = mutableListOf<Messenger>()
    private val registrationQueue = startRegistrator()

    val settingsListener = SettingsListener(intermittentDaemon).apply {
        subscribe(this@ServiceHandler) { settings ->
            sendEvent(Event.SettingsUpdate(settings))
        }
    }

    val accountCache = AccountCache(settingsListener, intermittentDaemon).apply {
        onAccountHistoryChange.subscribe(this@ServiceHandler) { history ->
            sendEvent(Event.AccountHistory(history))
        }

        onLoginStatusChange.subscribe(this@ServiceHandler) { status ->
            sendEvent(Event.LoginStatus(status))
        }
    }

    val keyStatusListener = KeyStatusListener(intermittentDaemon).apply {
        onKeyStatusChange.subscribe(this@ServiceHandler) { keyStatus ->
            sendEvent(Event.WireGuardKeyStatus(keyStatus))
        }
    }

    val locationInfoCache =
        LocationInfoCache(connectivityListener, settingsListener, intermittentDaemon).apply {
            stateEvents = connectionProxy.onStateChange

            onNewLocation = { location ->
                sendEvent(Event.NewLocation(location))
            }
        }

    init {
        connectionProxy.onStateChange.subscribe(this) { tunnelState ->
            sendEvent(Event.TunnelStateChange(tunnelState))
        }

        splitTunneling.onChange.subscribe(this) { excludedApps ->
            sendEvent(Event.SplitTunnelingUpdate(excludedApps))
        }
    }

    override fun handleMessage(message: Message) {
        val request = Request.fromMessage(message)

        when (request) {
            is Request.CreateAccount -> accountCache.createNewAccount()
            is Request.ExcludeApp -> {
                request.packageName?.let { packageName ->
                    splitTunneling.excludeApp(packageName)
                }
            }
            is Request.FetchAccountExpiry -> accountCache.fetchAccountExpiry()
            is Request.IncludeApp -> {
                request.packageName?.let { packageName ->
                    splitTunneling.includeApp(packageName)
                }
            }
            is Request.InvalidateAccountExpiry -> {
                accountCache.invalidateAccountExpiry(request.expiry)
            }
            is Request.Login -> request.account?.let { account -> accountCache.login(account) }
            is Request.Logout -> accountCache.logout()
            is Request.PersistExcludedApps -> splitTunneling.persist()
            is Request.RegisterListener -> registrationQueue.sendBlocking(message.replyTo)
            is Request.RemoveAccountFromHistory -> {
                request.account?.let { account ->
                    accountCache.removeAccountFromHistory(account)
                }
            }
            is Request.SetEnableSplitTunneling -> splitTunneling.enabled = request.enable
            is Request.WireGuardGenerateKey -> keyStatusListener.generateKey()
            is Request.WireGuardVerifyKey -> keyStatusListener.verifyKey()
        }
    }

    fun onDestroy() {
        registrationQueue.close()

        accountCache.onDestroy()
        keyStatusListener.onDestroy()
        locationInfoCache.onDestroy()
        settingsListener.onDestroy()

        connectionProxy.onStateChange.unsubscribe(this)
        splitTunneling.onChange.unsubscribe(this)
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
            send(Event.LoginStatus(accountCache.onLoginStatusChange.latestEvent).message)
            send(Event.AccountHistory(accountCache.onAccountHistoryChange.latestEvent).message)
            send(Event.SettingsUpdate(settingsListener.settings).message)
            send(Event.NewLocation(locationInfoCache.location).message)
            send(Event.WireGuardKeyStatus(keyStatusListener.keyStatus).message)
            send(Event.SplitTunnelingUpdate(splitTunneling.onChange.latestEvent).message)
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
