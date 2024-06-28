package io.embrace.tracerprovider

import android.view.View
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ReactShadowNode
import com.facebook.react.uimanager.ViewManager

class TracerProviderPackage : ReactPackage {
	override fun createViewManagers(
			reactContext: ReactApplicationContext
    ): MutableList<ViewManager<View, ReactShadowNode<*>>> = mutableListOf()

	override fun createNativeModules(
			reactContext: ReactApplicationContext
    ): MutableList<NativeModule> = listOf(TracerProviderModule(reactContext)).toMutableList()
}
