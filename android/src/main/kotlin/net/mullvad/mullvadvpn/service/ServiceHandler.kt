package net.mullvad.mullvadvpn.service

import android.os.Handler
import android.os.Looper
import android.os.Message
import net.mullvad.mullvadvpn.util.Intermittent

class ServiceHandler(
    looper: Looper,
    intermittentDaemon: Intermittent<MullvadDaemon>
) : Handler(looper) {
    val settingsListener = SettingsListener(intermittentDaemon)

    override fun handleMessage(message: Message) {}

    fun onDestroy() {
        settingsListener.onDestroy()
    }
}
