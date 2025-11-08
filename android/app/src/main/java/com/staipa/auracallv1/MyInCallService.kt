package com.staipa.auracallv1

import android.content.Intent
import android.net.Uri
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

/**
 * Updated InCallService: when a Call is added, try to find the matching AuraConnection
 * via the Call handle (URI) or call extras (CALL_ID). Attach UI and register a
 * lightweight callback to keep UI in sync.
 */
class MyInCallService : InCallService() {

    private val TAG = "AuraInCallService"

    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        Log.d(TAG, "Call Added. State: ${call?.state}")

        // Best-effort: try to match the Call to a Connection using a key.
        val handle: Uri? = try { call?.details?.handle } catch (e: Exception) { null }
        val callKey = when {
            call?.details?.extras?.getString("CALL_ID") != null -> call.details.extras?.getString("CALL_ID")
            handle != null -> handle.schemeSpecificPart
            else -> null
        }

        // Start UI
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            callKey?.let { putExtra("CALL_KEY", it) }
            handle?.let { putExtra("CALL_HANDLE", it.toString()) }
        }
        startActivity(intent)

        // Optional: register a callback on the Call to forward state changes to the UI via broadcasts
        call?.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call?, newState: Int) {
                super.onStateChanged(c, newState)
                // You can broadcast or use another mechanism to update the UI
                Log.d(TAG, "Call state changed: $newState")
            }

            override fun onDisconnected(c: Call?, details: Call.Details?) {
                super.onDisconnected(c, details)
                // Tell the UI to finish if needed
                val finish = Intent("com.staipa.auracallv1.ACTION_FINISH_INCALL_ACTIVITY")
                sendBroadcast(finish)
            }
        })
    }

    override fun onCallRemoved(call: Call?) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call Removed. State: ${call?.state}")
    }
}