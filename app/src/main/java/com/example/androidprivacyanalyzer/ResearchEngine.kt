package com.example.androidprivacyanalyzer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ResearchEngine {

    data class ResearchResult(
        val expectedPermissions: Set<String>,
        val source: String,           // "playstore", "exodus", "heuristic"
        val confidence: Double,       // 0.0 to 1.0
        val evidence: List<String>    // Human-readable sources
    )

    private const val EXODUS_API = "https://reports.exodus-privacy.eu.org/api/search"
    private const val PLAY_BATCH_API =
        "https://play.google.com/_/PlayStoreUi/data/batchexecute" +
            "?rpcids=xdSrCf&f.sid=-1&bl=boq_playuiserver&hl=en&gl=US" +
            "&authuser&soc-app=121&soc-platform=1&soc-device=1&_reqid=10000"
    private const val NETWORK_TIMEOUT_MS = 3000

    private val cache = mutableMapOf<String, ResearchResult>()

    /**
     * Main entry point. Tries sources in order:
     *
     *   1. Google Play Store (xdSrCf batchexecute) — developer-declared
     *      permissions for the current listing; most up-to-date.
     *   2. Exodus Privacy — static-analysis report of a specific APK version.
     *   3. Package-name heuristic.
     *
     * (Manifest analysis removed — circular: an app's own declared permissions
     * can never prove the app overreaches. The manifest now feeds the observed
     * side of the TrustEngine pipeline.)
     */
    suspend fun research(
        packageName: String
    ): ResearchResult = withContext(Dispatchers.IO) {

        // Return cached result if available
        cache[packageName]?.let { return@withContext it }

        // 1. Google Play Store first — freshest developer-declared baseline
        val playResult = fetchFromPlayStore(packageName)
        if (playResult != null) {
            cache[packageName] = playResult
            return@withContext playResult
        }

        // 2. Exodus Privacy second — verified static-analysis reports
        val exodusResult = fetchExodus(packageName)
        if (exodusResult != null) {
            cache[packageName] = exodusResult
            return@withContext exodusResult
        }

        // 3. Package-name heuristic fallback
        val heuristicResult = heuristicFallback(packageName)
        cache[packageName] = heuristicResult
        return@withContext heuristicResult
    }

    // ─────────────────────────────────────────────────────────
    // SOURCE 1: Google Play Store (batchexecute / xdSrCf)
    //
    // Scrapes the "App permissions" panel of the app's Play Store listing —
    // the permissions the developer has declared for the CURRENT version.
    // This is the freshest public record of what the app claims to need.
    // NOTE: uses Google's unofficial batchexecute endpoint (same one used
    // by Play scraper libraries); Google may change it without notice.
    // ─────────────────────────────────────────────────────────
    private fun fetchFromPlayStore(packageName: String): ResearchResult? {
        return try {
            val payload = buildPlayStoreRequest(packageName)

            val conn = URL(PLAY_BATCH_API).openConnection() as HttpURLConnection
            conn.connectTimeout = NETWORK_TIMEOUT_MS
            conn.readTimeout = NETWORK_TIMEOUT_MS
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

            conn.outputStream.use { it.write(payload.toByteArray()) }

            if (conn.responseCode != 200) return null
            val response = conn.inputStream.bufferedReader().use { it.readText() }

            parsePlayStoreResponse(response) ?: return null
        } catch (e: Exception) {
            Log.w("ResearchEngine", "Play Store fetch failed for $packageName: ${e.message}")
            null
        }
    }

    /**
     * Builds the batchexecute request body. The xdSrCf RPC returns the app's
     * data-safety / permissions panel as a nested JSON array-of-strings.
     */
    private fun buildPlayStoreRequest(packageName: String): String {
        // Inner request: [[["xdSrCf","[\"com.example.app\",7]",null,"generic"]]]
        val inner = "[\"$packageName\",7]"
        val rpc = "[[\"xdSrCf\",\"${inner.replace("\"", "\\\"")}\",null,\"generic\"]]"
        return "f.req=" + java.net.URLEncoder.encode(rpc, "UTF-8")
    }

    /**
     * The response is: )]}'
     *
     * [ ... , ["wrb.fr","xdSrCf","<escaped-json-string>",null,null,null,"generic"], ... ]
     *
     * The escaped-json-string (element [2]) contains the permissions panel.
     * Its structure: [null,null,[null,[...]], ... , [ ... ]] where two
     * permission-group blocks live at fixed indices. Each group block holds
     * entries of the form [ null, [ ... ], ... [ "android.permission.X" ] ... ].
     * Rather than hard-coding indices, we just scan the whole payload for
     * "android.permission.*" strings — robust against layout shifts.
     */
    private fun parsePlayStoreResponse(response: String): ResearchResult? {
        // Strip the XSS-protection prefix (")]}'
        val body = response.substringAfter("\n", response)

        val root = try {
            org.json.JSONArray(body)
        } catch (_: Exception) {
            null
        } ?: return null

        // Find the xdSrCf payload
        for (i in 0 until root.length()) {
            val entry = root.optJSONArray(i) ?: continue
            if (entry.length() < 3) continue
            if (entry.optString(1) != "xdSrCf") continue

            val payloadStr = entry.optString(2) ?: continue
            // The payload is itself a JSON *array* in string form
            val payload = try {
                org.json.JSONArray(payloadStr)
            } catch (_: Exception) {
                continue
            }

            // Scan the whole nested structure for permission strings.
            val perms = mutableSetOf<String>()
            scanForPermissions(payload, perms)
            if (perms.isNotEmpty()) {
                return ResearchResult(
                    expectedPermissions = perms,
                    source = "playstore",
                    confidence = 0.9,
                    evidence = listOf("Google Play Store listing (current version)")
                )
            }
            return null
        }
        return null
    }

    /** Depth-first scan of the nested JSON for "android.permission.*" strings. */
    private fun scanForPermissions(node: Any?, out: MutableSet<String>) {
        when (node) {
            is org.json.JSONArray -> {
                for (i in 0 until node.length()) {
                    scanForPermissions(node.opt(i), out)
                }
            }
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    scanForPermissions(node.opt(keys.next()), out)
                }
            }
            is String -> {
                if (node.startsWith("android.permission.")) {
                    normalizePermission(node)?.let { out.add(it) }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // SOURCE 2: Exodus Privacy
    // ─────────────────────────────────────────────────────────
    private fun fetchExodus(packageName: String): ResearchResult? {
        return try {
            val url = URL("$EXODUS_API/$packageName")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = NETWORK_TIMEOUT_MS
            conn.readTimeout = NETWORK_TIMEOUT_MS
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                // Exodus returns permissions in the reports
                val reports = json.optJSONArray("reports") ?: return null
                if (reports.length() == 0) return null

                val latestReport = reports.getJSONObject(0)
                val permissions = latestReport.optJSONArray("permissions") ?: return null

                val perms = mutableSetOf<String>()
                for (i in 0 until permissions.length()) {
                    val perm = permissions.getString(i)
                    // Normalize to your capability tokens
                    val cap = normalizePermission(perm)
                    if (cap != null) perms.add(cap)
                }

                ResearchResult(
                    expectedPermissions = perms,
                    source = "exodus",
                    confidence = 0.8,    // Exodus analyzes a specific APK version, often lagging
                    evidence = listOf("Exodus Privacy database report")
                )
            } else null
        } catch (e: Exception) {
            Log.w("ResearchEngine", "Exodus fetch failed: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────
    // SOURCE 3: Heuristic Fallback
    // (Manifest analysis removed — circular: an app's own declared
    // permissions can never prove the app overreaches. The manifest
    // now feeds the observed side of the TrustEngine pipeline.)
    // ─────────────────────────────────────────────────────────
    private fun heuristicFallback(packageName: String): ResearchResult {
        val name = packageName.lowercase()

        val baseline = when {
            name.contains("calculator") || name.contains("calc") ->
                setOf("network") // Some have ads; be conservative

            name.contains("camera") || name.contains("photo") ->
                setOf("camera", "storage", "network", "location")

            name.contains("browser") || name.contains("web") ->
                setOf("network", "storage", "camera", "mic", "location", "contacts")

            name.contains("messenger") || name.contains("chat") || name.contains("sms") ->
                setOf("network", "contacts", "mic", "camera", "storage", "location", "sms", "notifications")

            name.contains("mail") || name.contains("email") ->
                setOf("network", "storage", "contacts", "camera", "notifications")

            name.contains("map") || name.contains("navigat") ->
                setOf("network", "location", "storage", "mic", "camera", "notifications")

            name.contains("bank") || name.contains("finance") || name.contains("pay") ->
                setOf("network", "storage", "location", "camera", "biometric", "contacts", "notifications")

            name.contains("game") || name.contains("play") ->
                setOf("network", "storage", "mic", "camera", "location", "notifications")

            name.contains("music") || name.contains("spotify") || name.contains("stream") ->
                setOf("network", "storage", "mic", "location", "notifications")

            name.contains("social") || name.contains("facebook") || name.contains("insta") || name.contains("tiktok") ->
                setOf("network", "storage", "contacts", "camera", "mic", "location", "notifications")

            name.contains("flashlight") || name.contains("torch") ->
                setOf("camera") // Uses camera LED

            name.contains("keyboard") ->
                setOf("network", "storage", "contacts", "notifications")

            name.contains("launcher") || name.contains("home") ->
                setOf("network", "storage", "contacts", "notifications", "overlay")

            name.contains("vpn") || name.contains("proxy") ->
                setOf("network", "storage", "overlay", "boot", "notifications")

            else -> setOf("network", "storage", "notifications") // Ultra-conservative default
        }

        return ResearchResult(
            expectedPermissions = baseline,
            source = "heuristic",
            confidence = 0.4,
            evidence = listOf("Heuristic inference from app name: '$packageName'")
        )
    }

    // ─────────────────────────────────────────────────────────
    // Permission Normalization (maps Android perms → your tokens)
    // ─────────────────────────────────────────────────────────
    private fun normalizePermission(perm: String): String? {
        return when {
            perm.contains("INTERNET") || perm.contains("NETWORK_STATE") || perm.contains("WIFI_STATE") -> "network"
            perm.contains("SYSTEM_ALERT_WINDOW") -> "overlay"
            perm.contains("RECEIVE_BOOT_COMPLETED") -> "boot"
            perm.contains("READ_CONTACTS") || perm.contains("WRITE_CONTACTS") || perm.contains("GET_ACCOUNTS") -> "contacts"
            perm.contains("RECORD_AUDIO") -> "mic"
            perm.contains("CAMERA") -> "camera"
            perm.contains("LOCATION") -> "location"
            perm.contains("SMS") || perm.contains("MMS") -> "sms"
            perm.contains("CALL_LOG") || perm.contains("OUTGOING_CALLS") -> "call_log"
            perm.contains("EXTERNAL_STORAGE") || perm.contains("MANAGE_EXTERNAL_STORAGE") || perm.contains("READ_MEDIA") -> "storage"
            perm.contains("POST_NOTIFICATIONS") -> "notifications"
            perm.contains("BIOMETRIC") || perm.contains("FINGERPRINT") -> "biometric"
            perm.contains("BLUETOOTH") -> "bluetooth"
            perm.contains("NFC") -> "nfc"
            perm.contains("REQUEST_INSTALL_PACKAGES") -> "install_unknown"
            perm.contains("READ_PHONE_STATE") || perm.contains("CALL_PHONE") -> "phone"
            perm.contains("ACCESSIBILITY") -> "accessibility"
            perm.contains("PACKAGE_USAGE_STATS") -> "usage_stats"
            perm.contains("DRM") -> "drm"
            else -> null
        }
    }
}