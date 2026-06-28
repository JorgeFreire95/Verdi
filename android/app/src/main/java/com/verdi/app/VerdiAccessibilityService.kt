package com.verdi.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.regex.Pattern
import android.widget.Toast
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.app.Notification

class VerdiAccessibilityService : AccessibilityService() {

    companion object {
        var isServiceRunning = false
        @Volatile var activeApp = "Ninguna"
        private const val TAG = "VerdiAccessibilityService"
    }

    // Config cache in memory
    private var fuelPrice = 1200f
    private var vehicleEfficiency = 12f
    private var minHourlyEarnings = 15000f
    private var minPerDistance = 350f
    private var currency = "CLP"
    private var distanceUnit = "km"
    private var fuelUnit = "L"
    private var consumptionUnit = "km_l"

    private var lastCapturedTime = 0L
    private val captureCooldown = 2000L // avoid spamming calculations within 2 seconds of the same trip

    private var lastNotifiedPkg = ""
    private var lastNotifiedTime = 0L
    private val NOTIF_CHANNEL_ID = "verdi_service_channel"

    // ── Polling fallback: checks foreground app every 1 second via rootInActiveWindow ──
    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                val root = rootInActiveWindow
                if (root != null) {
                    val pkg = root.packageName?.toString()
                    root.recycle()
                    if (!pkg.isNullOrBlank()) {
                        val cleanName = pkgToAppName(pkg)
                        if (cleanName != null && cleanName != activeApp) {
                            Log.d(TAG, "Poll detected app change: $activeApp -> $cleanName (pkg=$pkg)")
                            commitActiveApp(cleanName)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Poll error", e)
            }
            pollHandler.postDelayed(this, 1000)
        }
    }
    private val NOTIF_ID = 8421

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.verdi.app.CONFIG_UPDATED") {
                loadConfig()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called - Initializing VerdiAccessibilityService")
        Toast.makeText(this, "Verdi: Servicio Creado", Toast.LENGTH_SHORT).show()
        try {
            loadConfig()
            Log.d(TAG, "Configuration loaded")
            
            // Register receiver for dynamic config updates
            val filter = IntentFilter("com.verdi.app.CONFIG_UPDATED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(configReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(configReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing service", e)
            e.printStackTrace()
        }
        // Create notification channel for persistent service notification
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(NOTIF_CHANNEL_ID, "Verdi Service", NotificationManager.IMPORTANCE_LOW)
                channel.description = "Notificaciones de estado del servicio Verdi"
                nm.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed creating notification channel", e)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        activeApp = "Ninguna"
        Log.d(TAG, "onServiceConnected - Service connected to accessibility")

        // Programmatically configure the service to receive ALL window events.
        // This is more reliable than the XML config on some OEM devices (OPPO/ColorOS).
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            info.flags = (AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS)
            info.notificationTimeout = 100
            serviceInfo = info
            Log.d(TAG, "AccessibilityServiceInfo configured programmatically")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set serviceInfo programmatically", e)
        }

        VerdiPlugin.onAppConnected(activeApp)
        pollHandler.postDelayed(pollRunnable, 1000)
        Toast.makeText(this, "Verdi: Servicio Conectado a Accesibilidad", Toast.LENGTH_SHORT).show()
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setContentTitle("Verdi — Servicio Activo")
                .setContentText("Lectura de pantalla activa")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            startForeground(NOTIF_ID, notif)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground notification", e)
        }
    }

    private fun loadConfig() {
        val prefs = getSharedPreferences("VerdiConfig", Context.MODE_PRIVATE)
        currency = prefs.getString("currency", "CLP") ?: "CLP"
        distanceUnit = prefs.getString("distanceUnit", "km") ?: "km"
        fuelUnit = prefs.getString("fuelUnit", "L") ?: "L"
        consumptionUnit = prefs.getString("consumptionUnit", "km_l") ?: "km_l"
        
        fuelPrice = prefs.getFloat("fuelPrice", 1200f)
        vehicleEfficiency = prefs.getFloat("vehicleEfficiency", 12f)
        minHourlyEarnings = prefs.getFloat("minHourlyEarnings", 15000f)
        minPerDistance = prefs.getFloat("minPerDistance", 350f)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: ""
        val eventType = event.eventType
        Log.d(TAG, "onAccessibilityEvent type=$eventType pkg=$pkg activeApp=$activeApp")

        // ── Detection Method 1: TYPE_WINDOWS_CHANGED (most reliable on Android 9+ / OPPO) ──
        // Fires whenever any window appears/disappears. We inspect the windows list to find
        // the topmost application window — this does NOT depend on the event's packageName.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            detectForegroundAppFromWindowsList()
        }

        // ── Detection Method 2: TYPE_WINDOW_STATE_CHANGED ──
        // Classic method: only update for known rideshare apps or the launcher.
        // System/unknown packages return null so we don't accidentally reset state.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val cleanName = pkgToAppName(pkg)
            if (cleanName != null && cleanName != activeApp) {
                Log.d(TAG, "App change via WINDOW_STATE_CHANGED: $activeApp -> $cleanName")
                commitActiveApp(cleanName)
            }
            // Extra: verify via rootInActiveWindow in case the event pkg is a system shell
            if (cleanName == null) {
                detectForegroundAppFromRoot()
            }
        }

        // ── Diagnostic Toast (non-rideshare, non-system) ──
        if (pkg.isNotBlank() &&
            !pkg.contains("android", ignoreCase = true) &&
            !pkg.contains("systemui", ignoreCase = true) &&
            !pkg.contains("launcher", ignoreCase = true) &&
            !pkg.contains("verdi", ignoreCase = true)
        ) {
            val now = System.currentTimeMillis()
            if (pkg != lastNotifiedPkg || now - lastNotifiedTime > 5000) {
                lastNotifiedPkg = pkg
                lastNotifiedTime = now
                Toast.makeText(applicationContext, "Diagnóstico: Activo $pkg", Toast.LENGTH_SHORT).show()
            }
        }

        // ── Content scan for rideshare apps ──
        if (pkg.contains("uber", ignoreCase = true) ||
            pkg.contains("didi", ignoreCase = true) ||
            pkg.contains("cabify", ignoreCase = true)
        ) {
            Log.d(TAG, "Scanning active app package $pkg")
            val cleanName = pkgToAppName(pkg)
            if (cleanName != null && cleanName != activeApp) {
                commitActiveApp(cleanName)
            }

            notifyAppConnected(pkg)

            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.w(TAG, "rootInActiveWindow is null for pkg=$pkg")
                return
            }
            val texts = ArrayList<String>()
            findTextNodes(rootNode, texts)
            Log.d(TAG, "Collected ${texts.size} text nodes for pkg=$pkg")
            parseAndEvaluateScreenTexts(texts)
        }
    }

    // ── Helper: map a package name to a clean app name (null = unknown/system) ──
    private fun pkgToAppName(pkg: String): String? = when {
        pkg.contains("uber", ignoreCase = true)    -> "Uber"
        pkg.contains("didi", ignoreCase = true)    -> "DiDi"
        pkg.contains("cabify", ignoreCase = true)  -> "Cabify"
        // Never mark Verdi itself as the foreground rideshare app.
        // On some devices, overlay/utility windows from our own process can
        // appear during app switches and incorrectly overwrite Cabify/Uber/DiDi.
        pkg.contains("verdi", ignoreCase = true)   -> null
        pkg.contains("launcher", ignoreCase = true) ||
            pkg == "com.android.launcher"  ||
            pkg == "com.android.launcher2" ||
            pkg == "com.android.launcher3" -> "Ninguna"
        else -> null
    }

    // ── Helper: persist new active app and notify JS ──
    private fun commitActiveApp(cleanName: String) {
        activeApp = cleanName
        if (cleanName != "Ninguna" && cleanName != "Verdi (Pruebas)") {
            getSharedPreferences("VerdiConfig", Context.MODE_PRIVATE)
                .edit().putString("lastConnectedApp", cleanName).apply()
        }
        VerdiPlugin.onAppConnected(activeApp)
    }

    // ── Method 1 impl: scan the windows list for the active rideshare app ──
    // Scans ALL TYPE_APPLICATION windows so that a launcher window listed before a
    // rideshare window doesn't incorrectly reset activeApp to "Ninguna".
    private fun detectForegroundAppFromWindowsList() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        try {
            val wins = windows ?: return
            var rideshareApp: String? = null
            var hasLauncher = false
            for (win in wins) {
                if (win.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = win.root ?: continue
                val windowPkg = root.packageName?.toString()
                root.recycle()
                if (windowPkg == null) continue
                val cleanName = pkgToAppName(windowPkg) ?: continue
                if (cleanName == "Ninguna") {
                    hasLauncher = true
                } else {
                    // Rideshare app found — takes priority, stop scanning
                    rideshareApp = cleanName
                    break
                }
            }
            val targetApp = rideshareApp ?: if (hasLauncher) "Ninguna" else return
            if (targetApp != activeApp) {
                Log.d(TAG, "App change via WINDOWS_CHANGED list: $activeApp -> $targetApp (rideshare=$rideshareApp, launcher=$hasLauncher)")
                commitActiveApp(targetApp)
            }
        } catch (e: Exception) {
            Log.w(TAG, "detectForegroundAppFromWindowsList failed", e)
        }
    }

    // ── Method 3 impl: inspect rootInActiveWindow directly ──
    private fun detectForegroundAppFromRoot() {
        try {
            val root = rootInActiveWindow ?: return
            val rootPkg = root.packageName?.toString() ?: run { root.recycle(); return }
            root.recycle()
            val cleanName = pkgToAppName(rootPkg) ?: return
            if (cleanName != activeApp) {
                Log.d(TAG, "App change via rootInActiveWindow: $activeApp -> $cleanName (pkg=$rootPkg)")
                commitActiveApp(cleanName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "detectForegroundAppFromRoot failed", e)
        }
    }

    private fun notifyAppConnected(pkg: String) {
        if (pkg.contains("verdi", ignoreCase = true)) {
            return
        }
        val now = System.currentTimeMillis()
        if (pkg == lastNotifiedPkg && now - lastNotifiedTime < 5000) {
            return
        }
        lastNotifiedPkg = pkg
        lastNotifiedTime = now

        val cleanName = when {
            pkg.contains("uber", ignoreCase = true) -> "Uber"
            pkg.contains("didi", ignoreCase = true) -> "DiDi"
            pkg.contains("cabify", ignoreCase = true) -> "Cabify"
            else -> "App"
        }

        // Guardar persistente en SharedPreferences
        val prefs = getSharedPreferences("VerdiConfig", Context.MODE_PRIVATE)
        prefs.edit().putString("lastConnectedApp", cleanName).apply()
        
        Log.d(TAG, "notifyAppConnected: Saved lastConnectedApp=$cleanName to SharedPreferences")

        // Mostrar un Toast de diagnóstico
        Toast.makeText(applicationContext, "Verdi: Conectado a $cleanName", Toast.LENGTH_SHORT).show()

        VerdiPlugin.onAppConnected(cleanName)
        Log.d(TAG, "notifyAppConnected: Called VerdiPlugin.onAppConnected($cleanName)")
    }

    private fun findTextNodes(node: AccessibilityNodeInfo?, texts: ArrayList<String>) {
        if (node == null) return
        
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        node.hintText?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        
        for (i in 0 until node.childCount) {
            findTextNodes(node.getChild(i), texts)
        }
    }

    private fun parseFlexibleNumber(raw: String): Double? {
        var t = raw.replace(Regex("[^0-9.,]"), "")
        if (t.isBlank()) return null

        val commaCount = t.count { it == ',' }
        val dotCount = t.count { it == '.' }

        if (commaCount > 0 && dotCount > 0) {
            if (t.lastIndexOf(',') > t.lastIndexOf('.')) {
                t = t.replace(".", "")
                t = t.replace(",", ".")
            } else {
                t = t.replace(",", "")
            }
        } else if (commaCount > 0) {
            if (commaCount == 1 && t.substringAfter(',').length <= 2) {
                t = t.replace(",", ".")
            } else {
                t = t.replace(",", "")
            }
        } else {
            if (dotCount > 1) {
                t = t.replace(".", "")
            }
        }

        return t.toDoubleOrNull()
    }

    private fun parseAndEvaluateScreenTexts(texts: List<String>) {
        Log.d(TAG, "parseAndEvaluateScreenTexts called with ${texts.size} texts")
        var detectedPrice: Double? = null
        var detectedDistance: Double? = null
        var detectedTimeMins: Double? = null

        // Regex pattern matches
        val pricePattern = Pattern.compile("[$]\\s*([0-9]+[.,]?[0-9]*[.,]?[0-9]*)")
        val distPattern = Pattern.compile("([0-9]+[.,]?[0-9]*)\\s*(km|KM|mi|mi\\.|Millas|millas)", Pattern.CASE_INSENSITIVE)
        val timePattern = Pattern.compile("([0-9]+)\\s*(min|mins|minutos|hr|h|hora|horas)", Pattern.CASE_INSENSITIVE)

        for (text in texts) {
            // Price Match
            val priceMatcher = pricePattern.matcher(text)
            if (priceMatcher.find()) {
                val raw = priceMatcher.group(1) ?: ""
                val parsed = parseFlexibleNumber(raw)
                parsed?.let {
                    detectedPrice = it
                    Log.d(TAG, "Detected price text='$text' -> $detectedPrice")
                }
            }
            
            // Distance Match
            val distMatcher = distPattern.matcher(text)
            if (distMatcher.find()) {
                val valStr = distMatcher.group(1)?.replace(",", ".")
                valStr?.toDoubleOrNull()?.let {
                    detectedDistance = it
                    Log.d(TAG, "Detected distance text='$text' -> $detectedDistance")
                }
            }

            // Time Match
            val timeMatcher = timePattern.matcher(text)
            if (timeMatcher.find()) {
                val valStr = timeMatcher.group(1)
                valStr?.toDoubleOrNull()?.let {
                    detectedTimeMins = it
                    Log.d(TAG, "Detected time text='$text' -> $detectedTimeMins")
                }
            }
        }

        // If we found a candidate trip (needs at least price & distance to evaluate)
        if (detectedPrice != null && detectedDistance != null) {
            Log.d(TAG, "Candidate trip found price=$detectedPrice distance=$detectedDistance time=$detectedTimeMins")
            val now = System.currentTimeMillis()
            if (now - lastCapturedTime < captureCooldown) {
                Log.d(TAG, "Skipping duplicate capture due cooldown")
                return
            }
            lastCapturedTime = now

            val finalTimeMins = detectedTimeMins ?: 15.0 // fallback if time text parsing failed
            runProfitabilityCalculation(detectedPrice!!, detectedDistance!!, finalTimeMins)
        }
    }

    private fun runProfitabilityCalculation(price: Double, distance: Double, timeMins: Double) {
        Log.d(TAG, "runProfitabilityCalculation - Price: $price, Distance: $distance, Time: $timeMins")
        
        // Calculation
        val fuelUsed = distance / vehicleEfficiency.toDouble()

        val fuelCost = fuelUsed * fuelPrice.toDouble()
        val netProfit = price - fuelCost
        
        val hours = timeMins / 60.0
        val hourlyRate = if (hours > 0) (netProfit / hours) else 0.0
        val distanceRate = if (distance > 0) (netProfit / distance) else 0.0

        // Decision logic
        var decision = "RED"
        if (netProfit > 0) {
            val pctDist = distanceRate / minPerDistance.toDouble()

            if (pctDist >= 1.0) {
                decision = "GREEN"
            } else if (pctDist >= 0.7) {
                decision = "YELLOW"
            }
        }

        Log.d(TAG, "Decision: $decision - Net: $netProfit, Hourly: $hourlyRate")

        // Send local Broadcast to FloatingBubbleService
        val bubbleIntent = Intent("com.verdi.app.UPDATE_BUBBLE").apply {
            putExtra("decision", decision)
            putExtra("price", price)
            putExtra("fuel", fuelCost)
            putExtra("net", netProfit)
            putExtra("hourly", hourlyRate)
            putExtra("currency", currency)
        }
        sendBroadcast(bubbleIntent)
        Log.d(TAG, "Broadcast sent to FloatingBubbleService")

        // Push to Web client via VerdiPlugin
        VerdiPlugin.onTripCaptured(price, distance, timeMins)
    }

    override fun onInterrupt() {
        isServiceRunning = false
        activeApp = "Ninguna"
        pollHandler.removeCallbacks(pollRunnable)
        VerdiPlugin.onAppConnected(activeApp)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        activeApp = "Ninguna"
        pollHandler.removeCallbacks(pollRunnable)
        VerdiPlugin.onAppConnected(activeApp)
        try {
            stopForeground(true)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground failed", e)
        }
        try {
            unregisterReceiver(configReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "unregisterReceiver failed", e)
        }
    }
}
