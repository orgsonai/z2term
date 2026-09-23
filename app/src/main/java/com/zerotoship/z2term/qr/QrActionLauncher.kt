package com.zerotoship.z2term.qr

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PersistableBundle
import android.provider.CalendarContract
import android.provider.ContactsContract.Intents.Insert
import android.provider.ContactsContract.RawContacts
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.net.toUri

/** Opens a [QrAction] in the app that owns it. Called only from the review screen's button. */
internal object QrActionLauncher {
    enum class Outcome { OPENED, NO_APP, WIFI_PASSWORD_COPIED }

    fun open(activity: Activity, action: QrAction): Outcome =
        if (action is QrAction.Wifi) wifi(activity, action) else start(activity, intent(action))

    private fun start(activity: Activity, intent: Intent): Outcome = try {
        activity.startActivity(intent)
        Outcome.OPENED
    } catch (_: ActivityNotFoundException) {
        Outcome.NO_APP
    }

    private fun intent(action: QrAction): Intent = when (action) {
        // App links, such as a LINE login URL, open in their app; other URLs fall back to a browser.
        is QrAction.Web -> Intent(Intent.ACTION_VIEW, action.uri.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        // BROWSABLE keeps other schemes to activities that accept links from web pages.
        is QrAction.Link -> Intent(Intent.ACTION_VIEW, action.uri.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        is QrAction.Dial -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", action.number, null))
        is QrAction.Email -> Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
            val to = action.to.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (to.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, to.toTypedArray())
            if (action.subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, action.subject)
            if (action.body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, action.body)
        }
        is QrAction.Sms -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", action.number, null)).apply {
            if (action.body.isNotEmpty()) putExtra("sms_body", action.body)
        }
        is QrAction.Contact -> Intent(Insert.ACTION).setType(RawContacts.CONTENT_TYPE).apply {
            if (action.name.isNotEmpty()) putExtra(Insert.NAME, action.name)
            listOf(Insert.PHONE, Insert.SECONDARY_PHONE, Insert.TERTIARY_PHONE).zip(action.phones)
                .forEach { (key, value) -> putExtra(key, value) }
            listOf(Insert.EMAIL, Insert.SECONDARY_EMAIL, Insert.TERTIARY_EMAIL).zip(action.emails)
                .forEach { (key, value) -> putExtra(key, value) }
            if (action.organization.isNotEmpty()) putExtra(Insert.COMPANY, action.organization)
            if (action.title.isNotEmpty()) putExtra(Insert.JOB_TITLE, action.title)
            if (action.address.isNotEmpty()) putExtra(Insert.POSTAL, action.address)
            if (action.note.isNotEmpty()) putExtra(Insert.NOTES, action.note)
        }
        is QrAction.Event -> Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            if (action.title.isNotEmpty()) putExtra(CalendarContract.Events.TITLE, action.title)
            if (action.location.isNotEmpty()) putExtra(CalendarContract.Events.EVENT_LOCATION, action.location)
            if (action.description.isNotEmpty()) putExtra(CalendarContract.Events.DESCRIPTION, action.description)
            action.begin?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
            action.end?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            if (action.allDay) putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        }
        is QrAction.Wifi -> error("Wi-Fi uses the settings flow")
    }

    private fun wifi(activity: Activity, wifi: QrAction.Wifi): Outcome {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            suggestion(wifi)?.let { suggestion ->
                val add = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                    .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
                if (start(activity, add) == Outcome.OPENED) return Outcome.OPENED
            }
        }
        // Android 10, WEP and enterprise networks: the Wi-Fi panel with the password on the clipboard.
        if (wifi.password.isNotEmpty()) {
            val clip = ClipData.newPlainText("Wi-Fi", wifi.password)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            }
            activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        }
        val opened = start(activity, Intent(Settings.Panel.ACTION_WIFI))
        return if (opened == Outcome.OPENED && wifi.password.isNotEmpty()) Outcome.WIFI_PASSWORD_COPIED else opened
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun suggestion(wifi: QrAction.Wifi): WifiNetworkSuggestion? = runCatching {
        val builder = WifiNetworkSuggestion.Builder().setSsid(wifi.ssid).setIsHiddenSsid(wifi.hidden)
        when (wifi.security.uppercase()) {
            "NOPASS" -> require(wifi.password.isEmpty())
            "WPA", "WPA2" -> builder.setWpa2Passphrase(wifi.password)
            "SAE", "WPA3" -> builder.setWpa3Passphrase(wifi.password)
            else -> error("Unsupported Wi-Fi security")
        }
        builder.build()
    }.getOrNull()
}
