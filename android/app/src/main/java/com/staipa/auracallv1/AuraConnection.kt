// File: com/staipa.auracallv1/AuraConnection.kt
package com.staipa.auracallv1

import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.RttTextStream
import android.util.Log

/**
 * Lightweight Connection subclass implementing essential lifecycle hooks.
 * This implementation is intentionally simple: it models the local lifecycle
 * (initializing -> dialing/ringing -> active -> disconnected) and exposes
 * methods to trigger hold/unhold/answer/disconnect. Extend for media/audio handling.
 */
class AuraConnection : Connection() {

    private val TAG = "AuraConnection"

    init {
        Log.d(TAG, "AuraConnection created")
        // Default capabilities: allow hold, mute and disconnect. Adjust as needed.
        setConnectionCapabilities(
            Connection.CAPABILITY_HOLD or
            Connection.CAPABILITY_MUTE or
            Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL // example capability
        )
    }

    override fun onAnswer() {
        super.onAnswer()
        Log.d(TAG, "onAnswer called")
        // The app should attach audio and start media for the call here.
        // For now, mark the connection as active.
        setActive()
    }

    override fun onReject() {
        super.onReject()
        Log.d(TAG, "onReject called")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    override fun onDisconnect() {
        super.onDisconnect()
        Log.d(TAG, "onDisconnect called")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    override fun onHold() {
        super.onHold()
        Log.d(TAG, "onHold called")
        setOnHold()
        // Pause audio streams here
    }

    override fun onUnhold() {
        super.onUnhold()
        Log.d(TAG, "onUnhold called")
        setActive()
        // Resume audio streams here
    }

    override fun onAbort() {
        super.onAbort()
        Log.d(TAG, "onAbort called")
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        destroy()
    }

    override fun onPlayDtmfTone(c: Char) {
        super.onPlayDtmfTone(c)
        Log.d(TAG, "DTMF tone: $c")
        // play DTMF locally or signal to remote
    }

    override fun onStopDtmfTone() {
        super.onStopDtmfTone()
        Log.d(TAG, "stop DTMF")
    }

    // Optional: support RTT stream if needed
    override fun onStartRtt(rttTextStream: RttTextStream) {
        super.onStartRtt(rttTextStream)
        Log.d(TAG, "RTT started")
    }

    override fun onStopRtt() {
        super.onStopRtt()
        Log.d(TAG, "RTT stopped")
    }

    /**
     * Convenience helper to tear down and notify repository that this connection was removed.
     * Call this after setDisconnected(...) to ensure clean state.
     */
    fun finishAndCleanup(key: String?) {
        try {
            setDisconnected(DisconnectCause(DisconnectCause.REMOTE))
            destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error during finishAndCleanup: ${e.message}")
        } finally {
            ConnectionRepository.unregister(key)
        }
    }
}