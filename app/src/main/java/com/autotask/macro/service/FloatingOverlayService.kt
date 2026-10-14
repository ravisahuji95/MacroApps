package com.autotask.macro.service

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
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.autotask.macro.R

/**
 * Floating Overlay Service that draws Start, Stop, and Play Loop buttons
 * on top of the Android home screen and any target application.
 */
class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var btnRecord: Button
    private lateinit var btnStop: Button
    private lateinit var btnLoop: Button

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        setupFloatingWindow()
    }

    private fun setupFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        // Build floating pill container
        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 16)
            elevation = 16f
            
            // Rounded dark container
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B")) // Slate 800
                cornerRadius = 48f
                setStroke(2, Color.parseColor("#334155"))
            }
        }

        // Drag handle / Title
        statusText = TextView(this).apply {
            text = "● AutoTask"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(8, 0, 16, 0)
        }

        // 1.) START RECORD BUTTON (Captures user tasks in target app)
        btnRecord = Button(this).apply {
            text = "● Rec"
            textSize = 11f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DC2626")) // Red
                cornerRadius = 24f
            }
            setOnClickListener {
                MacroAccessibilityService.instance?.let { service ->
                    service.startRecording()
                    statusText.text = "Recording..."
                    btnRecord.isEnabled = false
                    btnStop.isEnabled = true
                    btnLoop.isEnabled = false
                    Toast.makeText(this@FloatingOverlayService, "Recording started! Perform your task now.", Toast.LENGTH_SHORT).show()
                } ?: run {
                    Toast.makeText(this@FloatingOverlayService, "Please enable Accessibility Service first!", Toast.LENGTH_LONG).show()
                }
            }
        }

        // 2.) STOP BUTTON (Stops recording or stops the infinite repetition loop)
        btnStop = Button(this).apply {
            text = "■ Stop"
            textSize = 11f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#475569")) // Slate
                cornerRadius = 24f
            }
            setOnClickListener {
                MacroAccessibilityService.instance?.let { service ->
                    service.stopAll()
                    statusText.text = "Stopped"
                    btnRecord.isEnabled = true
                    btnStop.isEnabled = true
                    btnLoop.isEnabled = true
                    Toast.makeText(this@FloatingOverlayService, "Stopped!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 3.) START LOOP REPEAT BUTTON (Fills form again & again until stopped)
        btnLoop = Button(this).apply {
            text = "▶ Loop"
            textSize = 11f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB")) // Blue
                cornerRadius = 24f
            }
            setOnClickListener {
                MacroAccessibilityService.instance?.let { service ->
                    if (service.getRecordedActionsCount() == 0) {
                        Toast.makeText(this@FloatingOverlayService, "Record a task first before looping!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    service.startInfiniteRepeatLoop { loopCycle ->
                        statusText.post { statusText.text = "Loop #$loopCycle" }
                    }
                    btnRecord.isEnabled = false
                    btnLoop.isEnabled = false
                    btnStop.isEnabled = true
                    Toast.makeText(this@FloatingOverlayService, "Automated loop running! Tap Stop anytime to cancel.", Toast.LENGTH_SHORT).show()
                } ?: run {
                    Toast.makeText(this@FloatingOverlayService, "Please enable Accessibility Service first!", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Add views with spacing
        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(8, 0, 8, 0)
        }

        floatingView.addView(statusText)
        floatingView.addView(btnRecord, buttonParams)
        floatingView.addView(btnStop, buttonParams)
        floatingView.addView(btnLoop, buttonParams)

        // Dragging gesture for the floating bar
        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingView, params)
    }

    private fun startForegroundServiceNotification() {
        val channelId = "AutoTaskOverlayChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AutoTask Floating Controls",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AutoTask Macro Active")
            .setContentText("Floating Start/Stop buttons are active over your screen")
            .setSmallIcon(R.drawable.ic_notification)
            .build()

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
