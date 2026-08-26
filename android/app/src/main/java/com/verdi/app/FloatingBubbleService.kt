package com.verdi.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.util.Log
import java.util.Locale

class FloatingBubbleService : Service() {

    companion object {
        private const val TAG = "FloatingBubbleService"
        @Volatile var isRunning = false
        @Volatile private var instance: FloatingBubbleService? = null
        @Volatile private var pendingState: BubbleState? = null

        private data class BubbleState(
            val decision: String,
            val price: Double,
            val fuel: Double,
            val net: Double,
            val hourly: Double,
            val currency: String
        )

        /** Direct call from VerdiAccessibilityService or VerdiPlugin — avoids broadcast delivery issues. */
        fun updateBubble(context: Context?, decision: String, price: Double, fuel: Double, net: Double, hourly: Double, currency: String) {
            val state = BubbleState(decision, price, fuel, net, hourly, currency)
            pendingState = state

            val prefs = context?.getSharedPreferences("VerdiConfig", Context.MODE_PRIVATE)
            val bubbleEnabled = prefs?.getBoolean("bubble_enabled", true) ?: true
            if (!bubbleEnabled) {
                Log.d(TAG, "Bubble is disabled by user; ignoring updateBubble state: $decision")
                pendingState = null
                return
            }

            val inst = instance
            if (inst != null) {
                inst.updateBubbleState(decision, price, fuel, net, hourly, currency)
                pendingState = null
                return
            }

            if (context != null) {
                val intent = Intent(context, FloatingBubbleService::class.java)
                try {
                    ContextCompat.startForegroundService(context, intent)
                    Log.d(TAG, "Started FloatingBubbleService to apply bubble state: $decision")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start FloatingBubbleService", e)
                }
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleLayout: FrameLayout
    private lateinit var bubbleView: View
    private lateinit var bubbleText: TextView
    
    // Expanded Panel Views
    private lateinit var panelLayout: LinearLayout
    private lateinit var textPrice: TextView
    private lateinit var textFuel: TextView
    private lateinit var textProfit: TextView

    private lateinit var params: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams
    
    private var isExpanded = false
    private var stateColor = "#4B5563" // Default Graphite
    
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called - Service starting/restarting")
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "onCreate called - Creating FloatingBubbleService")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Start Foreground Service
        startServiceForeground()
        
        // Build views programmatically
        Log.d(TAG, "Creating bubble views")
        createBubbleView()
        createPanelView()
        // Expose instance only after views are fully initialized
        instance = this
        applyPendingBubbleState()
        Log.d(TAG, "FloatingBubbleService initialized successfully")
    }

    private fun startServiceForeground() {
        val channelId = "verdi_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Verdi Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la burbuja flotante del semáforo activa"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Verdi: Modo Conductor")
            .setContentText("Semáforo de rentabilidad activo en pantalla.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1001, notification)
    }

    private fun createBubbleView() {
        try {
            Log.d(TAG, "createBubbleView: Starting bubble view creation")
            // Root container for bubble
            bubbleLayout = FrameLayout(this)
            
            // Preferred Position
            val prefs = getSharedPreferences("VerdiConfig", Context.MODE_PRIVATE)
            val savedX = prefs.getInt("bubble_x", 0)
            val savedY = prefs.getInt("bubble_y", -100)
            Log.d(TAG, "Saved bubble position - X: $savedX, Y: $savedY")

            // Setup Window Manager Params
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER or Gravity.END
                x = savedX
                y = savedY
            }

            // Inside bubble (Circular design)
            bubbleView = FrameLayout(this).apply {
                val size = dpToPx(56)
                layoutParams = FrameLayout.LayoutParams(size, size)
                
                // Background ring with shadow
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(stateColor))
                    setStroke(dpToPx(3), Color.WHITE)
                }
                background = shape
            }

            // Indicator emoji
            bubbleText = TextView(this).apply {
                text = "🔘"
                textSize = 24f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            (bubbleView as FrameLayout).addView(bubbleText)
            bubbleLayout.addView(bubbleView)
            windowManager.addView(bubbleLayout, params)
            Log.d(TAG, "Bubble view added to window manager")

            // Drag and drop gesture
            setupDragAndDrop()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating bubble view", e)
        }
    }

    private fun createPanelView() {
        // Setup expanded info panel
        panelLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12131A"))
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)
            
            // Round border drawable
            val shape = GradientDrawable().apply {
                setColor(Color.parseColor("#1E202B"))
                cornerRadius = dpToPx(14).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#374151"))
            }
            background = shape
            visibility = View.GONE
        }

        // Setup panel window params
        panelParams = WindowManager.LayoutParams(
            dpToPx(240),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // Panel components
        val titleText = TextView(this).apply {
            text = "VERDI DETALLE"
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpToPx(8))
        }
        panelLayout.addView(titleText)

        textPrice = createPanelLabel("Precio Oferta: --")
        textFuel = createPanelLabel("Gasto Gasolina: --")
        textProfit = createPanelLabel("Ganancia Neta: --")

        panelLayout.addView(textPrice)
        panelLayout.addView(textFuel)
        panelLayout.addView(textProfit)

        // Close/minimize instruction
        val closeDescText = TextView(this).apply {
            text = "(Toca la burbuja para cerrar)"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 9f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dpToPx(10), 0, 0)
        }
        panelLayout.addView(closeDescText)

        // "APAGAR SEMÁFORO" Deactivate Button
        val btnDeactivate = TextView(this).apply {
            text = "APAGAR SEMÁFORO"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val paddingV = dpToPx(8)
            val paddingH = dpToPx(14)
            setPadding(paddingH, paddingV, paddingH, paddingV)

            val btnShape = GradientDrawable().apply {
                setColor(Color.parseColor("#7F1D1D")) // Dark Red fill
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#EF4444")) // Light Red border
            }
            background = btnShape

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12)
            }

            setOnClickListener {
                Log.d(TAG, "Deactivate button clicked - stopping FloatingBubbleService")
                stopSelf()
            }
        }
        panelLayout.addView(btnDeactivate)

        windowManager.addView(panelLayout, panelParams)
    }

    private fun createPanelLabel(defaultVal: String): TextView {
        return TextView(this).apply {
            text = defaultVal
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, dpToPx(4), 0, dpToPx(4))
        }
    }

    private fun setupDragAndDrop() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var touchTime = 0L

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX - (event.rawX - initialTouchX).toInt() // Gravity is on gravity.END, so drag moves opposite direction on X
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleLayout, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    val clickDuration = System.currentTimeMillis() - touchTime
                    
                    // Always snap horizontally to 0 (right edge) upon release
                    params.x = 0
                    windowManager.updateViewLayout(bubbleLayout, params)
                    
                    if (Math.abs(deltaX) < 10 && Math.abs(deltaY) < 10 && clickDuration < 300) {
                        toggleExpandedPanel()
                    } else {
                        // Save last preferred position
                        val prefs = getSharedPreferences("VerdiConfig", Context.MODE_PRIVATE)
                        prefs.edit().putInt("bubble_x", 0).putInt("bubble_y", params.y).apply()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleExpandedPanel() {
        isExpanded = !isExpanded
        if (isExpanded) {
            panelLayout.visibility = View.VISIBLE
            // reposition panel relative to bubble position
            panelParams.x = params.x
            panelParams.y = params.y
            windowManager.updateViewLayout(panelLayout, panelParams)
        } else {
            panelLayout.visibility = View.GONE
        }
    }

    private fun applyPendingBubbleState() {
        pendingState?.let { state ->
            Log.d(TAG, "Applying pending bubble state: ${state.decision}")
            updateBubbleState(state.decision, state.price, state.fuel, state.net, state.hourly, state.currency)
            pendingState = null
        }
    }

    private fun updateBubbleState(
        decision: String,
        price: Double,
        fuel: Double,
        net: Double,
        _hourly: Double,
        currencyCode: String
    ) {
        val emoji: String
        when (decision) {
            "GREEN" -> {
                stateColor = "#10B981"
                emoji = "🟢"
            }
            "YELLOW" -> {
                stateColor = "#F59E0B"
                emoji = "🟡"
            }
            "RED" -> {
                stateColor = "#EF4444"
                emoji = "🔴"
            }
            else -> {
                stateColor = "#4B5563"
                emoji = "🔘"
            }
        }

        val colorToApply = stateColor // capture before posting to avoid race conditions
        // Run UI updates on the main thread loop
        Handler(Looper.getMainLooper()).post {
            // 1. Always update panel labels first — isolated so they never get skipped
            try {
                // Use the currency symbol from user config, not a hardcoded "$"
                val cleanCur = when (currencyCode.uppercase(Locale.US)) {
                    "CLP" -> "CLP "
                    "COP" -> "COP "
                    "ARS" -> "ARS "
                    "MXN" -> "MXN "
                    "PEN" -> "S/ "
                    "BRL" -> "R$ "
                    "UYU" -> "UYU "
                    "USD" -> "US$ "
                    "EUR" -> "€"
                    else  -> "$currencyCode "
                }
                textPrice.text = String.format(Locale.US, "Precio Oferta: %s%,.0f", cleanCur, price)
                textFuel.text = String.format(Locale.US, "Gasto Gasolina: %s%,.0f", cleanCur, fuel)
                val netColor = if (net >= 0) "#10B981" else "#EF4444"
                textProfit.setTextColor(Color.parseColor(netColor))
                textProfit.text = String.format(Locale.US, "Ganancia Neta: %s%,.0f", cleanCur, net)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating panel labels", e)
            }

            // 2. Update bubble background color
            try {
                // Always recreate the drawable to avoid mutate() issues on some Android versions
                val newShape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(colorToApply))
                    setStroke(dpToPx(3), Color.WHITE)
                }
                bubbleView.background = newShape
                bubbleView.invalidate()
                bubbleText.text = emoji
                // Force root container to redraw the overlay
                bubbleLayout.invalidate()
                bubbleLayout.requestLayout()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating bubble color", e)
            }

            // 3. Force WindowManager to re-composite the overlay — isolated to avoid
            //    crashing the above updates if the view was detached (e.g. after service restart)
            try {
                if (::windowManager.isInitialized && ::bubbleLayout.isInitialized) {
                    windowManager.updateViewLayout(bubbleLayout, params)
                }
            } catch (e: Exception) {
                Log.w(TAG, "updateViewLayout failed (view may be detached): ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false
        Log.d(TAG, "onDestroy called - Cleaning up FloatingBubbleService")
        if (::bubbleLayout.isInitialized) {
            try {
                windowManager.removeView(bubbleLayout)
                Log.d(TAG, "Bubble layout removed")
            } catch (e: Exception) {
                Log.w(TAG, "Error removing bubble layout", e)
            }
        }
        if (::panelLayout.isInitialized) {
            try {
                windowManager.removeView(panelLayout)
                Log.d(TAG, "Panel layout removed")
            } catch (e: Exception) {
                Log.w(TAG, "Error removing panel layout", e)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return Math.round(dp.toFloat() * density)
    }
}
