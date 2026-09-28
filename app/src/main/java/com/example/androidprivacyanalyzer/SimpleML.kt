package com.example.androidprivacyanalyzer

object SimpleML {

    data class Neighbor(
        val role: String,
        val permissions: Set<String>,
        val similarity: Double
    )

    data class MLPrediction(
        val role: String,
        val expectedPermissions: Set<String>,
        val confidence: Double
    )

    fun predictRole(
        observedCaps: Set<String>,
        dataset: Map<String, AppProfile>,
        k: Int = 5
    ): MLPrediction {

        if (dataset.isEmpty()) {
            return MLPrediction("Unknown", fallbackPermissions("Unknown"), 0.0)
        }

        val similarities = dataset.values.map { profile ->
            val expectedCaps = profile.expectedPermissions
            val intersection = observedCaps.intersect(expectedCaps).size.toDouble()
            val union = observedCaps.union(expectedCaps).size.toDouble()
            val jaccard = if (union > 0) intersection / union else 0.0

            Neighbor(profile.role, profile.expectedPermissions, jaccard)
        }

        val topK = similarities.sortedByDescending { it.similarity }.take(k)
        val roleVotes = topK.groupingBy { it.role }.eachCount()
        val predictedRole = roleVotes.maxByOrNull { it.value }?.key ?: "Unknown"

        val top3OfRole = topK.filter { it.role == predictedRole }.take(3)
        val expectedPermissions = top3OfRole.flatMap { it.permissions }.toSet()
        val confidence = topK.map { it.similarity }.average()

        return MLPrediction(predictedRole, expectedPermissions, confidence)
    }

    /**
     * Realistic fallback permissions per category (role names follow the
     * Google Play Store categories used in dataset_final.csv).
     * These are BASELINE expectations — apps that exceed these get flagged.
     */
    private fun fallbackPermissions(role: String): Set<String> {
        return when (role) {
            "Communications / Messaging" -> setOf(
                "network", "contacts", "mic", "camera", "storage", "location", "sms", "call_log", "notifications"
            )
            "Web Browser" -> setOf(
                "network", "storage", "camera", "mic", "location", "contacts", "notifications"
            )
            "Social Media" -> setOf(
                "network", "storage", "contacts", "camera", "mic", "location", "notifications"
            )
            "Email Client" -> setOf(
                "network", "storage", "contacts", "camera", "notifications"
            )
            "Cloud Storage" -> setOf(
                "network", "storage", "camera", "notifications"
            )
            "Maps / Navigation" -> setOf(
                "network", "location", "storage", "mic", "camera", "contacts", "notifications"
            )
            "Video Streaming" -> setOf(
                "network", "storage", "mic", "camera", "location", "notifications"
            )
            "Music Streaming" -> setOf(
                "network", "storage", "mic", "location", "notifications"
            )
            "Productivity" -> setOf(
                "network", "storage", "camera", "mic", "contacts", "location", "notifications"
            )
            "Security / Antivirus" -> setOf(
                "network", "storage", "camera", "location", "contacts", "call_log", "sms",
                "overlay", "boot", "notifications", "accessibility", "usage_stats", "biometric"
            )
            "Finance / Banking" -> setOf(
                "network", "storage", "location", "camera", "contacts", "biometric", "notifications"
            )
            "Media / Photo Editing" -> setOf(
                "network", "storage", "camera", "location", "mic", "contacts", "notifications"
            )
            "Games" -> setOf(
                "network", "storage", "mic", "camera", "location", "notifications"
            )
            "System Tools" -> setOf(
                "network", "storage", "overlay", "boot", "camera", "location", "notifications"
            )
            "Smart Home / IoT" -> setOf(
                "network", "storage", "location", "camera", "mic", "notifications", "bluetooth"
            )
            "News / RSS" -> setOf(
                "network", "storage", "location", "notifications"
            )
            else -> setOf("network", "storage", "notifications")
        }
    }
}