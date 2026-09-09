package com.zerotoship.z2term.edge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import com.zerotoship.z2term.automation.ActionDefinition

/** Cancellation suppresses successors; keep the stroke reserved until Android ends it. */
internal class AndroidStroke(private val service: AccessibilityService) {
    private val main = Handler(Looper.getMainLooper())
    private var flight: Any? = null
    val inFlight get() = flight != null
    fun dispatch(step: ActionDefinition.Step.Stroke, screen: ActionDefinition.Screen, done: (String?) -> Unit): () -> Unit {
        check(!inFlight) { "Wait for the previous gesture to finish" }
        val p = step.points(screen)
        val path = Path().apply { moveTo(p[0], p[1]); if (p[0] != p[2] || p[1] != p[3]) lineTo(p[2], p[3]) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, step.ms)).build()
        val token = Any()
        var deliver = true
        flight = token
        val deadline = Runnable {
            if (flight === token) {
                flight = null
                if (deliver) { deliver = false; done("Android did not confirm gesture completion") }
            }
        }
        fun finish(error: String?) {
            if (flight !== token) return
            flight = null
            main.removeCallbacks(deadline)
            if (deliver) { deliver = false; done(error) }
        }
        main.postDelayed(deadline, step.ms + 1500)
        try {
            if (!service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = finish(null)
                override fun onCancelled(gestureDescription: GestureDescription?) = finish("Android cancelled the gesture")
            }, main)) finish("Android rejected the gesture")
        } catch (e: Exception) { finish(e.message ?: "Gesture failed") }
        return { deliver = false }
    }
}
