package com.verdi.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.Locale

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleLayout: FrameLayout
    private lateinit var bubbleView: View
    private lateinit var bubbleText: TextView
    
    // Expanded Panel Views
    private lateinit var panelLayout: LinearLayout
    private lateinit var textPrice: TextView
    private lateinit var textFuel: TextView
    private lateinit var textProfit: TextView
    private lateinit var textHourlyRate: TextView

    private lateinit var params: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams
    
    private var isExpanded = false
    private var stateColor = "#4B5563" // Default Graphite
    
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.verdi.app.UPDATE_BUBBLE") {
                val decision = intent.getStringExtra("decision") ?: "GRAPHITE"
                val price = intent.getDoubleExtra("price", 0.0)
                val fuel = intent.getDoubleExtra("fuel", 0.0)
                val net = intent.getDoubleExtra("net", 0.0)
                val hourly = intent.getDoubleExtra("hourly", 0.0)
                val cur = intent.getStringExtra("currency") ?: "$"
                
                updateBubbleState(decision, price, fuel, net, hourly, cur)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Register Broadcast Receiver for communication
        val filter = IntentFilter("com.verdi.app.UPDATE_BUBBLE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        // Start Foreground Service
        startServiceForeground()
        
        // Build views programmatically
        createBubbleView()
        createPanelView()
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
        // Root container for bubble
        bubbleLayout = FrameLayout(this)
        
        // Preferred Position
        val prefs = getSharedPreferences("VerdiConfig", Context.MODE_PRIVATE)
        val savedX = prefs.getInt("bubble_x", 0)
        val savedY = prefs.getInt("bubble_y", -100)

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

        // Drag and drop gesture
        setupDragAndDrop()
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
        textHourlyRate = createPanelLabel("Por Hora: --")

        panelLayout.addView(textPrice)
        panelLayout.addView(textFuel)
        panelLayout.addView(textProfit)
        panelLayout.addView(textHourlyRate)

        // Close/minimize instruction
        val closeDescText = TextView(this).apply {
            text = "(Toca la burbuja para cerrar)"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 9f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dpToPx(10), 0, 0)
        }
        panelLayout.addView(closeDescText)

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
                    
                    if (Math.abs(deltaX) < 10 && Math.abs(deltaY) < 10 && clickDuration < 300) {
                        toggleExpandedPanel()
                    } else {
                        // Save last preferred position
                        val prefs = getSharedPreferences("VerdiConfig", Context.MODE_PRIVATE)
                        prefs.edit().putInt("bubble_x", params.x).putInt("bubble_y", params.y).apply()
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

    private fun updateBubbleState(
        decision: String,
        price: Double,
        fuel: Double,
        net: Double,
        hourly: Double,
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

        // Update bubble background color and text
        val shape = bubbleView.background as GradientDrawable
        shape.setColor(Color.parseColor(stateColor))
        bubbleText.text = emoji
        
        // Update Panel labels with currency formatting
        val cleanCur = if (currencyCode == "CLP" || currencyCode == "COP") "$ " else "$ "
        textPrice.text = String.format("Precio Oferta: %s%,.0f", cleanCur, price)
        textFuel.text = String.format("Gasto Gasolina: %s%,.0f", cleanCur, fuel)
        
        val netColor = if (net >= 0) "#10B981" else "#EF4444"
        textProfit.setTextColor(Color.parseColor(netColor))
        textProfit.text = String.format("Ganancia Neta: %s%,.0f", cleanCur, net)
        
        textHourlyRate.text = String.format("Tasa Horaria: %s%,.0f/hr", cleanCur, hourly)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        if (::bubbleLayout.isInitialized) windowManager.removeView(bubbleLayout)
        if (::panelLayout.isInitialized) windowManager.removeView(panelLayout)
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return Math.round(dp.toFloat() * density)
    }
}
