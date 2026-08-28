package com.verdi.app

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "Verdi")
class VerdiPlugin : Plugin() {

    private val REQUEST_CODE_RUNTIME = 1501
    private val TAG = "VerdiPlugin"


    companion object {
        private var instance: VerdiPlugin? = null

        fun onTripCaptured(price: Double, distance: Double, timeMins: Double) {
            val trip = JSObject().apply {
                put("price", price)
                put("distance", distance)
                put("timeMins", timeMins)
            }
            // retainUntilConsumed=true: event is queued if WebView is paused (app in background)
            instance?.notifyListeners("onTripCaptured", trip, true)
        }

        fun onAppConnected(appName: String) {
            Log.d("VerdiPlugin", "🔌 onAppConnected called with appName=$appName, instance=${instance != null}")
            val app = JSObject().apply {
                put("appName", appName)
            }
            if (instance != null) {
                // retainUntilConsumed=true: event is queued if WebView is paused (app in background)
                // This prevents losing the "Cabify activo" event when Verdi is not in foreground
                instance?.notifyListeners("onAppConnected", app, true)
                Log.d("VerdiPlugin", "📢 Event emitted to JavaScript listeners for appName=$appName")
            } else {
                Log.w("VerdiPlugin", "⚠️  instance is NULL - cannot emit event! Plugin may not be loaded.")
            }
        }
    }

    override fun load() {
        super.load()
        instance = this
        // Fire the current active app state immediately so the WebView gets it on startup.
        // This covers the case where Cabify/Uber/DiDi was opened BEFORE Verdi's WebView loaded
        // (instance was null when the accessibility event fired, so the event was lost).
        val currentApp = VerdiAccessibilityService.activeApp
        val normalizedApp = normalizeActiveApp(currentApp)
        if (!normalizedApp.isNullOrBlank()) {
            Log.d(TAG, "load() → replaying active app state: $normalizedApp")
            bridge.activity?.runOnUiThread {
                notifyListeners("onAppConnected", JSObject().apply { put("appName", normalizedApp) }, true)
            }
        } else {
            VerdiAccessibilityService.activeApp = "Ninguna"
        }
        // NOTE: We do NOT fall back to SharedPreferences here.
        // lastConnectedApp could be stale (e.g. Cabify was closed but still saved).
        // The polling in checkPermissions handles the SharedPreferences fallback on first load.
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        Log.d(TAG, "checkPermissions called")
        val overlayGranted = Settings.canDrawOverlays(context)
        val accessibilityGranted = isAccessibilityServiceEnabled(context, VerdiAccessibilityService::class.java)

        val prefs = context.getSharedPreferences("VerdiConfig", Context.MODE_PRIVATE)
        val storedLastConnectedApp = prefs.getString("lastConnectedApp", "")
        val validLastConnectedApp = if (isAppInstalledForDisplay(storedLastConnectedApp)) storedLastConnectedApp.orEmpty() else ""
        if (validLastConnectedApp != storedLastConnectedApp) {
            prefs.edit().putString("lastConnectedApp", validLastConnectedApp).apply()
        }

        // Primary source: accessibility service static variable (real-time).
        val rawAccessibilityApp = VerdiAccessibilityService.activeApp
        val isExplicitlyNone = rawAccessibilityApp == "Ninguna"
        val accessibilityCandidate = rawAccessibilityApp
            .takeIf { !it.isNullOrBlank() && it != "Ninguna" && it != "Verdi (Pruebas)" }
        val accessibilityActiveApp = normalizeActiveApp(accessibilityCandidate)

        val currentActiveApp = accessibilityActiveApp
            ?: if (!isExplicitlyNone) validLastConnectedApp else ""

        if (currentActiveApp.isNullOrBlank() && !isExplicitlyNone && !accessibilityCandidate.isNullOrBlank()) {
            VerdiAccessibilityService.activeApp = "Ninguna"
        }

        val uberInstalled = isAppInstalled(context, "com.ubercab.driver")
        val didiInstalled = isAppInstalled(context, "com.didichuxing.driver") || isAppInstalled(context, "com.didiglobal.driver")
        val cabifyInstalled = isAppInstalled(context, "com.cabify.driver") ||
            isAnyInstalledPackageContaining(context, "cabify")

        Log.d(TAG, "DEBUG checkPermissions: VerdiAccessibilityService.activeApp=${VerdiAccessibilityService.activeApp} lastConnectedApp=$validLastConnectedApp currentActiveApp=$currentActiveApp")
        
        val bubbleEnabled = prefs.getBoolean("bubble_enabled", false)

        val ret = JSObject()
        ret.put("overlay", overlayGranted)
        ret.put("accessibility", accessibilityGranted)
        ret.put("isServiceRunning", VerdiAccessibilityService.isServiceRunning)
        ret.put("isBubbleRunning", FloatingBubbleService.isRunning)
        ret.put("bubbleEnabled", bubbleEnabled)
        ret.put("activeApp", currentActiveApp)
        ret.put("lastConnectedApp", validLastConnectedApp)
        ret.put("uberInstalled", uberInstalled)
        ret.put("didiInstalled", didiInstalled)
        ret.put("cabifyInstalled", cabifyInstalled)
        Log.d(TAG, "checkPermissions result=" + ret.toString())
        call.resolve(ret)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @PluginMethod
    override fun requestPermissions(call: PluginCall) {
        val type = call.getString("type", "")
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
        } else if (type == "appDetails") {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        val ret = JSObject()
        ret.put("status", "requested")
        call.resolve(ret)
    }

    

    @PluginMethod
    fun updateBubbleState(call: PluginCall) {
        val decision  = call.getString("decision",  "GRAPHITE") ?: "GRAPHITE"
        val price     = call.getDouble("price",     0.0)        ?: 0.0
        val fuel      = call.getDouble("fuel",      0.0)        ?: 0.0
        val net       = call.getDouble("net",       0.0)        ?: 0.0
        val hourly    = call.getDouble("hourly",    0.0)        ?: 0.0
        val currency  = call.getString("currency",  "CLP")      ?: "CLP"

        // Update FloatingBubbleService directly (avoids broadcast delivery issues)
        FloatingBubbleService.updateBubble(context, decision, price, fuel, net, hourly, currency)
        Log.d(TAG, "FloatingBubbleService.updateBubble called - Decision: $decision")

        val ret = JSObject()
        ret.put("status", "ok")
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
        // commit() is synchronous — guarantees data is persisted before CONFIG_UPDATED is broadcast,
        // preventing loadConfig() in VerdiAccessibilityService from reading stale values.
        editor.commit()

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
        val prefs = context.getSharedPreferences("VerdiConfig", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("bubble_enabled", active).commit()

        val intent = Intent(context, FloatingBubbleService::class.java)
        if (active) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.stopService(intent)
        }
        val ret = JSObject()
        ret.put("active", active)
        call.resolve(ret)
    }



    private fun normalizeActiveApp(appName: String?): String? {
        if (appName.isNullOrBlank()) return null
        if (appName == "Ninguna" || appName == "Verdi (Pruebas)") return null
        return if (isAppInstalledForDisplay(appName)) appName else null
    }

    private fun isAppInstalledForDisplay(appName: String?): Boolean {
        return when (appName?.trim()) {
            "Uber" -> isAppInstalled(context, "com.ubercab.driver")
            "DiDi" -> isAppInstalled(context, "com.didichuxing.driver") || isAppInstalled(context, "com.didiglobal.driver")
            "Cabify" -> isAppInstalled(context, "com.cabify.driver") || isAnyInstalledPackageContaining(context, "cabify")
            else -> false
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedPackage = context.packageName
        val expectedClass = service.name
        val expectedShortClass = ".${service.simpleName}"
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val component = ComponentName.unflattenFromString(componentNameString)
            if (component != null) {
                val samePackage = component.packageName.equals(expectedPackage, ignoreCase = true)
                val sameClass = component.className.equals(expectedClass, ignoreCase = true) ||
                    component.className.equals(expectedShortClass, ignoreCase = true) ||
                    component.className.equals("$expectedPackage$expectedShortClass", ignoreCase = true)
                if (samePackage && sameClass) {
                    return true
                }
            }

            // Fallback for OEM variants that may store custom flattened strings.
            if (componentNameString.contains(expectedPackage, ignoreCase = true) &&
                (componentNameString.contains(expectedClass, ignoreCase = true) ||
                 componentNameString.contains(expectedShortClass, ignoreCase = true))) {
                return true
            }
        }
        return false
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isAnyInstalledPackageContaining(context: Context, token: String): Boolean {
        return try {
            val packages = context.packageManager.getInstalledPackages(0)
            packages.any { pkg -> pkg.packageName?.contains(token, ignoreCase = true) == true }
        } catch (e: Exception) {
            false
        }
    }
}
