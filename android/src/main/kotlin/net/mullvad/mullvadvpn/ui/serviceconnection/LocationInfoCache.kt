package net.mullvad.mullvadvpn.ui.serviceconnection

class LocationInfoCache(val serviceCache: net.mullvad.mullvadvpn.service.LocationInfoCache) {
    var onNewLocation
        get() = serviceCache.onNewLocation
        set(value) { serviceCache.onNewLocation = value }

    fun onDestroy() {
        onNewLocation = null
    }
}
