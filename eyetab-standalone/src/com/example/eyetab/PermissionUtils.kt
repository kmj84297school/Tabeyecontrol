package com.example.eyetab

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager

object PermissionUtils {

    private const val TAG = "EyeTab"

    fun hasCameraPermission(ctx: Context): Boolean = try {
        ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        Log.e(TAG, "hasCameraPermission", e)
        false
    }

    fun hasOverlayPermission(ctx: Context): Boolean = try {
        Settings.canDrawOverlays(ctx)
    } catch (e: Exception) {
        Log.e(TAG, "hasOverlayPermission", e)
        false
    }

    fun overlaySettingsIntent(ctx: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        android.net.Uri.parse("package:${ctx.packageName}")
    )

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun isAccessibilityServiceEnabled(ctx: Context): Boolean = try {
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == ctx.packageName }
    } catch (e: Exception) {
        Log.e(TAG, "isAccessibilityServiceEnabled", e)
        false
    }
}
