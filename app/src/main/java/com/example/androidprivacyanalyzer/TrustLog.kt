package com.example.androidprivacyanalyzer

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent engine debug log for AppTrap.
 *
 * Every scan session writes a human-readable text file to:
 *
 *   Android/data/com.example.androidprivacyanalyzer/files/AppTrapLogs/
 *     apptrap_engine_<yyyyMMdd_HHmmss>.txt
 *
 * Each analyzed app gets one block that mirrors the TrustEngine pipeline
 * stages, so a tester can see exactly WHY an app received its verdict:
 *
 *   ┌─ [19:40:13.102] com.example.supercalculator (Super Calculator)
 *   │ [1] dataset: MISS
 *   │ [2] research: heuristic (conf 40%)
 *   │     → NOT OBTAINED (conf < 0.5) → package-name heuristic
 *   │     pkg-heuristic: HIT ('Simple Utility') → SUFFICIENT (conf 0.6)
 *   │ [4] MANIFEST   16 manifest perms → 10 capabilities: [boot, call_log, camera, ...]
 *   │ [5] DIFF       unexpected: call_log(+6) camera(+4) contacts(+5) ... → subtotal 39
 *   │ [6] ADJUST     +1 unknown-app; cap=30 (conf 0.60): 39 → 30 [CAPPED]
 *   │ [7] ANOMALY    7a OVERLAY_BOOT_NETWORK(+4) 7b UTILITY_NET_SENSITIVE(+5) 7c SPYWARE_PATTERN(+4) → severity 43
 *   │ [8] VERDICT    SPYWARE-LIKE | severity=43 | conf=MEDIUM (Name) | source=heuristic
 *   └─
 *
 * Every line is also mirrored to Logcat under the tag "AppTrapEngine" for
 * live debugging in Android Studio while the device is connected.
 *
 * Pull the files off a test device with:
 *   adb pull /storage/emulated/0/Android/data/com.example.androidprivacyanalyzer/files/AppTrapLogs/
 */
object TrustLog {

    private const val TAG = "AppTrapEngine"
    private const val DIR_NAME = "AppTrapLogs"
    private const val FILE_PREFIX = "apptrap_engine_"
    private const val KEEP_LOGS = 10   // prune: keep only the newest session files

    /** Master switch — set to false to silence file + logcat engine logging. */
    var enabled = true

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val nameStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private var logFile: File? = null
    private var scanStartMs = 0L
    private val sessionLines = mutableListOf<String>()

    // ── Session lifecycle ─────────────────────────────────────────────

    /**
     * Starts a new session: clears the in-memory transcript and writes the
     * session header (device, dataset, app count) into a fresh log file.
     * Called by MainActivity before every scan.
     */
    fun beginScan(context: Context, totalApps: Int) {
        if (!enabled) return
        synchronized(this) {
            sessionLines.clear()
            scanStartMs = System.currentTimeMillis()
            logFile = resolveLogFile(context)
        }
        write(
            listOf(
                "══════════════════════════════════════════════════",
                "AppTrap Engine Debug Log — session ${stamp.format(Date())}",
                "device       : ${Build.MANUFACTURER} ${Build.MODEL} " +
                    "(Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})",
                "dataset      : ${AppDataset.datasetName()} (${AppDataset.size()} profiles)",
                "apps to scan : $totalApps",
                "log file     : ${logFile?.absolutePath}",
                "══════════════════════════════════════════════════"
            )
        )
    }

    /**
     * Guarantees a session exists, so standalone TrustEngine.analyze() calls
     * (outside a full scan) still get logged to a file.
     */
    fun ensureSession(context: Context) {
        if (!enabled) return
        if (logFile == null) beginScan(context, totalApps = -1)
    }

    /** Writes the scan footer: duration, app count and label summary. */
    fun endScan(totalScanned: Int, labelCounts: Map<String, Int>) {
        if (!enabled) return
        val seconds =
            if (scanStartMs > 0) (System.currentTimeMillis() - scanStartMs) / 1000.0 else 0.0
        write(
            listOf(
                "──────────────────────────────────────────────────",
                "scan finished : ${stamp.format(Date())}",
                "apps scanned  : $totalScanned",
                "duration      : ${"%.1f".format(Locale.US, seconds)} s",
                "label summary : " + labelCounts.entries
                    .sortedBy { it.key }
                    .joinToString(", ") { "${it.key}=${it.value}" },
                "══════════════════════════════════════════════════"
            )
        )
    }

    // ── Writing ───────────────────────────────────────────────────────

    /** Writes one app's full analysis block (all lines at once). */
    fun app(lines: List<String>) = write(lines)

    /** Writes a single line. */
    fun line(text: String) = write(listOf(text))

    private fun write(lines: List<String>) {
        if (!enabled) return
        synchronized(this) {
            sessionLines.addAll(lines)
            try {
                logFile?.let { f -> f.appendText(lines.joinToString("\n", postfix = "\n")) }
            } catch (e: Exception) {
                Log.w(TAG, "failed to write engine log file: ${e.message}")
            }
        }
        lines.forEach { Log.d(TAG, it) }
    }

    // ── Access ────────────────────────────────────────────────────────

    /** In-memory transcript of the current session (since last beginScan). */
    fun currentSession(): String = synchronized(this) { sessionLines.joinToString("\n") }

    /** The log file of the current/last session, if one was started. */
    fun currentLogFile(): File? = synchronized(this) { logFile }

    // ── Internals ─────────────────────────────────────────────────────

    private fun resolveLogFile(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, DIR_NAME)
        dir.mkdirs()

        // Prune old sessions — keep only the newest KEEP_LOGS files.
        dir.listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP_LOGS - 1)
            ?.forEach { it.delete() }

        var file = File(dir, FILE_PREFIX + nameStamp.format(Date()) + ".txt")
        var n = 2
        while (file.exists()) {
            file = File(dir, FILE_PREFIX + nameStamp.format(Date()) + "_$n.txt")
            n++
        }
        return file
    }
}
