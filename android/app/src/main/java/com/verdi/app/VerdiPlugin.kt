package com.verdi.app

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
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
            instance?.notifyListeners("onTripCaptured", trip)
        }

        fun onAppConnected(appName: String) {
            val app = JSObject().apply {
                put("appName", appName)
            }
            instance?.notifyListeners("onAppConnected", app)
        }
    }

    override fun load() {
        super.load()
        instance = this
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        Log.d(TAG, "checkPermissions called")
        val overlayGranted = Settings.canDrawOverlays(context)
        val accessibilityGranted = isAccessibilityServiceEnabled(context, VerdiAccessibilityService::class.java)

        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val backgroundLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val bluetoothScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val bluetoothConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val bluetoothAdvertise = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val prefs = context.getSharedPreferences("VerdiConfig", Context.MODE_PRIVATE)
        val lastConnectedApp = prefs.getString("lastConnectedApp", "")

        // Primary source: accessibility service static variable (real-time)
        // Fallback 1: UsageStatsManager (last foreground rideshare app in the past 30s)
        // Fallback 2: lastConnectedApp saved in SharedPreferences
        val accessibilityActiveApp = VerdiAccessibilityService.activeApp
            .takeIf { !it.isNullOrBlank() && it != "Ninguna" && it != "Verdi (Pruebas)" }
        val usageActiveApp = getRecentForegroundRideshareApp(context)
        val currentActiveApp = accessibilityActiveApp
            ?: usageActiveApp
            ?: lastConnectedApp.orEmpty()

        // Persist usage-detected app so future calls have it as fallback
        if (usageActiveApp != null && lastConnectedApp != usageActiveApp) {
            prefs.edit().putString("lastConnectedApp", usageActiveApp).apply()
        }

        val uberInstalled = isAppInstalled(context, "com.ubercab.driver")
        val didiInstalled = isAppInstalled(context, "com.didichuxing.driver") || isAppInstalled(context, "com.didiglobal.driver")
        val cabifyInstalled = isAppInstalled(context, "com.cabify.driver")

        Log.d(TAG, "DEBUG checkPermissions: VerdiAccessibilityService.activeApp=${VerdiAccessibilityService.activeApp} lastConnectedApp=$lastConnectedApp currentActiveApp=$currentActiveApp")

        val usageStatsGranted = try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }

        val ret = JSObject()
        ret.put("overlay", overlayGranted)
        ret.put("accessibility", accessibilityGranted)
        ret.put("locationFine", fineLocation)
        ret.put("locationCoarse", coarseLocation)
        ret.put("locationBackground", backgroundLocation)
        ret.put("bluetoothScan", bluetoothScan)
        ret.put("bluetoothConnect", bluetoothConnect)
        ret.put("bluetoothAdvertise", bluetoothAdvertise)
        ret.put("isServiceRunning", VerdiAccessibilityService.isServiceRunning)
        ret.put("activeApp", currentActiveApp)
        ret.put("lastConnectedApp", lastConnectedApp)
        ret.put("uberInstalled", uberInstalled)
        ret.put("didiInstalled", didiInstalled)
        ret.put("cabifyInstalled", cabifyInstalled)
        ret.put("usageStatsGranted", usageStatsGranted)
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
        } else if (type == "usageStats") {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else if (type == "runtime" || type == "location" || type == "bluetooth") {
            Log.d(TAG, "requestPermissions called type=$type")
            // Request runtime permissions (location + bluetooth) from the activity if possible.
            val perms = mutableListOf<String>()
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)

            val activity = bridge.activity
            if (activity != null) {
                try {
                    Log.d(TAG, "Activity found for runtime permission request")
                    ActivityCompat.requestPermissions(activity, perms.toTypedArray(), REQUEST_CODE_RUNTIME)
                    val ret = JSObject()
                    ret.put("status", "requested")
                    call.resolve(ret)
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "requestPermissions failed, fallback to app settings", e)
                }
            } else {
                Log.w(TAG, "requestPermissions no activity available, opening app settings")
            }

            // Fallback: open app settings so user can grant perms manually
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.data = Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
            val ret = JSObject()
            ret.put("status", "opened_settings")
            call.resolve(ret)
            return
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

    /** Uses UsageStatsManager to find the most recent rideshare app in the foreground
     *  within the last 5 minutes. Returns null if permission not granted or no match. */
    private fun getRecentForegroundRideshareApp(context: Context): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 300_000L // last 5 minutes
            val events = usm.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var lastPkg: String? = null
            var lastTs = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && event.timeStamp > lastTs) {
                    lastTs = event.timeStamp
                    lastPkg = event.packageName
                }
            }
            val pkg = lastPkg ?: return null
            when {
                pkg.contains("uber", ignoreCase = true) -> "Uber"
                pkg.contains("didi", ignoreCase = true) -> "DiDi"
                pkg.contains("cabify", ignoreCase = true) -> "Cabify"
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "UsageStatsManager fallback failed", e)
            null
        }
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

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
