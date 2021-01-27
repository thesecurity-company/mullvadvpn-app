package net.mullvad.mullvadvpn.service

import android.os.Bundle
import android.os.Message
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class Request : Parcelable {
    @Parcelize
    class RegisterListener : Request(), Parcelable

    @Parcelize
    class WireGuardGenerateKey : Request(), Parcelable

    @Parcelize
    class WireGuardVerifyKey : Request(), Parcelable

    val message: Message
        get() = Message.obtain().also { message ->
            message.what = REQUEST_MESSAGE
            message.data = Bundle()
            message.data.putParcelable(REQUEST_KEY, this)
        }

    companion object {
        const val REQUEST_MESSAGE = 2
        const val REQUEST_KEY = "request"

        fun fromMessage(message: Message): Request {
            val data = message.data

            return data.getParcelable(REQUEST_KEY)!!
        }
    }
}
