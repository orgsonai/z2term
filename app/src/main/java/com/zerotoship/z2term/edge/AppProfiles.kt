package com.zerotoship.z2term.edge

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/**
 * Apps in the other profiles of this device (for example a work profile), through the same public API a launcher uses.
 * A profile is named by its serial number, which survives reboots, unlike the user id.
 * Profiles Android does not show to this app (such as a private space) are not listed and cannot be named.
 */
object AppProfiles {
    private fun launcher(context: Context) = context.getSystemService(LauncherApps::class.java)
    private fun users(context: Context) = context.getSystemService(UserManager::class.java)

    fun serial(context: Context, user: UserHandle): Long = users(context).getSerialNumberForUser(user)

    /** Profiles other than the one z2term runs in. */
    fun others(context: Context): List<UserHandle> =
        launcher(context).profiles.filter { it != Process.myUserHandle() }

    /** The profile for [serial]. A profile that is gone or not visible to this app is an error, never a silent fallback. */
    fun user(context: Context, serial: Long): UserHandle {
        val user = users(context).getUserForSerialNumber(serial)
        require(user != null && user in launcher(context).profiles) { "intent: no such profile $serial" }
        return user
    }

    fun isOwn(user: UserHandle) = user == Process.myUserHandle()

    /** Launchable activities of every other profile. A paused profile lists nothing. */
    fun activities(context: Context): List<LauncherActivityInfo> = others(context).flatMap { user ->
        runCatching { launcher(context).getActivityList(null, user) }.getOrDefault(emptyList())
    }

    fun component(context: Context, pkg: String, user: UserHandle): ComponentName =
        launcher(context).getActivityList(pkg, user).firstOrNull()?.componentName
            ?: throw IllegalArgumentException("intent: package has no launchable activity in profile ${serial(context, user)}")

    /** The app icon with the profile badge Android draws for that profile. */
    fun icon(context: Context, pkg: String, serial: Long): Drawable? =
        launcher(context).getActivityList(pkg, user(context, serial)).firstOrNull()?.getBadgedIcon(0)

    fun start(context: Context, component: ComponentName, user: UserHandle, options: Bundle?) {
        check(!users(context).isQuietModeEnabled(user)) { "intent: profile ${serial(context, user)} is paused" }
        launcher(context).startMainActivity(component, user, null, options)
    }
}
