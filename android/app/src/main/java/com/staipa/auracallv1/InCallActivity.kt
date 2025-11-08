// File: com/staipa/auracallv1/InCallActivity.kt
package com.staipa.auracallv1

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView

class InCallActivity : Activity() {

    private val TAG = "AuraInCallActivity"

    // Broadcast receiver to listen for a signal from MyInCallService to close
    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.staipa.auracallv1.ACTION_FINISH_INCALL_ACTIVITY") {
                Log.d(TAG, "Received finish signal. Finishing activity.")
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // You would replace this with your actual layout file: R.layout.activity_incall
        setContentView(TextView(this).apply { text = "Aura Call in Progress..." }) 
        
        Log.d(TAG, "InCallActivity created.")

        // Register the broadcast receiver
        val filter = IntentFilter("com.staipa.auracallv1.ACTION_FINISH_INCALL_ACTIVITY")
        registerReceiver(finishReceiver, filter, RECEIVER_EXPORTED) // Use correct flag for security
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(finishReceiver)
        Log.d(TAG, "InCallActivity destroyed.")
    }
    
    // You would add methods here to interact with the Call object via MyInCallService
    // For example, finding the current active Call and calling call.disconnect()
}
