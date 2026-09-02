package com.trio.today.domain

/**
 * Manufacturer-specific steps for keeping reminders alive.
 *
 * This is the second half of the P0 reliability requirement (§6). Exact alarms
 * get past Android's own Doze, but Samsung's "sleeping apps", Xiaomi's MIUI
 * autostart and the Oppo/Realme/Vivo power managers can still put the app to
 * sleep entirely. There is no API to detect or fix that, so the honest answer
 * -- the one Microsoft, Zoho and independent developers all ship -- is to
 * detect the brand and walk the user to the right settings screen.
 *
 * The mapping lives here, free of Android imports, so it can be unit tested.
 * A typo in this table means an affected user silently gets no guidance, and
 * the symptom (reminders that never arrive) looks nothing like the cause.
 *
 * @param settingsTargets package/activity pairs to try in order. They are
 *   undocumented and frequently renamed between firmware versions, so several
 *   are listed per brand and callers are expected to find none resolvable.
 */
data class OemGuidance(
    val brandLabel: String,
    val steps: List<String>,
    val settingsTargets: List<Pair<String, String>>,
)

object OemGuidanceTable {

    /** Returns the extra steps for [manufacturer], or null when none are needed. */
    fun forManufacturer(manufacturer: String): OemGuidance? =
        when (manufacturer.lowercase().trim()) {
            "samsung" -> OemGuidance(
                brandLabel = "Samsung",
                steps = listOf(
                    "Open Settings > Battery > Background usage limits",
                    "Check that Trio is not in \"Sleeping apps\" or \"Deep sleeping apps\"",
                    "Turn off \"Put unused apps to sleep\" for Trio",
                ),
                settingsTargets = listOf(
                    "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
                    "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
                ),
            )

            "xiaomi", "redmi", "poco" -> OemGuidance(
                brandLabel = "Xiaomi",
                steps = listOf(
                    "Open Settings > Apps > Manage apps > Trio",
                    "Turn on \"Autostart\"",
                    "Set \"Battery saver\" to \"No restrictions\"",
                ),
                settingsTargets = listOf(
                    "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    "com.miui.securitycenter" to "com.miui.powercenter.PowerSettings",
                ),
            )

            "huawei", "honor" -> OemGuidance(
                brandLabel = "Huawei",
                steps = listOf(
                    "Open Settings > Battery > App launch",
                    "Switch Trio to \"Manage manually\"",
                    "Turn on Auto-launch, Secondary launch and Run in background",
                ),
                settingsTargets = listOf(
                    "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                    "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
                ),
            )

            "oppo" -> oppoFamily("OPPO")
            "realme" -> oppoFamily("realme")

            "vivo", "iqoo" -> OemGuidance(
                brandLabel = "vivo",
                steps = listOf(
                    "Open Settings > Battery > High background power consumption",
                    "Allow Trio to run in the background",
                    "In i Manager > App manager > Autostart, turn Trio on",
                ),
                settingsTargets = listOf(
                    "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                    "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
                ),
            )

            "oneplus" -> OemGuidance(
                brandLabel = "OnePlus",
                steps = listOf(
                    "Open Settings > Battery > Battery optimization",
                    "Set Trio to \"Don't optimize\"",
                    "Turn off \"Advanced optimization\" > \"Deep optimization\"",
                ),
                settingsTargets = listOf(
                    "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
                ),
            )

            else -> null
        }

    private fun oppoFamily(label: String) = OemGuidance(
        brandLabel = label,
        steps = listOf(
            "Open Settings > Battery > App battery management",
            "Set Trio to \"Allow background running\"",
            "In Settings > Apps > Trio, turn on \"Auto-start\"",
        ),
        settingsTargets = listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        ),
    )
}
