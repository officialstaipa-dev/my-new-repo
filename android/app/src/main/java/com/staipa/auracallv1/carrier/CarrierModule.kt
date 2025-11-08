package com.staipa.auracallv1.carrier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.*
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.*
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

private const val TAG = "CarrierModule"
private const val ACTION_SIM_CARD_STATE_CHANGED = "android.telephony.action.SIM_CARD_STATE_CHANGED"
private const val ACTION_SIM_APPLICATION_STATE_CHANGED = "android.telephony.action.SIM_APPLICATION_STATE_CHANGED"
private const val ACTION_SERVICE_STATE_CHANGED = "android.telephony.action.SERVICE_STATE_CHANGED"

class CarrierModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "CarrierModule"

  private val telephonyManager: TelephonyManager by lazy {
    reactContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
  }

  private val subscriptionManager: SubscriptionManager? by lazy {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
        reactContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
      else null
    } catch (ex: Exception) {
      Log.w(TAG, "SubscriptionManager not available: ${ex.message}")
      null
    }
  }

  // --- receiver lifecycle flag ---
  private var simReceiverRegistered = false

  // --- SIM / Network State Receiver ---
  private val simReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      val action = intent?.action ?: return
      when (action) {
        ACTION_SIM_CARD_STATE_CHANGED,
        ACTION_SIM_APPLICATION_STATE_CHANGED,
        ACTION_SERVICE_STATE_CHANGED -> sendSimUpdate()
        else -> { /* ignore */ }
      }
    }
  }

  // --- Handler for Main Thread ---
  private val mainHandler = Handler(Looper.getMainLooper())

  // --- Simplified, Compatible CallStateListener ---
  @Suppress("DEPRECATION")
  // ***FIX 1:*** Declare as lateinit var instead of initializing here
  private lateinit var callStateListener: PhoneStateListener

  // --- Call Tracking ---
  private var lastState = TelephonyManager.CALL_STATE_IDLE
  private var callStartTime: Long = 0
  private var isIncoming = false

  private fun handleCallStateChanged(state: Int, phoneNumber: String?) {
    val timestamp = System.currentTimeMillis()
    val stateName = when (state) {
      TelephonyManager.CALL_STATE_IDLE -> "IDLE"
      TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
      TelephonyManager.CALL_STATE_RINGING -> "RINGING"
      else -> "UNKNOWN"
    }

    var direction = "UNKNOWN"
    when (state) {
      TelephonyManager.CALL_STATE_RINGING -> {
        isIncoming = true
        callStartTime = timestamp
        direction = "INCOMING"
      }
      TelephonyManager.CALL_STATE_OFFHOOK -> {
        direction = if (lastState == TelephonyManager.CALL_STATE_RINGING) "ANSWERED" else "OUTGOING"
        callStartTime = timestamp
      }
      TelephonyManager.CALL_STATE_IDLE -> {
        direction = if (lastState == TelephonyManager.CALL_STATE_OFFHOOK || lastState == TelephonyManager.CALL_STATE_RINGING)
          "ENDED"
        else
          "IDLE"
      }
    }

    lastState = state

    val data = Arguments.createMap().apply {
      putString("state", stateName)
      putString("number", phoneNumber ?: "")
      putString("direction", direction)
      putDouble("timestamp", timestamp.toDouble())
    }

    val array = Arguments.createArray().apply { pushMap(data) }
    emitEvent("CallStateChanged", array)
  }

  // --- React Lifecycle ---
  override fun initialize() {
    super.initialize()
    val filter = IntentFilter().apply {
      addAction(ACTION_SIM_CARD_STATE_CHANGED)
      addAction(ACTION_SIM_APPLICATION_STATE_CHANGED)
      addAction(ACTION_SERVICE_STATE_CHANGED)
    }

    try {
      // register receiver only if not registered
      if (!simReceiverRegistered) {
        reactContext.registerReceiver(simReceiver, filter)
        simReceiverRegistered = true
      }

      // Post to main looper to avoid potential permission / lifecycle races
      mainHandler.post {
        try {
          // ***FIX 2:*** Initialize the listener here, on the main thread
          callStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
              handleCallStateChanged(state, phoneNumber)
            }
          }
          telephonyManager.listen(callStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (ex: Exception) {
          Log.w(TAG, "Failed to listen to call state: ${ex.message}")
        }
      }
    } catch (ex: Exception) {
      Log.w(TAG, "Failed to register sim receiver or listener: ${ex.message}")
    }

    // initial push
    sendSimUpdate()
  }

  override fun invalidate() {
    super.invalidate()
    cleanup()
  }

  override fun onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy()
    cleanup()
  }

  private fun cleanup() {
    try {
      if (simReceiverRegistered) {
        try {
          reactContext.unregisterReceiver(simReceiver)
        } catch (ex: Exception) {
          Log.w(TAG, "Failed to unregister receiver: ${ex.message}")
        }
        simReceiverRegistered = false
      }

      try {
        // Check if listener was initialized before trying to use it
        if (::callStateListener.isInitialized) {
          telephonyManager.listen(callStateListener, PhoneStateListener.LISTEN_NONE)
        }
      } catch (ex: Exception) {
        Log.w(TAG, "Failed to stop call state listener: ${ex.message}")
      }
    } catch (ex: Exception) {
      Log.w(TAG, "Cleanup exception: ${ex.message}")
    }
  }

  private fun emitEvent(eventName: String, data: WritableArray) {
    try {
      reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit(eventName, data)
    } catch (ex: Exception) {
      Log.w(TAG, "emitEvent failed: ${ex.message}")
    }
  }

  // --- SIM & Carrier Info ---
  @ReactMethod
  @Suppress("MissingPermission")
  fun getCarrierInfo(promise: Promise) {
    try {
      val sims = getSimData()
      promise.resolve(sims)
    } catch (e: Exception) {
      Log.e(TAG, "getCarrierInfo error", e)
      promise.reject("CARRIER_ERROR", e.message)
    }
  }

  private fun sendSimUpdate() {
    // small debounce to handle quick state flaps
    mainHandler.postDelayed({
      try {
        val sims = getSimData()
        emitEvent("CarrierUpdated", sims)
      } catch (ex: Exception) {
        Log.w(TAG, "sendSimUpdate error: ${ex.message}")
      }
    }, 500)
  }

  @Suppress("MissingPermission")
  private fun getSimData(): WritableArray {
    val sims = Arguments.createArray()
    try {
      val activeSubs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
        subscriptionManager?.activeSubscriptionInfoList else null

      if (!activeSubs.isNullOrEmpty()) {
        for (sub in activeSubs) {
          val simMap = Arguments.createMap()
          val carrierName = sub.carrierName?.toString() ?: (telephonyManager.networkOperatorName ?: "Unknown")
          val simSlot = sub.simSlotIndex
          val number = sub.number ?: ""
          val countryIso = sub.countryIso ?: ""

          // Note: per-SIM simState isn't standardized across all OEMs; we use global telephonyManager.simState as a best-effort.
          val simState = when (telephonyManager.simState) {
            TelephonyManager.SIM_STATE_READY -> "READY"
            TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "LOCKED_PIN"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "LOCKED_PUK"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "LOCKED_NETWORK"
            else -> "UNKNOWN"
          }

          val serviceState =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
              telephonyManager.serviceState?.state ?: ServiceState.STATE_OUT_OF_SERVICE
            else ServiceState.STATE_OUT_OF_SERVICE

          val networkState = when (serviceState) {
            ServiceState.STATE_IN_SERVICE -> "IN_SERVICE"
            ServiceState.STATE_OUT_OF_SERVICE -> "OUT_OF_SERVICE"
            ServiceState.STATE_EMERGENCY_ONLY -> "EMERGENCY_ONLY"
            ServiceState.STATE_POWER_OFF -> "POWER_OFF"
            else -> "UNKNOWN"
          }

          simMap.putString("carrierName", carrierName)
          simMap.putInt("slotIndex", simSlot)
          simMap.putString("phoneNumber", number)
          simMap.putString("countryIso", countryIso)
          simMap.putString("simState", simState)
          simMap.putString("networkState", networkState)
          simMap.putInt("subscriptionId", sub.subscriptionId)
          sims.pushMap(simMap)
        }
      } else {
        val single = Arguments.createMap()
        single.putString("carrierName", telephonyManager.networkOperatorName ?: "Unknown")
        single.putString("simState", "UNKNOWN")
        single.putString("networkState", "UNKNOWN")
        sims.pushMap(single)
      }
    } catch (e: Exception) {
      Log.e(TAG, "getSimData error", e)
      val err = Arguments.createMap()
      err.putString("error", e.message ?: "Failed to read SIM info")
      sims.pushMap(err)
    }
    return sims
  }

  // --- Default Dialer ---
  @ReactMethod
  fun ensureDefaultDialer(promise: Promise) {
    try {
      val telecomManager = reactContext.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
      if (reactContext.packageName != telecomManager.defaultDialerPackage) {
        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
          putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, reactContext.packageName)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        reactContext.startActivity(intent)
        promise.resolve("REQUESTED_DEFAULT_DIALER_CHANGE")
      } else {
        promise.resolve("ALREADY_DEFAULT_DIALER")
      }
    } catch (e: Exception) {
      Log.e(TAG, "ensureDefaultDialer error", e)
      promise.reject("DIALER_ERROR", e.message)
    }
  }

  // --- SIM Slot PhoneAccountHandle ---
  @Suppress("MissingPermission")
  private fun getPhoneAccountHandle(simSlotIndex: Int): PhoneAccountHandle? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
    val telecomManager = reactContext.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return null

    val subInfo = try {
      subscriptionManager?.getActiveSubscriptionInfoForSimSlotIndex(simSlotIndex)
    } catch (ex: Exception) {
      Log.w(TAG, "getActiveSubscriptionInfoForSimSlotIndex failed: ${ex.message}")
      null
    }

    val accounts = try { telecomManager.callCapablePhoneAccounts } catch (ex: Exception) {
      Log.w(TAG, "callCapablePhoneAccounts failed: ${ex.message}")
      emptyList<PhoneAccountHandle>()
    }

    // 1) Prefer handle whose id contains subscriptionId
    val subscriptionIdStr = subInfo?.subscriptionId?.toString()
    if (!subscriptionIdStr.isNullOrEmpty()) {
      accounts.firstOrNull { handle ->
        handle.id.contains(subscriptionIdStr)
      }?.let { return it }
    }

    // 2) Try matching slot index string inside id
    val slotStr = simSlotIndex.toString()
    accounts.firstOrNull { handle ->
      handle.id.contains(slotStr)
    }?.let { return it }

    // 3) Fallback: return first call-capable account (best-effort)
    return accounts.firstOrNull()
  }

  // --- Place Carrier Call ---
  @ReactMethod
  @Suppress("MissingPermission")
  fun makeCallWithSim(phoneNumber: String, simSlotIndex: Int, promise: Promise) {
    try {
      val perm = android.Manifest.permission.CALL_PHONE
      if (reactContext.checkCallingOrSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        promise.reject("PERMISSION_DENIED", "CALL_PHONE permission is required to make a call.")
        return
      }

      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        promise.reject("UNSUPPORTED_API", "Placing calls on a specific SIM is only supported on Android M (API 23) and above.")
        return
      }

      val telecomManager = reactContext.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
      if (telecomManager == null) {
        promise.reject("SYSTEM_ERROR", "Telecom service not available.")
        return
      }

      if (reactContext.packageName != telecomManager.defaultDialerPackage) {
        promise.reject("NOT_DEFAULT_DIALER", "App must be the default dialer to place carrier calls.")
        return
      }

      val uri = Uri.fromParts("tel", phoneNumber, null)
      val handle = getPhoneAccountHandle(simSlotIndex)
      if (handle == null) {
        promise.reject("SIM_NOT_FOUND", "No valid PhoneAccountHandle found for SIM slot $simSlotIndex.")
        return
      }

      val extras = Bundle().apply {
        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
      }

      telecomManager.placeCall(uri, extras)
      promise.resolve("CALL_STARTED")
    } catch (e: Exception) {
      Log.e(TAG, "makeCallWithSim error", e)
      promise.reject("CALL_ERROR", e.message ?: "Failed to place call.")
    }
  }
}
