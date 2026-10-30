package com.afterlater.auto

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object AutoDetectPermissions {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, LinkCaptureService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val accessibilityEnabled = runCatching {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
            )
        }.getOrDefault(0) == 1
        if (!accessibilityEnabled) return false
        return enabledServices.split(':').any { service ->
            service.equals(expected.flattenToString(), ignoreCase = true) ||
                service.equals(expected.flattenToShortString(), ignoreCase = true)
        }
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun overlaySettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )
}
