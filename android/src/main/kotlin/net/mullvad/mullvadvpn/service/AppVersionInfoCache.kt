package net.mullvad.mullvadvpn.service

import android.content.Context
import net.mullvad.mullvadvpn.model.AppVersionInfo
import net.mullvad.mullvadvpn.util.Intermittent
import net.mullvad.talpid.util.EventNotifier

class AppVersionInfoCache(context: Context, val intermittentDaemon: Intermittent<MullvadDaemon>) {
    companion object {
        val LEGACY_SHARED_PREFERENCES = "app_version_info_cache"
    }

    val appVersionInfoNotifier = EventNotifier<AppVersionInfo?>(null)
    val currentVersionNotifier = EventNotifier<String?>(null)

    var appVersionInfo by appVersionInfoNotifier.notifiable()
        private set
    var currentVersion by currentVersionNotifier.notifiable()
        private set

    init {
        context.getSharedPreferences(LEGACY_SHARED_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        intermittentDaemon.registerListener(this) { newDaemon ->
            newDaemon?.let { daemon ->
                initializeCurrentVersion(daemon)
                registerVersionInfoListener(daemon)
                fetchInitialVersionInfo(daemon)
            }
        }
    }

    fun onDestroy() {
        intermittentDaemon.unregisterListener(this)

        appVersionInfoNotifier.unsubscribeAll()
        currentVersionNotifier.unsubscribeAll()
    }

    private fun initializeCurrentVersion(daemon: MullvadDaemon) {
        if (currentVersion == null) {
            currentVersion = daemon.getCurrentVersion()
        }
    }

    private fun registerVersionInfoListener(daemon: MullvadDaemon) {
        daemon.onAppVersionInfoChange = { newAppVersionInfo ->
            synchronized(this@AppVersionInfoCache) {
                appVersionInfo = newAppVersionInfo
            }
        }
    }

    private fun fetchInitialVersionInfo(daemon: MullvadDaemon) {
        synchronized(this@AppVersionInfoCache) {
            if (appVersionInfo == null) {
                appVersionInfo = daemon.getVersionInfo()
            }
        }
    }
}
