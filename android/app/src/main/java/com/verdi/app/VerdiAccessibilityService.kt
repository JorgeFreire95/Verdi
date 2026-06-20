package com.verdi.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern
import android.widget.Toast

class VerdiAccessibilityService : AccessibilityService() {

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

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.verdi.app.CONFIG_UPDATED") {
                loadConfig()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Toast.makeText(this, "Verdi: Servicio Creado", Toast.LENGTH_SHORT).show()
        try {
            loadConfig()
            
            // Register receiver for dynamic config updates
            val filter = IntentFilter("com.verdi.app.CONFIG_UPDATED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(configReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(configReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Toast.makeText(this, "Verdi: Servicio Conectado a Accesibilidad", Toast.LENGTH_SHORT).show()
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
        
        // Diagnóstico: Mostrar Toast temporal con el nombre del paquete activo (excepto sistema y launcher)
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

        // Scan ride-sharing packages: Uber, DiDi, Cabify
        // For general development / debugging, scan any active screen that changes
        if (pkg.contains("uber", ignoreCase = true) || 
            pkg.contains("didi", ignoreCase = true) || 
            pkg.contains("cabify", ignoreCase = true) ||
            pkg.contains("verdi", ignoreCase = true) // allow self-scanning for testing
        ) {
            notifyAppConnected(pkg)

            val rootNode = rootInActiveWindow ?: return
            
            // Collect all visible text nodes
            val texts = ArrayList<String>()
            findTextNodes(rootNode, texts)
            
            // Parse for travel metrics
            parseAndEvaluateScreenTexts(texts)
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

        // Mostrar un Toast de diagnóstico
        Toast.makeText(applicationContext, "Verdi: Conectado a $cleanName", Toast.LENGTH_SHORT).show()

        VerdiPlugin.onAppConnected(cleanName)
    }

    private fun findTextNodes(node: AccessibilityNodeInfo?, texts: ArrayList<String>) {
        if (node == null) return
        
        if (node.text != null && node.text.toString().isNotBlank()) {
            texts.add(node.text.toString())
        }
        
        for (i in 0 until node.childCount) {
            findTextNodes(node.getChild(i), texts)
        }
    }

    private fun parseAndEvaluateScreenTexts(texts: List<String>) {
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
                val valStr = priceMatcher.group(1)?.replace(".", "")?.replace(",", "")
                valStr?.toDoubleOrNull()?.let {
                    detectedPrice = it
                }
            }
            
            // Distance Match
            val distMatcher = distPattern.matcher(text)
            if (distMatcher.find()) {
                val valStr = distMatcher.group(1)?.replace(",", ".")
                valStr?.toDoubleOrNull()?.let {
                    detectedDistance = it
                }
            }

            // Time Match
            val timeMatcher = timePattern.matcher(text)
            if (timeMatcher.find()) {
                val valStr = timeMatcher.group(1)
                valStr?.toDoubleOrNull()?.let {
                    detectedTimeMins = it
                }
            }
        }

        // If we found a candidate trip (needs at least price & distance to evaluate)
        if (detectedPrice != null && detectedDistance != null) {
            val now = System.currentTimeMillis()
            if (now - lastCapturedTime < captureCooldown) return
            lastCapturedTime = now

            val finalTimeMins = detectedTimeMins ?: 15.0 // fallback if time text parsing failed
            runProfitabilityCalculation(detectedPrice!!, detectedDistance!!, finalTimeMins)
        }
    }

    private fun runProfitabilityCalculation(price: Double, distance: Double, timeMins: Double) {
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

        // Push to Web client via VerdiPlugin
        VerdiPlugin.onTripCaptured(price, distance, timeMins)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(configReceiver)
    }
}
