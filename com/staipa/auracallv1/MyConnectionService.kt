// File: com/staipa.auracallv1/MyConnectionService.kt
package com.staipa.auracallv1

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.DisconnectCause
import android.util.Log

class MyConnectionService : ConnectionService() {

    private val TAG = "AuraConnectionService"
    private val PHONE_ACCOUNT_ID = "com.staipa.auracallv1.AURA_PHONE_ACCOUNT"
    private val connections: MutableMap<String, Connection> = mutableMapOf()

    override fun onCreate() {
        super.onCreate()
        registerPhoneAccountIfNeeded()
    }

    private fun registerPhoneAccountIfNeeded() {
        try {
            val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val componentName = ComponentName(this, MyConnectionService::class.java)
            val handle = PhoneAccountHandle(componentName, PHONE_ACCOUNT_ID)
            val phoneAccount = PhoneAccount.builder(handle, "Aura")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build()

            tm.registerPhoneAccount(phoneAccount)
            Log.d(TAG, "Registered PhoneAccount: $PHONE_ACCOUNT_ID")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register PhoneAccount: ${e.message}")
        }
    }

    private fun storeConnection(key: String?, connection: Connection?) {
        if (key == null || connection == null) return
        connections[key] = connection
    }

    private fun removeConnection(key: String?) {
        if (key == null) return
        connections.remove(key)
    }

    override fun onCreateOutgoingConnection(
        phoneAccountHandle: PhoneAccountHandle?,
        connectionRequest: ConnectionRequest
    ): Connection? {
        Log.d(TAG, "Creating outgoing connection for: ${connectionRequest.address} using account: ${phoneAccountHandle?.id}")

        // Create and return an AuraConnection to manage lifecycle of this call.
        val conn = AuraConnection()
        try {
            connectionRequest.address?.let { uri ->
                conn.setAddress(uri, TelecomManager.PRESENTATION_ALLOWED)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set address on connection: ${e.message}")
        }

        // Initialize and set dialing state
        conn.setInitializing()
        conn.setDialing()

        // Store by request id (fallback to address string if null)
        val key = connectionRequest.extras?.getString("CALL_ID") ?: connectionRequest.address?.schemeSpecificPart ?: System.currentTimeMillis().toString()
        storeConnection(key, conn)

        return conn
    }

    override fun onCreateIncomingConnection(
        phoneAccountHandle: PhoneAccountHandle?,
        connectionRequest: ConnectionRequest
    ): Connection? {
        Log.d(TAG, "Creating incoming connection for: ${connectionRequest.address} using account: ${phoneAccountHandle?.id}")

        val conn = AuraConnection()
        try {
            connectionRequest.address?.let { uri ->
                conn.setAddress(uri, TelecomManager.PRESENTATION_ALLOWED)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set address on connection: ${e.message}")
        }

        conn.setInitializing()
        conn.setRinging()

        val key = connectionRequest.extras?.getString("CALL_ID") ?: connectionRequest.address?.schemeSpecificPart ?: System.currentTimeMillis().toString()
        storeConnection(key, conn)

        return conn
    }

    override fun onCreateOutgoingConnectionFailed(phoneAccountHandle: PhoneAccountHandle?, connectionRequest: ConnectionRequest): Connection? {
        Log.w(TAG, "Outgoing connection failed for: ${connectionRequest.address}")
        return super.onCreateOutgoingConnectionFailed(phoneAccountHandle, connectionRequest)
    }

    override fun onCreateIncomingConnectionFailed(phoneAccountHandle: PhoneAccountHandle?, connectionRequest: ConnectionRequest): Connection? {
        Log.w(TAG, "Incoming connection failed for: ${connectionRequest.address}")
        return super.onCreateIncomingConnectionFailed(phoneAccountHandle, connectionRequest)
    }

    // Optional: provide a method to lookup and disconnect stored connections
    fun disconnectCallById(callId: String) {
        connections[callId]?.let { conn ->
            conn.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            conn.destroy()
            removeConnection(callId)
        }
    }
}