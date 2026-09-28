package com.example.androidprivacyanalyzer

import android.Manifest
import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * AppTrap TrustEngine — permission-mismatch pipeline.
 *
 * Architecture — mirrors the project flowchart stage-for-stage:
 *
 *   STAGE 1  Dataset lookup (AppDataset.getProfile, exact package match)
 *              └ found? → expected baseline (conf 1.0)
 *
 *   STAGE 2  Research Engine (Exodus → Play Store → package heuristic)
 *              └ baseline obtained? (conf ≥ 0.5) → expected baseline
 *                The built-in package heuristic is what covers sideloaded
 *                APKs present in neither Exodus nor Play.
 *
 *   PACKAGE-NAME HEURISTIC (fallback box) — display-name role inference
 *              └ sufficient? (recognizable name → conf 0.6) → baseline
 *
 *   STAGE 3  K-NN machine learning (Jaccard similarity, K=5 neighbours,
 *            majority role vote → expected baseline → ML confidence)
 *              └ sufficient? (conf ≥ 0.4) → baseline
 *
 *   GENERIC FALLBACK ("General": network, storage, notifications)
 *
 *   STAGE 4  Manifest analysis: extract declared permissions →
 *            normalizePermission() → observed capability set.
 *            (Extracted before the K-NN runs because the classifier
 *            consumes it. The manifest NEVER builds the expected side —
 *            that would be circular.)
 *
 *   STAGE 5  Expected vs observed differential + permission weighting
 *            (weighted caps → severity points, info-only caps → signals).
 *
 *   STAGE 6  Source/confidence adjustment (unknown-app penalty only when
 *            something weighted was unexpected + confidence ceiling).
 *
 *   STAGE 7  Cross-permission anomaly rules (7a–7f). Run on BOTH the match
 *            and mismatch paths — a perfect diff must not hide a suspicious
 *            capability combo. Role exemption sets + OEM bypass live here.
 *
 *   STAGE 8  Final assessment → TrustVerdict (role, source, confidence,
 *            observed/unexpected caps, severity 0–100, risk label).
 *
 * Refinements kept beyond the flowchart (each validated on-device):
 *   • dataset baselines widened with common-modern caps (biometric/boot/…)
 *   • display-name role widening for research baselines
 *   • OEM platform caps for manufacturer-signed packages
 *   • "General"/unknown roles skip per-capability penalties
 *
 * Ranking → Top 10 → Mitigation → Android App Info all live in
 * MainActivity / DetailActivity; this engine only produces the verdict.
 */
@Suppress("DEPRECATION")
object TrustEngine {

    // ── Severity caps for unknown-app baselines (STAGE 6) ──────────
    // Lower confidence → higher ceiling, because we don't want to bury a
    // genuinely suspicious app just because we couldn't classify it.
    private val SEVERITY_CAPS = listOf(
        0.8 to 20,
        0.5 to 30,
        0.4 to 35,
        0.0 to 45
    )

    // Roles that legitimately need overlay+boot+network — not flagged by STAGE 7.
    // Role names follow the Google Play Store categories in dataset_final.csv.
    private val OVERLAY_BOOT_NETWORK_EXEMPT = setOf(
        "Communications / Messaging",
        "Security / Antivirus",
        "System Tools",
        "System Tool / Launcher",
        "Media / Photo Editing",
        "Media / Camera",
        "Maps / Navigation",
        "Video Streaming",
        "Smart Home / IoT",
        "Productivity",
        "Web Browser",
        "Social Media",
        "Email Client",
        "Cloud Storage",
        "Music Streaming",
        "Games",
        "Finance / Banking",
        "Family Safety / Location",
        "Voice Recorder / Transcription",
        "VPN / Proxy",
        "AI Assistant",
        "News / RSS",
        // File-sharing apps run persistent transfer services (boot) and
        // notification/quick-toggle overlays.
        "File Sharing"
    )

    // Roles where SMS+contacts+location+network is expected (not flagged).
    private val SPYWARE_PATTERN_EXEMPT = setOf(
        "Communications / Messaging",
        "Security / Antivirus",
        "Social Media",
        "Finance / Banking",
        "System Tools",
        "Maps / Navigation",
        // File-sharing apps use SMS (download links) + contacts (send to
        // contact) as baseline-expected features — the differential already
        // scores genuinely-unexpected caps, the combo rule would double-count.
        "File Sharing"
    )

    // Roles where accessibility / usage_stats is a legitimate core feature
    // (antivirus scanning, digital wellbeing, launchers). Everywhere else
    // these capabilities are a major red flag — see anomaly rules 7d/7e.
    private val ACCESSIBILITY_EXPECTED_ROLES = setOf(
        "Security / Antivirus",
        "System Tools",
        "System Tool / Launcher",
        "Productivity",
        "Family Safety / Location"
    )

    // OEM platform-package prefixes. Apps signed by the device manufacturer
    // routinely use overlay (floating windows), boot services, BT accessories
    // and OEM install flows as platform features — not overreach. These caps
    // are added to the expected baseline for OEM packages so the differential
    // only flags genuinely suspicious capabilities, not platform behavior.
    private val OEM_PREFIXES = listOf(
        "com.miui.", "com.xiaomi.", "com.android.", "com.samsung.",
        "com.sec.android", "com.huawei.", "com.hihonor.", "com.oplus.",
        "com.oppo.", "com.coloros.", "com.vivo.", "com.bbk.",
        "com.oneplus.", "com.google.android.", "com.heytap."
    )

    // Capabilities that are normal platform behavior for OEM packages.
    // (Overlay = floating window; boot = sync service; bluetooth = accessory
    // support; install_unknown = OEM updater / package installer.)
    private val OEM_PLATFORM_CAPS = setOf(
        "overlay", "boot", "bluetooth", "install_unknown"
    )

    private fun isOemPackage(packageName: String): Boolean =
        OEM_PREFIXES.any { packageName.startsWith(it) }

    fun analyze(
        context: Context,
        packageName: String,
        appName: String,
        perms: List<String>
    ): TrustVerdict {

        // ── STAGES 1–3: Baseline resolution (one source wins) ────────
        // Dataset → Research → package-name heuristic → K-NN → Generic.
        // First sufficient source sets the expected baseline; the thresholds
        // match the flowchart's decision diamonds (0.5 / 0.6 / 0.4).
        // The observed side is extracted first because the K-NN consumes it.
        TrustLog.ensureSession(context)
        val observed = extractObservedCapabilities(perms)   // STAGE 4 (manifest analysis)
        val baseline = resolveBaseline(context, packageName, appName, observed)

        val claimedRole = baseline.role
        val expected = baseline.expected
        val confidence = baseline.confidenceLabel
        val confidenceScore = baseline.confidenceScore
        val baselineSource = baseline.source

        // ── STAGE 5: Expected vs observed differential ───────────────
        // "General"/"Unknown" roles have no meaningful baseline to diff against,
        // so we skip per-capability penalties and rely on anomaly rules only.
        val roleIsKnown = claimedRole != "General" && claimedRole != "Unknown"
        val unexpected = if (roleIsKnown) observed.filter { it !in expected }.sorted() else emptyList()
        // Zero-weight capabilities (phone/bluetooth/nfc) contribute nothing to
        // severity but previously still generated "unexpected" signals and the
        // +1 unknown-app penalty — producing severity 1–2 flags on apps like
        // Steam/Viu/redBus whose only "issue" was a weight-0 capability. They
        // are now informational only: mentioned in the verdict's signals but
        // excluded from the penalty-driving unexpected set.
        val unexpectedWeighted = unexpected.filter { capabilityWeight(it) > 0 }
        val unexpectedInfoOnly = unexpected.filter { capabilityWeight(it) == 0 }
        val reasons = mutableListOf<String>()
        var severity = 0

        val diffTrace = mutableListOf<String>()
        diffTrace += "[4] MANIFEST    ${perms.size} manifest perms → " +
            "${observed.size} capabilities: [${observed.sorted().joinToString(", ")}]"
        diffTrace += "    expected (${baselineSource}): [${expected.sorted().joinToString(", ")}]"

        if (!roleIsKnown) {
            reasons.add("Role not recognized; scored on behavior patterns only")
            diffTrace += "[5] DIFF        role unknown → per-capability penalty SKIPPED"
        } else if (unexpected.isEmpty()) {
            reasons.add("Requested permissions match the expected behavior for $claimedRole")
            diffTrace += "[5] DIFF        no unexpected capabilities (role=$claimedRole)"
        } else {
            val parts = unexpectedWeighted.joinToString(" ") { cap ->
                val w = capabilityWeight(cap)
                severity += w
                reasons.add("Unexpected capability '$cap' for $claimedRole app")
                "${cap}(+$w)"
            }
            if (unexpectedInfoOnly.isNotEmpty()) {
                reasons.add("Informational: ${unexpectedInfoOnly.joinToString(", ")} " +
                    "present but carries no privacy weight")
                diffTrace += "    info-only (weight 0, no penalty): " +
                    unexpectedInfoOnly.joinToString(", ")
            }
            diffTrace += "[5] DIFF        unexpected for $claimedRole: " +
                (parts.ifEmpty { "(all zero-weight)" })
        }
        val severityAfterDiff = severity

        // ── STAGE 6: Source/confidence adjustments ───────────────────
        val adjustTrace = mutableListOf<String>()
        if (baselineSource != "dataset") {
            // Unknown-app penalty ONLY when something weighted was unexpected.
            // A clean unknown app is not suspicious on its own, and neither is
            // one whose only "unexpected" caps carry zero privacy weight.
            if (unexpectedWeighted.isNotEmpty()) {
                severity += 1
                adjustTrace += "[6] ADJUST      +1 unknown-app (had unexpected caps)"
            } else {
                adjustTrace += "[6] ADJUST      no penalty (clean or info-only app)"
            }
            reasons.add(
                "App not in dataset; baseline confidence: ${(confidenceScore * 100).toInt()}%"
            )

            val maxSeverity = SEVERITY_CAPS.first { confidenceScore >= it.first }.second
            if (severity > maxSeverity) {
                severity = maxSeverity
                reasons.add("Severity capped at $maxSeverity due to baseline confidence level")
                adjustTrace += "    cap=$maxSeverity (conf ${(confidenceScore * 100).toInt()}%): " +
                    "$severityAfterDiff → $severity [CAPPED]"
            } else {
                adjustTrace += "    cap=$maxSeverity (conf ${(confidenceScore * 100).toInt()}%): " +
                    "not applied ($severity ≤ $maxSeverity)"
            }
        } else {
            adjustTrace += "[6] ADJUST      skipped (${baselineSource} baseline, conf=1.0)"
        }

        // ── STAGE 7: Cross-permission anomaly rules ──────────────────
        // NOTE: these run on BOTH the match and mismatch paths (a kept
        // refinement) — a perfect diff must not hide a suspicious combo.
        val anomalyTrace = mutableListOf<String>()
        var anomaliesFired = 0

        // 7a. overlay + boot + network outside exempt roles
        if ("overlay" in observed && "boot" in observed && "network" in observed) {
            if (claimedRole !in OVERLAY_BOOT_NETWORK_EXEMPT &&
                !isOemPackage(packageName)
            ) {
                severity += 4
                anomaliesFired++
                reasons.add("Suspicious combo: overlay + boot + network (possible stalkerware/dropper pattern)")
                anomalyTrace += "    7a OVERLAY_BOOT_NETWORK: FIRED (+4) — role '$claimedRole' not exempt"
            } else {
                anomalyTrace += "    7a OVERLAY_BOOT_NETWORK: observed but role '$claimedRole' exempt"
            }
        }

        // 7b. Calculator / flashlight + network + sensitive (role-based, not name-based)
        if ("network" in observed && claimedRole == "Simple Utility") {
            if (observed.any { it in setOf("contacts", "location", "sms", "call_log", "mic", "camera") }) {
                severity += 5
                anomaliesFired++
                reasons.add("CRITICAL: Simple utility app ('$appName') requests network + sensitive permissions")
                anomalyTrace += "    7b UTILITY_NET_SENSITIVE: FIRED (+5)"
            }
        }

        // 7c. SMS + contacts + location + network outside messaging-like roles
        if ("sms" in observed && "contacts" in observed &&
            "location" in observed && "network" in observed
        ) {
            if (claimedRole !in SPYWARE_PATTERN_EXEMPT) {
                severity += 4
                anomaliesFired++
                reasons.add("Spyware pattern: SMS + contacts + location + network outside messaging role")
                anomalyTrace += "    7c SPYWARE_PATTERN: FIRED (+4)"
            }
        }

        // 7d. Accessibility service outside expected roles.
        // Accessibility can read screen content and inject input events —
        // legitimate for antivirus/system-tool/digital-wellbeing apps, a major
        // red flag everywhere else (especially games, calculators, utilities).
        if ("accessibility" in observed && claimedRole !in ACCESSIBILITY_EXPECTED_ROLES) {
            severity += 7
            anomaliesFired++
            reasons.add("HIGH: Accessibility service requested outside a security/system-tool role " +
                "(can read screen and inject input)")
            anomalyTrace += "    7d ACCESSIBILITY: FIRED (+7) — role '$claimedRole' not in expected set"
        }

        // 7e. Usage-stats outside expected roles.
        // Usage stats reveal which apps the user opens and for how long —
        // legitimately used by antivirus/digital-wellbeing/launchers, a
        // privacy red flag elsewhere.
        if ("usage_stats" in observed && claimedRole !in ACCESSIBILITY_EXPECTED_ROLES) {
            severity += 5
            anomaliesFired++
            reasons.add("MEDIUM: Usage-stats access outside security/system-tool role " +
                "(reveals all app usage patterns)")
            anomalyTrace += "    7e USAGE_STATS: FIRED (+5) — role '$claimedRole' not in expected set"
        }

        // 7f. DRM capability outside streaming roles.
        // DRM is expected for Video/Music streaming apps (105+1 in dataset);
        // anywhere else it's odd but not high-risk (weight 0, so no severity
        // impact) — kept as an informational signal only, not a penalty.
        if ("drm" in observed && claimedRole !in setOf("Video Streaming", "Music Streaming")) {
            reasons.add("Informational: DRM capability outside streaming role " +
                "(likely content-protection SDK, not privacy-sensitive)")
            anomalyTrace += "    7f DRM: informational only (role '$claimedRole' not streaming)"
        }

        anomalyTrace.add(
            0,
            if (anomaliesFired == 0) "[7] ANOMALY     none fired (subtotal $severity)"
            else "[7] ANOMALY     $anomaliesFired rule(s) fired (subtotal $severity)"
        )

        if (severity > 100) severity = 100

        // ── STAGE 8: Final label assignment ──────────────────────────
        val label = when {
            severity == 0 -> "CONSISTENT"
            severity <= 10 -> "OVERREACH"
            severity <= 25 -> "DECEPTIVE"
            else -> "SPYWARE-LIKE"
        }

        // ── Engine debug log block (TrustLog) ─────────────────────────
        val datasetProfile = AppDataset.getProfile(packageName)
        TrustLog.app(
            buildList {
                add("┌─ $packageName ('$appName')")
                addAll(baseline.trace)
                addAll(diffTrace)
                addAll(adjustTrace)
                addAll(anomalyTrace)
                add("[8] VERDICT     $label | severity=$severity | " +
                    "conf=$confidence | source=$baselineSource")
                add("└─")
            }
        )

        return TrustVerdict(
            claimedRole = claimedRole,
            observedCapabilities = observed.map(::prettyCapability),
            signals = reasons,
            label = label,
            severity = severity,
            confidence = confidence,
            baselineSource = baselineSource,
            confidenceScore = confidenceScore,
            unexpectedCapabilities = unexpected.map(::prettyCapability),
            datasetKnown = datasetProfile != null
        )
    }

    // ════════════════════════════════════════════════════════════════
    // STAGES 1–3: baseline resolution (the fallback chain)
    // ════════════════════════════════════════════════════════════════

    private data class Baseline(
        val role: String,
        val expected: Set<String>,
        val confidenceLabel: String,
        val confidenceScore: Double,
        val source: String,   // "dataset" | "research" | "heuristic" | "ml" | "generic"
        val trace: List<String> = emptyList()   // stage-by-stage decisions (for TrustLog)
    )

    private fun resolveBaseline(
        context: Context,
        packageName: String,
        appName: String,
        observed: Set<String>
    ): Baseline {

        val trace = mutableListOf<String>()

        // ── STAGE 1: Dataset exact lookup ────────────────────────────
        val profile = AppDataset.getProfile(packageName)
        if (profile != null) {
            // The dataset role-wide baselines are a strict "minimum expected"
            // set per role. Modern Android apps commonly add: biometric
            // (fingerprint app-lock), boot (push notification service),
            // nfc/bluetooth (accessories), phone (telephony state for
            // analytics dedup). These are ubiquitous-and-benign capabilities
            // that the role-union baselines don't include — apps in the
            // dataset were being flagged just for having them. They are
            // therefore added to the expected baseline for dataset apps.
            val commonModern = setOf(
                "biometric", "boot", "nfc", "bluetooth", "phone"
            )
            // Role-specific modern extras: smart-home hubs share devices with
            // family (contacts) and show floating camera/doorbell alerts
            // (overlay).
            val roleExtras = when (profile.role) {
                "Smart Home / IoT" -> setOf("contacts", "overlay")
                else -> emptySet()
            }
            val expected = profile.expectedPermissions + commonModern + roleExtras
            trace += "[1] dataset: HIT (role=${profile.role}, " +
                "+common-modern caps: ${(commonModern + roleExtras).joinToString("/")})"
            return Baseline(
                role = profile.role,
                expected = expected,
                confidenceLabel = "HIGH",
                confidenceScore = 1.0,
                source = "dataset",
                trace = trace
            )
        }
        trace += "[1] dataset: MISS"

        // ── STAGE 2: Research Engine (Exodus → Play Store → package heuristic) ──
        // The package heuristic inside ResearchEngine is what gives sideloaded
        // APKs (absent from Exodus AND Play) a first-pass baseline.
        val nameRole = inferRoleFromAppName(appName)
        val research = runBlocking { ResearchEngine.research(packageName) }

        trace += "[2] research: ${research.source} (conf ${(research.confidence * 100).toInt()}%)"

        // Baseline obtained? (flowchart diamond — confidence clears the bar)
        if (research.confidence >= 0.5) {
            val role = nameRole ?: inferRoleFromPermissions(research.expectedPermissions)
            var expected = research.expectedPermissions
            // Refinement: a recognizable display name widens the baseline with
            // that role's typical caps — Exodus/Play data can lag the installed
            // version, and conservative defaults were flagging legit apps.
            if (nameRole != null) {
                expected = expected + fallbackPermissionsForRole(nameRole)
                trace += "    refinement: name-role widening ('$nameRole')"
            }
            // Refinement: OEM platform caps (see OEM_PLATFORM_CAPS above).
            if (isOemPackage(packageName)) {
                expected = expected + OEM_PLATFORM_CAPS
                trace += "    refinement: OEM platform caps added (overlay/boot/bluetooth/install_unknown)"
            }
            val confLabel = when {
                research.confidence >= 0.8 -> "HIGH (Research)"
                else -> "MEDIUM (Research)"
            }
            Log.d("AppTrapResearch", "$packageName → source=research, " +
                    "confidence=${(research.confidence * 100).toInt()}%, role=$role")
            trace += "    → BASELINE OBTAINED: role=$role conf=${research.confidence} source=research"
            return Baseline(role, expected, confLabel, research.confidence, "research", trace)
        }
        trace += "    → NOT OBTAINED (conf < 0.5) → package-name heuristic"

        // ── PACKAGE-NAME HEURISTIC (flowchart fallback box) ──────────
        // Offline display-name role inference — the stage that covers
        // sideloaded APKs with no Exodus/Play footprint. Sufficient when the
        // name maps to a role (conf 0.6).
        if (nameRole != null) {
            var expected = research.expectedPermissions + fallbackPermissionsForRole(nameRole)
            if (isOemPackage(packageName)) {
                expected = expected + OEM_PLATFORM_CAPS
                trace += "    refinement: OEM platform caps added (overlay/boot/bluetooth/install_unknown)"
            }
            Log.d("AppTrapResearch", "$packageName → source=heuristic (name), role=$nameRole")
            trace += "    pkg-heuristic: HIT ('$nameRole') → SUFFICIENT (conf 0.6)"
            return Baseline(nameRole, expected, "MEDIUM (Name)", 0.6, "heuristic", trace)
        }
        trace += "    pkg-heuristic: MISS"

        // ── STAGE 3: K-NN machine learning (Jaccard, K=5, majority vote) ──
        // Gate (refinement): if the PACKAGE name is recognizable, skip ML —
        // a name-inferred conservative baseline beats a low-confidence
        // neighbor vote for a package we can already classify.
        val isGenericName = !packageName.lowercase().let { name ->
            name.contains("calculator") || name.contains("calc") ||
                    name.contains("flashlight") || name.contains("torch") ||
                    name.contains("camera") || name.contains("photo") ||
                    name.contains("browser") || name.contains("web") ||
                    name.contains("messenger") || name.contains("chat") ||
                    name.contains("sms") || name.contains("mail") ||
                    name.contains("email") || name.contains("map") ||
                    name.contains("navigat") || name.contains("bank") ||
                    name.contains("finance") || name.contains("pay") ||
                    name.contains("game") || name.contains("play") ||
                    name.contains("music") || name.contains("spotify") ||
                    name.contains("stream") || name.contains("social") ||
                    name.contains("facebook") || name.contains("insta") ||
                    name.contains("tiktok") || name.contains("keyboard") ||
                    name.contains("launcher") || name.contains("home") ||
                    name.contains("vpn") || name.contains("proxy") ||
                    name.contains("threads") || name.contains("share") ||
                    name.contains("midrop") || name.contains("barcelona")
        }

        if (isGenericName) {
            trace += "[3] knn: package not recognizable → predicting"
            val dataset = AppDataset.getAllProfiles()
            val prediction = SimpleML.predictRole(observed, dataset, k = 5)

            trace += "    vote result: role=${prediction.role} " +
                "conf=${(prediction.confidence * 100).toInt()}%"

            Log.d("AppTrapML", "Fell back to ML for $appName → " +
                    "predicted=${prediction.role}, confidence=${(prediction.confidence * 100).toInt()}%")

            // ML confidence Sufficient? (flowchart diamond)
            if (prediction.confidence >= 0.4) {
                val confLabel = when {
                    prediction.confidence >= 0.7 -> "MEDIUM-HIGH (ML)"
                    else -> "MEDIUM (ML)"
                }
                trace += "    → SUFFICIENT"
                return Baseline(
                    prediction.role, prediction.expectedPermissions,
                    confLabel, prediction.confidence, "ml", trace
                )
            }
            trace += "    → NOT SUFFICIENT"
        } else {
            trace += "[3] knn: package recognizable → ML skipped"
        }

        // ── GENERIC FALLBACK ("General" role) ────────────────────────
        // Last resort: a permissive baseline. Per flowchart, "General" role
        // skips per-capability differential penalties and relies on anomaly rules.
        trace += "    generic fallback → role=General (anomaly rules only)"
        return Baseline(
            role = "General",
            expected = setOf("network", "storage", "notifications"),
            confidenceLabel = "LOW",
            confidenceScore = 0.2,
            source = "generic",
            trace = trace
        )
    }

    // ════════════════════════════════════════════════════════════════
    // STAGE 4: observed-side helpers
    // ════════════════════════════════════════════════════════════════

    /**
     * Typical permission baseline for a role inferred from the app name.
     * Used to widen the (ultra-conservative) package-name heuristic baseline
     * so legitimate apps stop tripping the differential penalty.
     */
    private fun fallbackPermissionsForRole(role: String): Set<String> {
        return when (role) {
            "Family Safety / Location" -> setOf(
                "network", "location", "contacts", "phone", "storage", "notifications", "boot", "biometric"
            )
            "Finance / Banking" -> setOf(
                "network", "storage", "location", "camera", "contacts", "biometric", "notifications", "nfc", "mic"
            )
            "Voice Recorder / Transcription" -> setOf(
                "network", "storage", "mic", "notifications", "boot", "overlay"
            )
            "Web Browser" -> setOf(
                "network", "storage", "camera", "mic", "location", "contacts", "notifications", "biometric"
            )
            "VPN / Proxy" -> setOf(
                "network", "storage", "overlay", "boot", "notifications", "biometric"
            )
            "Social Media" -> setOf(
                "network", "storage", "contacts", "camera", "mic", "location",
                "notifications", "boot", "overlay",
                // Fingerprint app-lock is standard in modern social apps
                // (WhatsApp, Telegram, Instagram, Threads).
                "biometric"
            )
            "File Sharing" -> setOf(
                "network", "storage", "location", "bluetooth", "notifications",
                "install_unknown", "camera",
                // Vivo EasyShare / SHAREit-era apps also use: SMS to send
                // download links, contacts for "send to contact", boot for the
                // persistent transfer service, and overlay for the quick-share
                // bubble. These are legacy-but-legitimate file-sharing features.
                "sms", "contacts", "boot", "overlay"
            )
            "Games" -> setOf(
                "network", "storage", "mic", "camera", "location", "notifications", "bluetooth", "nfc"
            )
            "Simple Utility" -> setOf(
                "network", "notifications"
            )
            "Productivity" -> setOf(
                "network", "storage", "camera", "mic", "contacts", "location", "notifications"
            )
            "AI Assistant" -> setOf(
                "network", "storage", "mic", "camera", "location", "notifications", "biometric"
            )
            else -> setOf("network", "storage", "notifications")
        }
    }

    /**
     * Role inference from the human-readable app NAME (what the user sees),
     * tried BEFORE package-name heuristics. Package names are often opaque
     * ("com.life360.android.safetymapd", "ai.meeting.app") while the display
     * name is descriptive ("Life360", "Meeting.ai"), so this catches the
     * cases the package heuristic misses. Returns null when nothing matches.
     */
    private fun inferRoleFromAppName(appName: String): String? {
        val name = appName.lowercase()
        return when {
            // Family / location safety trackers (Life360, Find Hub, etc.)
            name.contains("life360") || name.contains("family") ||
                name.contains("find hub") || name.contains("find my") ||
                name.contains("locator") || name.contains("safety") ->
                "Family Safety / Location"

            // E-wallets / payments
            name.contains("pay") || name.contains("wallet") ||
                name.contains("ewallet") || name.contains("e-wallet") ->
                "Finance / Banking"

            // Voice / meeting recorders & transcribers
            name.contains("recorder") || name.contains("transkrib") ||
                name.contains("transkript") || name.contains("transcript") ||
                name.contains("meeting") ->
                "Voice Recorder / Transcription"

            // Browsers (incl. adblock / privacy browsers)
            name.contains("browser") || name.contains("adblock") ||
                name.contains("firefox") || name.contains("brave") ||
                name.contains("opera") || name.contains("duckduckgo") ->
                "Web Browser"

            // VPN / proxy
            name.contains("vpn") || name.contains("proxy") ->
                "VPN / Proxy"

            // Social media — "Threads" (com.instagram.barcelona) has an opaque
            // package name, so catch it by display name along with the other
            // major social platforms.
            name.contains("threads") || name.contains("instagram") ||
                name.contains("tiktok") || name.contains("snapchat") ||
                name.contains("twitter") || name.contains("facebook") ||
                name.contains("mastodon") || name.contains("bluesky") ||
                name.contains("reddit") || name.contains("weibo") ||
                name.contains("social") ->
                "Social Media"

            // File-sharing / transfer apps (ShareMe, EasyShare, SHAREit,
            // Xender, Zapya...). Wi-Fi Direct transfer legitimately needs
            // location (scanning nearby devices), bluetooth and the ability
            // to install received APKs.
            name.contains("share") || name.contains("xender") ||
                name.contains("zapya") || name.contains("midrop") ||
                name.contains("send anywhere") || name.contains("file transfer") ||
                name.contains("quick share") ->
                "File Sharing"

            // Games — catch common game words so they don't fall to "General"
            name.contains("pokémon") || name.contains("pokemon") ||
                name.contains("game") || name.contains("clash") ||
                name.contains("racing") || name.contains("among us") ||
                name.contains("roblox") || name.contains("fight") ||
                name.contains("critters") || name.contains("bb racing") ->
                "Games"

            // Calculators / flashlights — the strict "Simple Utility" bucket.
            // NOTE: calendar & compass deliberately NOT here (see below).
            name.contains("calculator") || name.contains("flashlight") ||
                name.contains("torch") ->
                "Simple Utility"

            // Calendar needs contacts (attendees) + storage (attachments) and
            // compass needs location (magnetic declination) + camera (AR mode)
            // — these are expected behaviors, so they get the Productivity
            // baseline instead of the ultra-strict calculator bucket.
            name.contains("calendar") || name.contains("compass") ||
                name.contains("clock") ->
                "Productivity"

            // Notes / to-do / scanner
            name.contains("note") || name.contains("to do") ||
                name.contains("todo") || name.contains("scanner") ||
                name.contains("scan") ->
                "Productivity"

            // AI assistants
            name.contains("chatgpt") || name.contains("gemini") ||
                name.contains("claude") || name.contains("copilot") ||
                name.contains("bard") ->
                "AI Assistant"

            // Banking (display name often has the bank brand)
            name.contains("bank") || name.contains("ryt") ->
                "Finance / Banking"

            else -> null
        }
    }

    private fun inferRoleFromPermissions(perms: Set<String>): String {
        return when {
            "sms" in perms && "contacts" in perms && "mic" in perms ->
                "Communications / Messaging"
            "network" in perms && "storage" in perms && "camera" in perms &&
                "contacts" !in perms && "sms" !in perms ->
                "Media / Camera"
            "location" in perms && "network" in perms && "camera" !in perms ->
                "Maps / Navigation"
            "network" in perms && "storage" in perms && perms.size <= 3 ->
                "Utility / Tool"
            "biometric" in perms || "nfc" in perms ->
                "Finance / Banking"
            "overlay" in perms && "boot" in perms ->
                "System Tool / Launcher"
            "network" in perms && "storage" in perms && "contacts" in perms &&
                "camera" in perms && "mic" in perms && "location" in perms ->
                "Social Media"
            "network" in perms && "storage" in perms && "mic" in perms &&
                "camera" !in perms ->
                "Music Streaming"
            else -> "General"
        }
    }

    private fun extractObservedCapabilities(perms: List<String>): Set<String> {
        val caps = mutableSetOf<String>()
        perms.forEach { perm ->
            when (perm) {
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE -> caps.add("network")

                Manifest.permission.SYSTEM_ALERT_WINDOW -> caps.add("overlay")
                Manifest.permission.RECEIVE_BOOT_COMPLETED -> caps.add("boot")

                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.GET_ACCOUNTS -> caps.add("contacts")

                Manifest.permission.RECORD_AUDIO -> caps.add("mic")
                Manifest.permission.CAMERA -> caps.add("camera")

                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION -> caps.add("location")

                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.BROADCAST_SMS,
                "android.permission.WRITE_SMS" -> caps.add("sms")

                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                "android.permission.PROCESS_OUTGOING_CALLS" -> caps.add("call_log")

                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO",
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> caps.add("storage")

                Manifest.permission.POST_NOTIFICATIONS -> caps.add("notifications")

                Manifest.permission.USE_BIOMETRIC,
                "android.permission.USE_FINGERPRINT" -> caps.add("biometric")

                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH_SCAN",
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN -> caps.add("bluetooth")

                Manifest.permission.NFC -> caps.add("nfc")
                Manifest.permission.REQUEST_INSTALL_PACKAGES -> caps.add("install_unknown")

                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE -> caps.add("phone")

                // ── New capabilities from dataset_final.csv ──
                // Accessibility service: can read screen content and inject
                // input events — stalkerware-grade power when misused.
                "android.permission.BIND_ACCESSIBILITY_SERVICE" -> caps.add("accessibility")

                // Usage stats: can observe which apps are used and for how long.
                "android.permission.PACKAGE_USAGE_STATS" -> caps.add("usage_stats")

                // DRM (widevine etc.): content protection for streaming apps.
                "android.permission.BIND_DRM_SERVICE",
                "com.android.vending.DRM",
                "com.android.vending.LICENSING",
                "android.permission.READ_MEDIA_DRM" -> caps.add("drm")
            }
        }
        return caps
    }

    // ════════════════════════════════════════════════════════════════
    // STAGE 5/7 helpers
    // ════════════════════════════════════════════════════════════════

    private fun capabilityWeight(capability: String): Int {
        return when (capability) {
            "sms"             -> 8
            "call_log"        -> 6
            // Accessibility is stalkerware-grade power when requested outside
            // antivirus/system-tool roles (reads screen + injects touches).
            "accessibility"   -> 7
            // Usage stats reveals the user's entire app-usage behavior.
            "usage_stats"     -> 5
            "overlay"         -> 5
            "contacts"        -> 5
            "location"        -> 5
            "mic"             -> 4
            "camera"          -> 4
            "install_unknown" -> 5
            "boot"            -> 2
            "storage"         -> 2
            "notifications"   -> 1
            "biometric"       -> 3
            // DRM is content protection for streaming — no privacy weight.
            "drm"             -> 0
            "bluetooth"       -> 0
            "nfc"             -> 0
            "phone"           -> 0
            "network"         -> 0
            else              -> 3
        }
    }

    private fun prettyCapability(capability: String): String {
        return when (capability) {
            "network"         -> "Network access"
            "overlay"         -> "Overlay permission"
            "boot"            -> "Runs after restart"
            "contacts"        -> "Contacts access"
            "mic"             -> "Microphone access"
            "camera"          -> "Camera access"
            "location"        -> "Location access"
            "sms"             -> "SMS access"
            "call_log"        -> "Call log access"
            "storage"         -> "Storage access"
            "notifications"   -> "Notification access"
            "biometric"       -> "Biometric access"
            "bluetooth"       -> "Bluetooth access"
            "nfc"             -> "NFC access"
            "install_unknown" -> "Install unknown apps"
            "phone"           -> "Phone state access"
            "accessibility"   -> "Accessibility service"
            "usage_stats"     -> "Usage data access"
            "drm"             -> "DRM access"
            else              -> capability
        }
    }
}
