// File: com/staipa/auracallv1/MyInCallService.kt
package com.staipa.auracallv1

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class MyInCallService : InCallService() {

    private val TAG = "AuraInCallService"

    // The system calls this when a new call (incoming or outgoing) is added.
    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        Log.d(TAG, "Call Added. State: ${call?.state}")
        
        // --- KEY STEP: Launch your custom InCallActivity ---
        val intent = Intent(this, InCallActivity::class.java).apply {
            // Flags are important to launch a UI from a Service context
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        
        // In a real app, you would also pass Call details (e.g., call ID) 
        // to the InCallActivity here.
    }

    // The system calls this when a call is disconnected/removed.
    override fun onCallRemoved(call: Call?) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call Removed. State: ${call?.state}")
        
        // If all calls are gone, dismiss the InCallActivity
        if (getCalls().isEmpty()) {
            // A simple way to signal to the activity to finish
            val intent = Intent("com.staipa.auracallv1.ACTION_FINISH_INCALL_ACTIVITY")
            sendBroadcast(intent)
        }
    }
}
