package com.example.androidprivacyanalyzer

data class AppProfile(
    val packageName: String,
    val appName: String,
    val role: String,
    val expectedPermissions: Set<String>,
    // Dataset provenance ("playstore" = fetched via Play API, "own" = curated)
    val source: String = ""
)
