package net.mullvad.mullvadvpn.ui

import android.os.Looper
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import net.mullvad.mullvadvpn.dataproxy.AppVersionInfoCache
import net.mullvad.mullvadvpn.dataproxy.RelayListListener
import net.mullvad.mullvadvpn.service.Request
import net.mullvad.mullvadvpn.service.ServiceInstance
import net.mullvad.mullvadvpn.ui.serviceconnection.EventDispatcher

class ServiceConnection(private val service: ServiceInstance, val mainActivity: MainActivity) {
    val dispatcher = EventDispatcher(Looper.getMainLooper())

    val daemon = service.daemon
    val accountCache = service.accountCache
    val connectionProxy = service.connectionProxy
    val customDns = service.customDns
    val keyStatusListener = service.keyStatusListener
    val locationInfoCache = service.locationInfoCache
    val settingsListener = service.settingsListener
    val splitTunneling = service.splitTunneling

    val appVersionInfoCache = AppVersionInfoCache(mainActivity, daemon, settingsListener)
    var relayListListener = RelayListListener(daemon, settingsListener)

    init {
        appVersionInfoCache.onCreate()
        connectionProxy.mainActivity = mainActivity
        registerListener()
    }

    fun onDestroy() {
        dispatcher.onDestroy()

        appVersionInfoCache.onDestroy()
        relayListListener.onDestroy()
        connectionProxy.mainActivity = null
    }

    private fun registerListener() {
        val message = Request.RegisterListener().message.apply {
            replyTo = Messenger(dispatcher)
        }

        try {
            service.messenger.send(message)
        } catch (exception: RemoteException) {
            Log.e("mullvad", "Failed to register listener for service events", exception)
        }
    }
}
