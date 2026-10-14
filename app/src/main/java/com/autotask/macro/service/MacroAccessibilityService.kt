package com.autotask.macro.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

data class RecordedStep(
    val type: StepType,
    val x: Float,
    val y: Float,
    val targetPackage: String? = null,
    val viewId: String? = null,
    val textToInput: String? = null,
    val delayMs: Long = 400L
)

enum class StepType {
    CLICK,
    INPUT_TEXT
}

/**
 * Universal Android Accessibility Service:
 * Operates across ALL external third-party applications (Chrome, WhatsApp, 
 * Enterprise Portals, Google Forms, Games, etc.).
 * 
 * Uses hardware-level dispatchGesture so it works even on apps with no view IDs (Flutter, WebViews, React Native).
 */
class MacroAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MacroAccessibilityService? = null
    }

    private var isRecording = false
    private var isLooping = false
    private val recordedSteps = mutableListOf<RecordedStep>()
    private var lastEventTime = 0L
    private var recordedTargetPackage: String? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var loopJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }

    override fun onInterrupt() {
        stopAll()
    }

    fun startRecording() {
        recordedSteps.clear()
        recordedTargetPackage = null
        isRecording = true
        isLooping = false
        lastEventTime = System.currentTimeMillis()
    }

    fun stopRecording() {
        isRecording = false
    }

    fun stopAll() {
        isRecording = false
        isLooping = false
        loopJob?.cancel()
    }

    fun getRecordedActionsCount(): Int = recordedSteps.size
    fun getTargetPackageName(): String? = recordedTargetPackage

    /**
     * Intercepts touches & keystrokes in ANY application running on Android
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRecording || event == null) return

        // Skip our own floating controller app touches
        val packageName = event.packageName?.toString()
        if (packageName == "com.autotask.macro") return

        // Save target package (e.g. com.android.chrome, com.whatsapp, or your work app)
        if (packageName != null && recordedTargetPackage == null) {
            recordedTargetPackage = packageName
        }

        val currentTime = System.currentTimeMillis()
        val delay = (currentTime - lastEventTime).coerceIn(100L, 3000L)
        lastEventTime = currentTime

        when (event.eventType) {
            // User clicked any element inside the other application
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val node: AccessibilityNodeInfo? = event.source
                val rect = Rect()
                node?.getBoundsInScreen(rect)
                val clickX = if (rect.width() > 0) rect.centerX().toFloat() else 500f
                val clickY = if (rect.height() > 0) rect.centerY().toFloat() else 800f
                val viewId = node?.viewIdResourceName

                recordedSteps.add(
                    RecordedStep(
                        type = StepType.CLICK,
                        x = clickX,
                        y = clickY,
                        targetPackage = packageName,
                        viewId = viewId,
                        textToInput = null,
                        delayMs = delay
                    )
                )
            }

            // User typed text into any field in the other application
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val enteredText = event.text?.joinToString("") ?: ""
                val node: AccessibilityNodeInfo? = event.source
                val rect = Rect()
                node?.getBoundsInScreen(rect)

                if (enteredText.isNotEmpty()) {
                    recordedSteps.add(
                        RecordedStep(
                            type = StepType.INPUT_TEXT,
                            x = rect.centerX().toFloat(),
                            y = rect.centerY().toFloat(),
                            targetPackage = packageName,
                            viewId = node?.viewIdResourceName,
                            textToInput = enteredText,
                            delayMs = delay
                        )
                    )
                }
            }
        }
    }

    /**
     * Replays the recorded tasks into the target external application continuously!
     */
    fun startInfiniteRepeatLoop(onCycleUpdate: (Int) -> Unit) {
        if (recordedSteps.isEmpty()) return

        isRecording = false
        isLooping = true

        // Optionally bring the target external application to the front if minimized
        recordedTargetPackage?.let { targetPkg ->
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(targetPkg)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            } catch (e: Exception) {
                // If launching directly fails, user is already on the target app
            }
        }

        loopJob?.cancel()
        loopJob = serviceScope.launch {
            var cycleNumber = 1

            while (isLooping && isActive) {
                withContext(Dispatchers.Main) {
                    onCycleUpdate(cycleNumber)
                }

                // Execute each step in the external application
                for (step in recordedSteps) {
                    if (!isLooping || !isActive) break

                    delay(step.delayMs)

                    when (step.type) {
                        StepType.CLICK -> {
                            simulateTouchClick(step.x, step.y)
                        }
                        StepType.INPUT_TEXT -> {
                            step.textToInput?.let { text ->
                                simulateTextInput(step.viewId, step.x, step.y, text)
                            }
                        }
                    }
                }

                // Pause before next repetition cycle in the other app
                delay(1200L)
                cycleNumber++
            }
        }
    }

    /**
     * Universal Hardware-Level Touch Click:
     * Works on ALL external apps regardless of whether they are native, Flutter, React Native, or WebViews
     */
    private fun simulateTouchClick(x: Float, y: Float) {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, null, null)
    }

    /**
     * Injects text into form field in external application
     */
    private fun simulateTextInput(viewId: String?, x: Float, y: Float, text: String) {
        val rootNode = rootInActiveWindow
        var targetNode: AccessibilityNodeInfo? = null

        if (viewId != null && rootNode != null) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNotEmpty()) {
                targetNode = nodes[0]
            }
        }

        if (targetNode != null) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } else {
            // Universal fallback: Tap the coordinate to focus field, then type
            simulateTouchClick(x, y)
        }
    }
}