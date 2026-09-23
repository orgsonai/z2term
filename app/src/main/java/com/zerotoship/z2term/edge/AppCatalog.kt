package com.zerotoship.z2term.edge

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import androidx.core.graphics.createBitmap

object AppCatalog {
    data class LaunchableApp(val packageName: String, val label: String)

    @Suppress("DEPRECATION")
    fun launchableApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }
            .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedWith(compareBy<LaunchableApp> { it.label }.thenBy { it.packageName })
    }

    @Suppress("DEPRECATION")
    fun command(context: Context, args: List<String>): String {
        val pm = context.packageManager
        return when {
            args == listOf("pick") -> AppPickerActivity.pick(context)
            args == listOf("list") -> {
                JSONArray(launchableApps(context).map {
                    JSONObject().put("package", it.packageName).put("label", it.label)
                }).toString()
            }
            args.size == 2 && args[0] == "icon" -> {
                val bitmap = bitmap(pm.getApplicationIcon(args[1]))
                val bytes = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
                bitmap.recycle()
                Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            }
            else -> throw IllegalArgumentException("z2-app: list | icon <package> -o <file.png>")
        }
    }

    fun bitmap(drawable: Drawable, size: Int = 96): Bitmap {
        val bitmap = createBitmap(size, size)
        val old = android.graphics.Rect(drawable.bounds)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        drawable.bounds = old
        return bitmap
    }
}
