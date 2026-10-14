package com.autotask.macro

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autotask.macro.service.FloatingOverlayService
import com.autotask.macro.service.MacroAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnAccessibilityPermission: Button
    private lateinit var btnLaunchFloatingButtons: Button
    private lateinit var btnStopFloatingButtons: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnAccessibilityPermission = findViewById(R.id.btnAccessibilityPermission)
        btnLaunchFloatingButtons = findViewById(R.id.btnLaunchFloatingButtons)
        btnStopFloatingButtons = findViewById(R.id.btnStopFloatingButtons)

        // Step 1: Grant "Draw Over Other Apps" permission
        btnOverlayPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Floating Overlay Permission already granted!", Toast.LENGTH_SHORT).show()
            }
        }

        // Step 2: Enable Accessibility Service
        btnAccessibilityPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Find 'AutoTask Macro' and toggle it ON", Toast.LENGTH_LONG).show()
        }

        // Step 3: Launch Floating Start/Stop bar over home screen
        btnLaunchFloatingButtons.setOnClickListener {
            if (checkOverlayPermission()) {
                val serviceIntent = Intent(this, FloatingOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "Floating controls launched! Return to home screen.", Toast.LENGTH_LONG).show()
                moveTaskToBack(true) // Minimize to home screen
            } else {
                Toast.makeText(this, "Please grant Floating Overlay permission first", Toast.LENGTH_LONG).show()
            }
        }

        // Stop Floating Bar
        btnStopFloatingButtons.setOnClickListener {
            val serviceIntent = Intent(this, FloatingOverlayService::class.java)
            stopService(serviceIntent)
            MacroAccessibilityService.instance?.stopAll()
            Toast.makeText(this, "Floating controls stopped.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun isAccessibilityEnabled(): Boolean {
        return MacroAccessibilityService.instance != null
    }

    private fun updatePermissionStatus() {
        val overlayGranted = checkOverlayPermission()
        tvOverlayStatus.text = if (overlayGranted) "Granted ✓" else "Not Granted ✗"
        tvOverlayStatus.setTextColor(if (overlayGranted) 0xFF10B981.toInt() else 0xFFEF4444.toInt())

        val accessibilityGranted = isAccessibilityEnabled()
        tvAccessibilityStatus.text = if (accessibilityGranted) "Enabled ✓" else "Disabled ✗"
        tvAccessibilityStatus.setTextColor(if (accessibilityGranted) 0xFF10B981.toInt() else 0xFFEF4444.toInt())
    }
}
