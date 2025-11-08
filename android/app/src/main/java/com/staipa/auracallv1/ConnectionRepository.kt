package com.staipa.auracallv1

import android.telecom.Connection
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-process repository so the InCall UI and other components can find
 * the Connection object created by MyConnectionService.
 *
 * NOTE: This is an in-process helper. It assumes your ConnectionService, InCallService
 * and Activity run in the same process (default for most apps). If you change process
 * configuration, you'll need an IPC mechanism instead.
 */
object ConnectionRepository {
    private val TAG = "ConnectionRepository"
    private val map = ConcurrentHashMap<String, Connection>()

    fun register(key: String, connection: Connection) {
        if (key.isEmpty()) return
        map[key] = connection
        Log.d(TAG, "Registered connection for key=$key")
    }

    fun lookup(key: String): Connection? {
        return map[key]
    }

    fun unregister(key: String?) {
        if (key == null) return
        map.remove(key)
        Log.d(TAG, "Unregistered connection for key=$key")
    }

    fun clearAll() {
        map.clear()
        Log.d(TAG, "Cleared all connections")
    }
}