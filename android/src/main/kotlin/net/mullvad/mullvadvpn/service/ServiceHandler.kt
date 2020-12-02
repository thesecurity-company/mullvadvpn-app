package net.mullvad.mullvadvpn.service

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
            is Request.RegisterListener -> listeners.add(message.replyTo)
        }
    }

    fun onDestroy() {
        settingsListener.onDestroy()
    }
}
