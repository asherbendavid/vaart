package cvc.dashingdog.vaart

import android.os.Build

/**
 * Small shared utility for a human-readable device identifier.
 * Kept separate (rather than inlined at each call site) since it's a natural candidate
 * for reuse beyond GPX export — e.g. debug panel, future crash/log context.
 */
object DeviceInfo {

    /** e.g. "HUAWEI ELE-L29", "HISENSE U964" */
    fun deviceId(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
}
