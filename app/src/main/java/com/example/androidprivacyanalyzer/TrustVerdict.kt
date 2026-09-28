package com.example.androidprivacyanalyzer

import java.io.Serializable

data class TrustVerdict(
    val claimedRole: String,
    val observedCapabilities: List<String>,
    val signals: List<String>,
    val label: String,
    val severity: Int,
    val confidence: String = "HIGH",
    val baselineSource: String = "dataset",      // provenance of expected baseline
    val confidenceScore: Double = 1.0,           // numeric confidence 0.0–1.0
    val unexpectedCapabilities: List<String> = emptyList(),  // human-readable unexpected caps
    val datasetKnown: Boolean = false            // app present in dataset_final.csv
) : Serializable
