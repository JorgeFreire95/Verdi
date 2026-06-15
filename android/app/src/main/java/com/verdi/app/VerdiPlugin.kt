package com.verdi.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "Verdi")
class VerdiPlugin : Plugin() {

    companion object {
        private var instance: VerdiPlugin? = null

        fun onTripCaptured(price: Double, distance: Double, timeMins: Double) {
            val trip = JSObject().apply {
                put("price", price)
                put("distance", distance)
                put("timeMins", timeMins)
            }
            instance?.notifyListeners("onTripCaptured", trip)
        }
    }

    override fun load() {
        super.load()
        instance = this
    }

    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        val context = context
        val overlayGranted = Settings.canDrawOverlays(context)
        val accessibilityGranted = isAccessibilityServiceEnabled(context, VerdiAccessibilityService::class.java)

        val ret = JSObject()
        ret.put("overlay", overlayGranted)
        ret.put("accessibility", accessibilityGranted)
        call.resolve(ret)
    }

    @PluginMethod
    override fun requestPermissions(call: PluginCall) {
        val type = call.getString("type", "")
        val context = context
        if (type == "overlay") {
            if (!Settings.canDrawOverlays(context)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } else if (type == "accessibility") {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        val ret = JSObject()
        ret.put("status", "requested")
        call.resolve(ret)
    }

    @PluginMethod
    fun updateConfig(call: PluginCall) {
        val sharedPrefs = context.getSharedPreferences("VerdiConfig", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        
        editor.putString("currency", call.getString("currency", "CLP"))
        editor.putString("distanceUnit", call.getString("distanceUnit", "km"))
        editor.putString("fuelUnit", call.getString("fuelUnit", "L"))
        editor.putString("consumptionUnit", call.getString("consumptionUnit", "km_l"))
        
        editor.putFloat("fuelPrice", call.getFloat("fuelPrice", 1200f) ?: 1200f)
        editor.putFloat("vehicleEfficiency", call.getFloat("vehicleEfficiency", 12f) ?: 12f)
        editor.putFloat("minHourlyEarnings", call.getFloat("minHourlyEarnings", 15000f) ?: 15000f)
        editor.putFloat("minPerDistance", call.getFloat("minPerDistance", 350f) ?: 350f)
        editor.apply()

        // Notify active services to update configurations immediately
        val configIntent = Intent("com.verdi.app.CONFIG_UPDATED")
        context.sendBroadcast(configIntent)

        val ret = JSObject()
        ret.put("status", "success")
        call.resolve(ret)
    }

    @PluginMethod
    fun toggleBubble(call: PluginCall) {
        val active = call.getBoolean("active", false) ?: false
        val context = context
        val intent = Intent(context, FloatingBubbleService::class.java)
        if (active) {
            context.startService(intent)
        } else {
            context.stopService(intent)
        }
        val ret = JSObject()
        ret.put("active", active)
        call.resolve(ret)
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = "${context.packageName}/${service.name}"
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            if (componentNameString.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
