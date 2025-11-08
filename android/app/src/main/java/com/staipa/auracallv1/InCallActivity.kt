package com.staipa.auracallv1

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Small example InCallActivity that looks up the AuraConnection and provides
 * basic answer / disconnect UI. Replace with your real layout and wiring.
 */
class InCallActivity : Activity() {

    private val TAG = "AuraInCallActivity"

    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.staipa.auracallv1.ACTION_FINISH_INCALL_ACTIVITY") {
                finish()
            }
        }
    }

    private var callKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple UI: text + two buttons. Replace with proper layout.
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tv = TextView(this).apply { text = "Aura Call UI" }
        val btnAnswer = Button(this).apply { text = "Answer" }
        val btnDisconnect = Button(this).apply { text = "Disconnect" }
        layout.addView(tv); layout.addView(btnAnswer); layout.addView(btnDisconnect)
        setContentView(layout)

        callKey = intent?.getStringExtra("CALL_KEY") ?: intent?.getStringExtra("CALL_HANDLE")
        Log.d(TAG, "InCallActivity callKey="+callKey)

        val filter = IntentFilter("com.staipa.auracallv1.ACTION_FINISH_INCALL_ACTIVITY")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(finishReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(finishReceiver, filter)
        }

        btnAnswer.setOnClickListener {
            callKey?.let { key ->
                ConnectionRepository.lookup(key)?.let { conn ->
                    // Trigger the Connection's answer path
                    conn.onAnswer()
                }
            }
        }

        btnDisconnect.setOnClickListener {
            callKey?.let { key ->
                ConnectionRepository.lookup(key)?.let { conn ->
                    conn.onDisconnect()
                    ConnectionRepository.unregister(key)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(finishReceiver) } catch (e: IllegalArgumentException) { /* ignore */ }
    }
}