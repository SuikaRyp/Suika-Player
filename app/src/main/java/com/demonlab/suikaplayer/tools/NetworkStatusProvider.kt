package com.demonlab.suikaplayer.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tracks whether the device currently has usable internet access (Wi-Fi or mobile data).
 * Used to decide whether the home screen should show the online song library
 * (from SuikaRyp's remote database) or fall back to the offline, on-device library.
 */
class NetworkStatusProvider private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    var isOnline by mutableStateOf(checkCurrentlyOnline())
        private set

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isOnline = checkCurrentlyOnline()
        }

        override fun onLost(network: Network) {
            isOnline = checkCurrentlyOnline()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            isOnline = checkCurrentlyOnline()
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkCurrentlyOnline(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        } catch (e: Exception) {
            false
        }
    }

    fun refresh() {
        isOnline = checkCurrentlyOnline()
    }

    companion object {
        @Volatile
        private var instance: NetworkStatusProvider? = null

        fun getInstance(context: Context): NetworkStatusProvider {
            return instance ?: synchronized(this) {
                instance ?: NetworkStatusProvider(context.applicationContext).also { instance = it }
            }
        }
    }
}
