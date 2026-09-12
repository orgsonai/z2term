package com.zerotoship.z2term.service

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/** Android observations are authoritative, including changes made by other applications. */
object TorchState {
    private val lock = Object()
    private var manager: CameraManager? = null
    private var camera: String? = null
    private val observation = TorchObservation()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) = update(cameraId, enabled)
        // Android disables torch mode before reporting it unavailable.
        override fun onTorchModeUnavailable(cameraId: String) = update(cameraId, false)
    }

    private fun update(id: String, on: Boolean) {
        synchronized(lock) {
            if (id != camera) return
            observation.update(on)
        }
        listeners.forEach { it() }
    }

    fun start(context: Context) = synchronized(lock) {
        if (manager != null) return@synchronized
        val cm = context.applicationContext.getSystemService(CameraManager::class.java)
        val cameras = cm.cameraIdList.filter {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        camera = cameras.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: cameras.firstOrNull() ?: throw IllegalStateException("No camera flash available")
        cm.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        manager = cm
    }

    fun stop() = synchronized(lock) {
        manager?.unregisterTorchCallback(callback)
        manager = null; camera = null; observation.update(null)
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    fun current(): Boolean? = observation.current()

    /** Called on the API worker. Never infer toggle state from a previous command. */
    fun command(context: Context, mode: String): String {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Torch commands must run off the UI thread" }
        start(context)
        val on = when (mode.lowercase()) {
            "on", "1", "true" -> true
            "off", "0", "false" -> false
            "toggle", "" -> !observation.await()
            "status" -> return if (observation.await()) "on" else "off"
            else -> throw IllegalArgumentException("usage: on | off | toggle | status")
        }
        val (cm, id) = synchronized(lock) { manager!! to camera!! }
        cm.setTorchMode(id, on)
        // Await the observation so a following toggle does not use the pre-command state.
        return if (observation.await(on)) "on" else "off"
    }
}
