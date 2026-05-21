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

    fun hasCameraPermission(ctx: Context): Boolean {
        return try {
            ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "hasCameraPermission: ${e.javaClass.simpleName} ${e.message}")
            false
        }
    }

    fun hasOverlayPermission(ctx: Context): Boolean {
        return try {
            Settings.canDrawOverlays(ctx)
        } catch (e: Exception) {
            Log.e(TAG, "hasOverlayPermission: ${e.javaClass.simpleName} ${e.message}")
            false
        }
    }

    fun isAccessibilityServiceEnabled(ctx: Context): Boolean {
        return try {
            val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.packageName == ctx.packageName }
        } catch (e: Exception) {
            Log.e(TAG, "isAccessibilityServiceEnabled: ${e.javaClass.simpleName} ${e.message}")
            false
        }
    }

    fun overlaySettingsIntent(ctx: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${ctx.packageName}")
        )
    }

    fun accessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }
}
