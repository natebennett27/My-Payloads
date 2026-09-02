package com.trio.today.reminder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService
import com.trio.today.domain.OemGuidance
import com.trio.today.domain.OemGuidanceTable

/**
 * The Android-facing half of the reminder-reliability guidance.
 *
 * The brand-to-steps mapping lives in [OemGuidanceTable] so it can be unit
 * tested without a device; everything here needs a real [Context].
 */
object OemBatteryGuidance {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService<PowerManager>() ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** The system dialog asking to exempt the app from battery optimisation. */
    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))

    /** The Android 12+ "Alarms & reminders" toggle for this app. */
    fun exactAlarmSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${context.packageName}"))
        } else {
            null
        }

    /** Extra steps for this device, or null when the brand needs none. */
    fun forThisDevice(): OemGuidance? = OemGuidanceTable.forManufacturer(Build.MANUFACTURER)

    /**
     * The first guidance screen this device can actually open, if any.
     *
     * OEM settings activities are undocumented and often renamed between
     * firmware versions, so returning null is expected rather than
     * exceptional -- the written steps still get the user there.
     */
    fun resolveSettingsIntent(context: Context, guidance: OemGuidance): Intent? =
        guidance.settingsTargets
            .asSequence()
            .map { (pkg, cls) ->
                Intent().setComponent(ComponentName(pkg, cls))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            .firstOrNull { context.packageManager.resolveActivity(it, 0) != null }
}
