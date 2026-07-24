package cvc.dashingdog.vaart

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles the filesystem side of GPX export: building a safe filename, writing the
 * GPX string into the app's cacheDir, and sweeping stale exports on launch.
 *
 * Files live in cacheDir because they're transient — created only to be handed off
 * through the share sheet, not meant to persist. See EXPORT_MAX_AGE_MS.
 */
object GpxExportHelper {

    private const val EXPORT_SUBDIR = "gpx_exports"
    private val EXPORT_MAX_AGE_MS = 60 * 60 * 1000L // 1 hour

    /**
     * Builds a filesystem-safe filename: "VehicleName yyyy-MM-dd HH-mm.gpx".
     * Dashes instead of slashes/colons — "/" would be read as a path separator,
     * ":" is rejected outright by Windows (relevant since these files may end up
     * there via LocalSend / the GPX Route Player).
     */
    fun buildFileName(vehicleName: String, startTime: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault())
        val dateStr = fmt.format(Date(startTime))
        val safeVehicleName = sanitize(vehicleName)
        return "$safeVehicleName $dateStr.gpx"
    }

    /** Strips characters invalid in filenames on Android/Windows, just in case a vehicle name has any. */
    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()

    private fun exportDir(context: Context): File {
        val dir = File(context.cacheDir, EXPORT_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Writes the GPX string to cacheDir/gpx_exports/<fileName> and returns the File.
     * Any previous file with the same name is overwritten.
     */
    fun writeToCache(context: Context, fileName: String, gpxContent: String): File {
        val file = File(exportDir(context), fileName)
        file.writeText(gpxContent)
        return file
    }

    /**
     * Deletes any export older than EXPORT_MAX_AGE_MS. Intended to run once per app
     * launch (not a background job) — cheap, and avoids the cache growing unbounded
     * on low-storage devices if a share target never actually reads the file.
     */
    fun cleanupStaleExports(context: Context) {
        val dir = exportDir(context)
        val cutoff = System.currentTimeMillis() - EXPORT_MAX_AGE_MS
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }
}