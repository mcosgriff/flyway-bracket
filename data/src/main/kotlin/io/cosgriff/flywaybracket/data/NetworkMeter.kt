package io.cosgriff.flywaybracket.data

import android.content.Context
import android.net.ConnectivityManager

/** Whether the phone's connection may cost the player for data (#39). */
fun interface NetworkMeter {
    /** True on a metered connection, such as mobile data or a hotspot, and when there is none. */
    fun isMetered(): Boolean
}

/**
 * Android's answer, from `ConnectivityManager`. It needs `ACCESS_NETWORK_STATE`, granted at install: the
 * player is never asked. Android has no other way for an app to tell whether a download may cost money.
 */
class AndroidNetworkMeter(private val context: Context) : NetworkMeter {
    override fun isMetered(): Boolean = context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
}
