// NOTE: this is an updated version (showing integration points with ConnectionRepository and AuraConnection).
package com.staipa.auracallv1

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

class MyConnectionService : ConnectionService() {

    private val TAG = "AuraConnectionService"
    private val PHONE_ACCOUNT_ID = "com.staipa.auracallv1.AURA_PHONE_ACCOUNT"

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

    override fun onCreateOutgoingConnection(
        phoneAccountHandle: PhoneAccountHandle?,
        connectionRequest: ConnectionRequest
    ): Connection? {
        Log.d(TAG, "Creating outgoing connection for: ${connectionRequest.address} using account: ${phoneAccountHandle?.id}")

        val conn = AuraConnection()
        try {
            connectionRequest.address?.let { uri: Uri ->
                conn.setAddress(uri, TelecomManager.PRESENTATION_ALLOWED)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set address on connection: ${e.message}")
        }

        conn.setInitializing()
        conn.setDialing()

        // Create a stable key for lookups: prefer explicit CALL_ID in extras, otherwise use handle
        val key = connectionRequest.extras?.getString("CALL_ID")
            ?: connectionRequest.address?.schemeSpecificPart
            ?: System.currentTimeMillis().toString()

        // register both in a local repository (so UI / InCallService can lookup) and keep for cleanup
        ConnectionRepository.register(key, conn)

        return conn
    }

    override fun onCreateIncomingConnection(
        phoneAccountHandle: PhoneAccountHandle?,
        connectionRequest: ConnectionRequest
    ): Connection? {
        Log.d(TAG, "Creating incoming connection for: ${connectionRequest.address} using account: ${phoneAccountHandle?.id}")

        val conn = AuraConnection()
        try {
            connectionRequest.address?.let { uri: Uri ->
                conn.setAddress(uri, TelecomManager.PRESENTATION_ALLOWED)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set address on connection: ${e.message}")
        }

        conn.setInitializing()
        conn.setRinging()

        val key = connectionRequest.extras?.getString("CALL_ID")
            ?: connectionRequest.address?.schemeSpecificPart
            ?: System.currentTimeMillis().toString()

        ConnectionRepository.register(key, conn)

        return conn
    }

    // Optional helper exposed to other components for teardown
    fun disconnectCallByKey(callKey: String) {
        ConnectionRepository.lookup(callKey)?.let { conn ->
            try {
                conn.setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
                conn.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting connection: ${e.message}")
            } finally {
                ConnectionRepository.unregister(callKey)
            }
        }
    }
}