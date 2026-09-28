package com.example.androidprivacyanalyzer

import java.io.Serializable

data class AppRisk(
    val packageName: String,
    val appName: String,
    val dangerousPerms: Int,
    val score: Int,
    val level: String,
    val verdict: TrustVerdict,
    val allPerms: List<String> = emptyList()
) : Serializable
