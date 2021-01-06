package net.mullvad.mullvadvpn.service

import net.mullvad.mullvadvpn.model.AppVersionInfo
import net.mullvad.mullvadvpn.util.Intermittent
import net.mullvad.talpid.util.EventNotifier

class AppVersionInfoCache(val intermittentDaemon: Intermittent<MullvadDaemon>) {
    val appVersionInfoNotifier = EventNotifier<AppVersionInfo?>(null)
    val currentVersionNotifier = EventNotifier<String?>(null)

    var appVersionInfo by appVersionInfoNotifier.notifiable()
        private set
    var currentVersion by currentVersionNotifier.notifiable()
        private set

    init {
        intermittentDaemon.registerListener(this) { newDaemon ->
            if (currentVersion == null && newDaemon != null) {
                currentVersion = newDaemon.getCurrentVersion()
            }

            newDaemon?.onAppVersionInfoChange = { newAppVersionInfo ->
                synchronized(this@AppVersionInfoCache) {
                    appVersionInfo = newAppVersionInfo
                }
            }

            // Load initial version info
            synchronized(this@AppVersionInfoCache) {
                if (appVersionInfo == null && newDaemon != null) {
                    appVersionInfo = newDaemon.getVersionInfo()
                }
            }
        }
    }

    fun onDestroy() {
        intermittentDaemon.unregisterListener(this)

        appVersionInfoNotifier.unsubscribeAll()
        currentVersionNotifier.unsubscribeAll()
    }
}
