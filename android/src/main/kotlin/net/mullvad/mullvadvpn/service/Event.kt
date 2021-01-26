package net.mullvad.mullvadvpn.service

import android.os.Bundle
import android.os.Message
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class Event : Parcelable {
    @Parcelize
    class ListenerReady : Event(), Parcelable

    val message: Message
        get() = Message.obtain().also { message ->
            message.what = EVENT_MESSAGE
            message.data = Bundle()
            message.data.putParcelable(EVENT_KEY, this)
        }

    companion object {
        const val EVENT_MESSAGE = 1
        const val EVENT_KEY = "event"

        fun fromMessage(message: Message): Event {
            val data = message.data

            return data.getParcelable(EVENT_KEY)!!
        }
    }
}