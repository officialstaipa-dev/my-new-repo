package com.staipa.auracallv1.carrier

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/**
 * CarrierPackage
 * This package registers CarrierModule with React Native.
 * It's automatically added to MainApplication.kt by the withCarrier.js plugin.
 */
class CarrierPackage : ReactPackage {

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        // Register your merged module here
        return listOf(CarrierModule(reactContext))
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        // No custom UI components yet
        return emptyList()
    }
}
