// File: com/staipa/auracallv1/MyConnectionService.kt
package com.staipa.auracallv1

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.util.Log

class MyConnectionService : ConnectionService() {

    private val TAG = "AuraConnectionService"

    /**
     * ✅ CORRECTED: Primary override for creating a new OUTGOING call.
     * Must include the PhoneAccountHandle parameter.
     */
    override fun onCreateOutgoingConnection(
        // The system passes the PhoneAccountHandle to identify which SIM/account to use
        phoneAccountHandle: PhoneAccountHandle?, 
        connectionRequest: ConnectionRequest
    ): Connection? {
        Log.d(TAG, "Creating outgoing connection for: ${connectionRequest.address} using account: ${phoneAccountHandle?.id}")

        // Since you are building a native dialer, you can return 'null' here.
        // Returning null allows the Telecom framework to fall back to the native
        // telephony stack to establish the connection, while your InCallService
        // manages the UI.
        return null 
    }

    /**
     * ✅ CORRECTED: Primary override for handling a new INCOMING call.
     * Must include the PhoneAccountHandle parameter.
     */
    override fun onCreateIncomingConnection(
        phoneAccountHandle: PhoneAccountHandle?, 
        connectionRequest: ConnectionRequest
    ): Connection? {
        Log.d(TAG, "Creating incoming connection for: ${connectionRequest.address} using account: ${phoneAccountHandle?.id}")
        
        // Similar to outgoing, for a native dialer, let the system handle connection establishment.
        return null 
    }
    
    // NOTE: The previous two methods that only took 'ConnectionRequest' are no longer needed
    // nor supported as direct overrides on modern APIs and have been removed, resolving the errors.
}
