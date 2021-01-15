package net.mullvad.mullvadvpn.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.sendBlocking
import net.mullvad.mullvadvpn.model.VoucherSubmissionResult
import net.mullvad.mullvadvpn.util.Intermittent

class VoucherRedeemer(
    val daemon: Intermittent<MullvadDaemon>,
    val onResult: (String, VoucherSubmissionResult) -> Unit
) {
    private val voucherChannel = spawnActor()

    fun submit(voucher: String) {
        voucherChannel.sendBlocking(voucher)
    }

    fun onDestroy() {
        voucherChannel.close()
    }

    private fun spawnActor() = GlobalScope.actor<String>(Dispatchers.Default, Channel.UNLIMITED) {
        try {
            val voucher = channel.receive()
            val result = daemon.await().submitVoucher(voucher)

            onResult(voucher, result)
        } catch (exception: ClosedReceiveChannelException) {
            // Voucher channel was closed, stop the actor
        }
    }
}
