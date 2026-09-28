package com.example.androidprivacyanalyzer

import android.content.Context

object AppDataset {

    private const val DATASET_FILE = "dataset_final.csv"

    private val profilesByPackage = mutableMapOf<String, AppProfile>()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return

        context.assets.open(DATASET_FILE).bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                val row = parseCsvLine(line)
                if (row.size < 4) return@forEach

                val packageName = row[0].trim()
                val appName = row[1].trim()
                val role = row[2].trim()
                val expected = row[3]
                    .split("|")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .toSet()

                // Column 5: source ("playstore" | "own"). Columns 6-7 carry
                // Exodus tracker metadata that is not used by the analyzer.
                val source = row.getOrNull(4)?.trim() ?: ""

                if (packageName.isNotBlank()) {
                    profilesByPackage[packageName] = AppProfile(
                        packageName = packageName,
                        appName = appName,
                        role = role,
                        expectedPermissions = expected,
                        source = source
                    )
                }
            }
        }

        loaded = true
    }

    fun getProfile(packageName: String): AppProfile? {
        return profilesByPackage[packageName]
    }

    fun isKnown(packageName: String): Boolean {
        return profilesByPackage.containsKey(packageName)
    }

    fun size(): Int = profilesByPackage.size

    /** Name of the active dataset file (for debug logs / report provenance). */
    fun datasetName(): String = DATASET_FILE

    fun getAllProfiles(): Map<String, AppProfile> {
        return profilesByPackage.toMap()
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }

        result.add(current.toString())
        return result
    }
}
