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
        private const val TAG = "VerdiAccService"
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

    private var lastCapturedSignature: String? = null
    private var lastCapturedTime = 0L
    private val duplicateOfferWindowMs = 10000L

    private var lastNotifiedPkg = ""
    private var lastNotifiedTime = 0L
    private val NOTIF_CHANNEL_ID = "verdi_service_channel"

    // ── Debounce for "Ninguna" resets ──
    // When the launcher briefly appears (e.g. during app-switch transition), we don't want
    // to immediately reset the active rideshare app. Only reset after 4 continuous seconds
    // of no rideshare app being visible.
    private val ningunaResetHandler = Handler(Looper.getMainLooper())
    private var ningunaResetPending = false
    private val NINGUNA_DEBOUNCE_MS = 4000L

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

    private data class PriceCandidate(
        val value: Double,
        val score: Int,
        val index: Int,
        val source: String
    )

    private data class RouteSegment(
        val distanceKm: Double,
        val timeMins: Double
    )

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
        Log.d(TAG, "✨ onServiceConnected - Accessibility Service is NOW ACTIVE")

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
        val eventTypeStr = when(eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "WINDOWS_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
            else -> "TYPE_$eventType"
        }
        Log.d(TAG, "🔔 onAccessibilityEvent [$eventTypeStr] pkg=$pkg activeApp=$activeApp")

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
        // Trigger on ANY event type (WINDOW_STATE_CHANGED, WINDOW_CONTENT_CHANGED, etc.)
        // so we react instantly when a trip request appears — even mid-screen updates.
        val pkgToScan = if (pkg.contains("uber", ignoreCase = true) ||
            pkg.contains("didi", ignoreCase = true) ||
            pkg.contains("cabify", ignoreCase = true)
        ) {
            pkg
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Fallback: use the root window's package to catch events that arrive
            // with a system/shell package name while a rideshare app is in front.
            val root = rootInActiveWindow
            val rootPkg = root?.packageName?.toString().also { root?.recycle() }
            if (!rootPkg.isNullOrBlank() && (
                rootPkg.contains("uber", ignoreCase = true) ||
                rootPkg.contains("didi", ignoreCase = true) ||
                rootPkg.contains("cabify", ignoreCase = true))
            ) rootPkg else null
        } else null

        if (pkgToScan != null) {
            Log.d(TAG, "🚗 Scanning active app package $pkgToScan (event=$eventTypeStr)")
            val cleanName = pkgToAppName(pkgToScan)
            if (cleanName != null && cleanName != activeApp) {
                Log.d(TAG, "  └─ App change detected: $cleanName")
                commitActiveApp(cleanName)
            }

            notifyAppConnected(pkgToScan)

            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.w(TAG, "  ⚠️  rootInActiveWindow is NULL for pkg=$pkgToScan")
                return
            }
            
            Log.d(TAG, "  └─ Root node packageName: ${rootNode.packageName}")
            Log.d(TAG, "  └─ Root node childCount: ${rootNode.childCount}")
            
            val texts = ArrayList<String>()
            findTextNodes(rootNode, texts)
            Log.d(TAG, "  └─ Collected ${texts.size} text nodes from tree")
            
            if (texts.isNotEmpty()) {
                texts.forEachIndexed { idx, text ->
                    if (text.isNotBlank()) {
                        Log.d(TAG, "    [Text $idx]: ${text.take(100)}")
                    }
                }
            }
            
            parseAndEvaluateScreenTexts(texts)
        }
        
        // ── Fallback: Always check root if event didn't trigger a known rideshare app ──
        // This handles cases where Cabify/Uber/DiDi might not fire events properly
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            try {
                val root = rootInActiveWindow
                if (root != null) {
                    val rootPkg = root.packageName?.toString()
                    root.recycle()
                    if (!rootPkg.isNullOrBlank()) {
                        val cleanName = pkgToAppName(rootPkg)
                        if (cleanName != null && cleanName != activeApp) {
                            Log.d(TAG, "Fallback detection triggered: $activeApp -> $cleanName (pkg=$rootPkg)")
                            commitActiveApp(cleanName)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallback detection error", e)
            }
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
        if (cleanName == "Ninguna") {
            // Debounce: don't reset immediately — wait 4s to avoid false resets during
            // brief launcher transitions when switching between apps.
            if (ningunaResetPending) return
            ningunaResetPending = true
            Log.d(TAG, "⏳ Debouncing Ninguna reset (${NINGUNA_DEBOUNCE_MS}ms)...")
            ningunaResetHandler.postDelayed({
                ningunaResetPending = false
                if (activeApp != "Ninguna") {
                    Log.d(TAG, "🔄 commitActiveApp (debounced): Changing state from '$activeApp' to 'Ninguna'")
                    activeApp = "Ninguna"
                    VerdiPlugin.onAppConnected(activeApp)
                }
            }, NINGUNA_DEBOUNCE_MS)
            return
        }
        // Non-Ninguna app: cancel any pending reset and update immediately
        if (ningunaResetPending) {
            ningunaResetHandler.removeCallbacksAndMessages(null)
            ningunaResetPending = false
            Log.d(TAG, "✅ Cancelled pending Ninguna reset")
        }
        Log.d(TAG, "🔄 commitActiveApp: Changing state from '$activeApp' to '$cleanName'")
        activeApp = cleanName
        if (cleanName != "Ninguna" && cleanName != "Verdi (Pruebas)") {
            getSharedPreferences("VerdiConfig", Context.MODE_PRIVATE)
                .edit().putString("lastConnectedApp", cleanName).apply()
            Log.d(TAG, "  💾 Saved to SharedPreferences: lastConnectedApp=$cleanName")
        }
        Log.d(TAG, "  📞 Calling VerdiPlugin.onAppConnected('$cleanName')...")
        VerdiPlugin.onAppConnected(activeApp)
        Log.d(TAG, "  ✅ VerdiPlugin.onAppConnected() returned")
    }

    // ── Method 1 impl: scan the windows list for the topmost app window ──
    private fun detectForegroundAppFromWindowsList() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        try {
            val wins = windows ?: return
            for (win in wins) {
                if (win.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = win.root ?: continue
                val windowPkg = root.packageName?.toString()
                root.recycle()
                if (windowPkg == null) continue
                // Topmost app window found. If its package is unknown (null = Verdi itself or
                // any unrecognised app), stop here — don't keep scanning lower windows that
                // might be a launcher and incorrectly fire commitActiveApp("Ninguna").
                val cleanName = pkgToAppName(windowPkg) ?: return
                if (cleanName != activeApp) {
                    Log.d(TAG, "App change via WINDOWS_CHANGED list: $activeApp -> $cleanName (pkg=$windowPkg)")
                    commitActiveApp(cleanName)
                }
                return // only process the topmost application window
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
        
        val cleanName = when {
            pkg.contains("uber", ignoreCase = true) -> "Uber"
            pkg.contains("didi", ignoreCase = true) -> "DiDi"
            pkg.contains("cabify", ignoreCase = true) -> "Cabify"
            else -> "App"
        }
        
        // Only throttle for non-rideshare apps, always update rideshare apps
        if (cleanName == "App") {
            val now = System.currentTimeMillis()
            if (pkg == lastNotifiedPkg && now - lastNotifiedTime < 5000) {
                return
            }
            lastNotifiedPkg = pkg
            lastNotifiedTime = now
        } else {
            // For rideshare apps, always mark the time but don't throttle
            lastNotifiedPkg = pkg
            lastNotifiedTime = System.currentTimeMillis()
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
        
        try {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            
            // getHintText requires API 26, so only call it on newer versions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                node.hintText?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            }
            
            // Also try to get viewIdResourceName for debugging
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                node.viewIdResourceName?.takeIf { it.isNotBlank() }?.let { 
                    texts.add("[ViewId: $it]")
                }
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    findTextNodes(child, texts)
                    try {
                        child.recycle()
                    } catch (e: Exception) {
                        // Ignore recycle errors
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in findTextNodes", e)
        }
    }

    private fun parseFlexibleNumber(raw: String): Double? {
        if (raw.isBlank()) {
            Log.d(TAG, "    [parseNum] Empty input")
            return null
        }
        
        var t = raw.replace(Regex("[^0-9.,]"), "").trimEnd(',', '.')
        if (t.isBlank()) {
            Log.d(TAG, "    [parseNum] No digits found in '$raw'")
            return null
        }

        val commaCount = t.count { it == ',' }
        val dotCount = t.count { it == '.' }
        val isNoDecimalsCurrency = currency.equals("CLP", ignoreCase = true) || currency.equals("COP", ignoreCase = true)

        if (commaCount > 0 && dotCount > 0) {
            if (t.lastIndexOf(',') > t.lastIndexOf('.')) {
                t = t.replace(".", "")
                t = t.replace(",", ".")
            } else {
                t = t.replace(",", "")
            }
        } else if (commaCount > 0) {
            if (commaCount == 1 && t.substringAfter(',').length <= 2 && !isNoDecimalsCurrency) {
                t = t.replace(",", ".")
            } else {
                t = t.replace(",", "")
            }
        } else {
            if (dotCount > 1 || (dotCount == 1 && isNoDecimalsCurrency)) {
                t = t.replace(".", "")
            } else if (dotCount == 1) {
                // If it is a single dot, check if it could be a thousands separator.
                // In Spanish, thousands separator is dot (e.g. 8.500). If it has exactly 3 digits after the dot,
                // and it is not followed by anything, it is very likely a thousands separator.
                val afterDot = t.substringAfter('.')
                if (afterDot.length == 3) {
                    t = t.replace(".", "")
                }
            }
        }

        val result = t.toDoubleOrNull()
        Log.d(TAG, "    [parseNum] raw='$raw' -> cleaned='$t' -> result=$result")
        return result
    }

    private fun normalizeScreenText(raw: String): String {
        return raw.replace(Regex("\\s+"), " ").trim()
    }

    private fun looksLikeStandaloneCurrencyToken(text: String): Boolean {
        return Regex(
            "^(CLP|COP|ARS|MXN|PEN|BRL|UYU|USD|EUR|\\$|€|¥|£)$",
            RegexOption.IGNORE_CASE
        ).matches(text)
    }

    private fun looksLikeStandaloneAmount(text: String): Boolean {
        return Regex("^[0-9][0-9.,]*$").matches(text)
    }

    private fun containsIgnoredMoneyContext(text: String): Boolean {
        val lowered = text.lowercase()
        return lowered.contains("/km") ||
            lowered.contains("estimad") ||
            lowered.contains("tarjeta") ||
            lowered.contains("efectivo") ||
            lowered.contains("cash") ||
            lowered.contains("visa") ||
            lowered.contains("master") ||
            lowered.contains("rating") ||
            lowered.contains("calificacion") ||
            lowered.contains("calificación") ||
            lowered.contains("★") ||
            lowered.contains("⭐")
    }

    private fun scorePriceCandidate(
        rawText: String,
        value: Double,
        index: Int,
        standalone: Boolean,
        pairedToken: Boolean
    ): Int {
        var score = 0
        if (standalone) score += 140
        if (pairedToken) score += 150
        if (rawText.length <= 16) score += 15
        if (index <= 8) score += (18 - (index * 2)).coerceAtLeast(0)
        if (value >= 1000) score += 20
        if (value >= 10000) score += 10
        if (containsIgnoredMoneyContext(rawText)) score -= 180
        if (!pairedToken && Regex("^[0-9]+[.,][0-9]{1,2}$").matches(rawText)) score -= 140
        if (value < 10 && rawText.contains(Regex("[.,][0-9]{1,2}"))) score -= 120
        return score
    }

    private fun collectPriceCandidates(texts: List<String>): List<PriceCandidate> {
        val standalonePriceRegex = Regex(
            "^(?:CLP|COP|ARS|MXN|PEN|BRL|UYU|USD|EUR|\\$|€|¥|£)\\s*([0-9][0-9.,]*)$|^([0-9][0-9.,]*)\\s*(?:CLP|COP|ARS|MXN|PEN|BRL|UYU|USD|EUR|\\$|€|¥|£)$",
            RegexOption.IGNORE_CASE
        )
        val embeddedPriceRegex = Regex(
            "(?:CLP|COP|ARS|MXN|PEN|BRL|UYU|USD|EUR|\\$|€|¥|£)\\s*([0-9][0-9.,]*)|([0-9][0-9.,]*)\\s*(?:CLP|COP|ARS|MXN|PEN|BRL|UYU|USD|EUR|\\$|€|¥|£)",
            RegexOption.IGNORE_CASE
        )

        val candidates = mutableListOf<PriceCandidate>()

        texts.forEachIndexed { index, text ->
            standalonePriceRegex.matchEntire(text)?.let { match ->
                val raw = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty()
                val parsed = parseFlexibleNumber(raw)
                if (parsed != null) {
                    candidates += PriceCandidate(
                        value = parsed,
                        score = scorePriceCandidate(text, parsed, index, standalone = true, pairedToken = false),
                        index = index,
                        source = text
                    )
                }
            }

            if (looksLikeStandaloneAmount(text)) {
                val parsed = parseFlexibleNumber(text)
                if (parsed != null && parsed >= 100) {
                    val prev = texts.getOrNull(index - 1).orEmpty()
                    val next = texts.getOrNull(index + 1).orEmpty()
                    val paired = looksLikeStandaloneCurrencyToken(prev) || looksLikeStandaloneCurrencyToken(next)
                    candidates += PriceCandidate(
                        value = parsed,
                        score = scorePriceCandidate(text, parsed, index, standalone = false, pairedToken = paired) +
                            if (paired) 0 else 35,
                        index = index,
                        source = text
                    )
                }
            }

            embeddedPriceRegex.findAll(text).forEach { match ->
                val raw = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty()
                val parsed = parseFlexibleNumber(raw)
                if (parsed != null) {
                    candidates += PriceCandidate(
                        value = parsed,
                        score = scorePriceCandidate(text, parsed, index, standalone = false, pairedToken = false),
                        index = index,
                        source = text
                    )
                }
            }
        }

        return candidates
    }

    private fun parseDistanceKm(raw: String): Double? {
        return raw.replace(",", ".").toDoubleOrNull()
    }

    private fun parseDurationToMinutes(rawValue: String, rawUnit: String): Double? {
        val base = rawValue.replace(",", ".").toDoubleOrNull() ?: return null
        return when (rawUnit.lowercase()) {
            "hr", "h", "hora", "horas" -> base * 60.0
            else -> base
        }
    }

    private fun extractRouteMetrics(texts: List<String>): Pair<Double?, Double?> {
        val joinedText = texts.joinToString("\n")
        val pickupRegex = Pattern.compile(
            "(?:^|\\n)\\s*(?:a|en)\\s*([0-9]+[.,]?[0-9]*)\\s*(min|mins|minutos|hr|h|hora|horas)\\s*\\(([0-9]+[.,]?[0-9]*)\\s*(km|mi|mi\\.|millas|millas?)\\)",
            Pattern.CASE_INSENSITIVE
        )
        val tripRegex = Pattern.compile(
            "viaje\\s*:?\\s*([0-9]+[.,]?[0-9]*)\\s*(min|mins|minutos|hr|h|hora|horas)\\s*\\(([0-9]+[.,]?[0-9]*)\\s*(km|mi|mi\\.|millas|millas?)\\)",
            Pattern.CASE_INSENSITIVE
        )

        var pickupSegment: RouteSegment? = null
        val pickupMatcher = pickupRegex.matcher(joinedText)
        if (pickupMatcher.find()) {
            val timeMins = parseDurationToMinutes(pickupMatcher.group(1).orEmpty(), pickupMatcher.group(2).orEmpty())
            val distanceKm = parseDistanceKm(pickupMatcher.group(3).orEmpty())
            if (timeMins != null && distanceKm != null) {
                pickupSegment = RouteSegment(distanceKm, timeMins)
                Log.d(TAG, "  🚕 Pickup segment: ${pickupSegment.timeMins} min / ${pickupSegment.distanceKm} km")
            }
        }

        var tripSegment: RouteSegment? = null
        val tripMatcher = tripRegex.matcher(joinedText)
        if (tripMatcher.find()) {
            val timeMins = parseDurationToMinutes(tripMatcher.group(1).orEmpty(), tripMatcher.group(2).orEmpty())
            val distanceKm = parseDistanceKm(tripMatcher.group(3).orEmpty())
            if (timeMins != null && distanceKm != null) {
                tripSegment = RouteSegment(distanceKm, timeMins)
                Log.d(TAG, "  🧭 Trip segment: ${tripSegment.timeMins} min / ${tripSegment.distanceKm} km")
            }
        }

        if (pickupSegment != null && tripSegment != null) {
            return Pair(
                pickupSegment.distanceKm + tripSegment.distanceKm,
                pickupSegment.timeMins + tripSegment.timeMins
            )
        }
        if (tripSegment != null) {
            return Pair(tripSegment.distanceKm, tripSegment.timeMins)
        }
        if (pickupSegment != null) {
            return Pair(pickupSegment.distanceKm, pickupSegment.timeMins)
        }

        var genericDistance: Double? = null
        var genericTimeMins: Double? = null
        val distPattern = Pattern.compile("([0-9]+[.,]?[0-9]*)\\s*(km|KM|mi|mi\\.|Millas|millas)", Pattern.CASE_INSENSITIVE)
        val timePattern = Pattern.compile("([0-9]+[.,]?[0-9]*)\\s*(min|mins|minutos|hr|h|hora|horas)", Pattern.CASE_INSENSITIVE)
        for (text in texts) {
            if (!containsIgnoredMoneyContext(text)) {
                val distMatcher = distPattern.matcher(text)
                if (distMatcher.find()) {
                    genericDistance = parseDistanceKm(distMatcher.group(1).orEmpty()) ?: genericDistance
                }
            }

            val timeMatcher = timePattern.matcher(text)
            if (timeMatcher.find()) {
                genericTimeMins = parseDurationToMinutes(timeMatcher.group(1).orEmpty(), timeMatcher.group(2).orEmpty()) ?: genericTimeMins
            }
        }
        return Pair(genericDistance, genericTimeMins)
    }

    private fun parseAndEvaluateScreenTexts(texts: List<String>) {
        Log.d(TAG, "📊 parseAndEvaluateScreenTexts: Processing ${texts.size} text nodes")
        if (texts.isEmpty()) {
            Log.w(TAG, "  ⚠️  No texts collected - tree may be empty or using WebView")
            return
        }

        val normalizedTexts = texts
            .map(::normalizeScreenText)
            .filter { it.isNotBlank() }
            .distinct()

        val bestPriceCandidate = collectPriceCandidates(normalizedTexts)
            .filter { it.score > 0 }
            .sortedWith(compareByDescending<PriceCandidate> { it.score }.thenByDescending { it.value }.thenBy { it.index })
            .firstOrNull()

        val detectedPrice = bestPriceCandidate?.value
        if (bestPriceCandidate != null) {
            Log.d(
                TAG,
                "  💰 Price selected: value=${bestPriceCandidate.value} score=${bestPriceCandidate.score} source='${bestPriceCandidate.source.take(80)}'"
            )
        }

        val (detectedDistance, detectedTimeMins) = extractRouteMetrics(normalizedTexts)

        if (detectedPrice != null && detectedDistance != null) {
            Log.d(TAG, "✅ Candidate trip: price=\$$detectedPrice distance=${detectedDistance}km time=$detectedTimeMins")
            val tripSignature = "${detectedPrice.toInt()}|${String.format("%.1f", detectedDistance)}"
            val now = System.currentTimeMillis()
            if (tripSignature == lastCapturedSignature && now - lastCapturedTime < duplicateOfferWindowMs) {
                Log.d(TAG, "  ⏳ Skipping duplicate offer signature=$tripSignature")
                return
            }
            lastCapturedSignature = tripSignature
            lastCapturedTime = now

            val finalTimeMins = detectedTimeMins ?: 15.0
            runProfitabilityCalculation(detectedPrice, detectedDistance, finalTimeMins)
        } else {
            Log.w(TAG, "  ❌ No valid trip found: price=${detectedPrice} distance=${detectedDistance}")
        }
    }

    private fun runProfitabilityCalculation(price: Double, distance: Double, timeMins: Double) {
        Log.d(TAG, "🧠 runProfitabilityCalculation")
        Log.d(TAG, "   Price: \$$price | Distance: ${distance}km | Time: ${timeMins.toInt()}min")
        
        // Calculation
        val fuelUsed = distance / vehicleEfficiency.toDouble()
        val fuelCost = fuelUsed * fuelPrice.toDouble()
        val netProfit = price - fuelCost
        
        val hours = timeMins / 60.0
        val hourlyRate = if (hours > 0) (netProfit / hours) else 0.0
        val distanceRate = if (distance > 0) (netProfit / distance) else 0.0

        // Decision logic — use BOTH user-configured thresholds
        var decision = "RED"
        if (netProfit > 0) {
            val pctDist   = distanceRate / minPerDistance.toDouble()
            val pctHourly = if (minHourlyEarnings > 0) hourlyRate / minHourlyEarnings.toDouble() else 1.0
            Log.d(TAG, "   NetProfit: \$$netProfit | DistRate: \$${String.format("%.0f", distanceRate)}/km (${String.format("%.1f", pctDist*100)}%) | HourlyRate: \$${String.format("%.0f", hourlyRate)}/hr (${String.format("%.1f", pctHourly*100)}%)")

            // Both thresholds must pass for GREEN; use the stricter (lower) ratio for YELLOW
            val minPct = minOf(pctDist, pctHourly)
            if (minPct >= 1.0) {
                decision = "GREEN"
            } else if (minPct >= 0.7) {
                decision = "YELLOW"
            }
        } else {
            Log.d(TAG, "   NetProfit is NEGATIVE: \$$netProfit")
        }

        Log.d(TAG, "   🚦 Decision: $decision | Hourly: \$${String.format("%.0f", hourlyRate)}/hr")

        // Update FloatingBubbleService directly (avoids broadcast delivery issues)
        FloatingBubbleService.updateBubble(applicationContext, decision, price, fuelCost, netProfit, hourlyRate, currency)
        Log.d(TAG, "   💰 FloatingBubbleService.updateBubble called - decision=$decision")

        // Push to Web client via VerdiPlugin
        VerdiPlugin.onTripCaptured(price, distance, timeMins)
    }

    override fun onInterrupt() {
        isServiceRunning = false
        activeApp = "Ninguna"
        pollHandler.removeCallbacks(pollRunnable)
        ningunaResetHandler.removeCallbacksAndMessages(null)
        Log.w(TAG, "⚠️  onInterrupt - Accessibility Service was INTERRUPTED")
        VerdiPlugin.onAppConnected(activeApp)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        activeApp = "Ninguna"
        pollHandler.removeCallbacks(pollRunnable)
        ningunaResetHandler.removeCallbacksAndMessages(null)
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
