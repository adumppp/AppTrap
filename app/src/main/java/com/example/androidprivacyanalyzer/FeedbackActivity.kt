package com.example.androidprivacyanalyzer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FeedbackActivity : AppCompatActivity() {

    private val FEEDBACK_EMAIL = "adamsyfiq424@gmail.com"
    private var lastScanResults: ArrayList<AppRisk>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback_simple)

        lastScanResults = getScanResultsFromIntent()

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        // Default to "Yes" so Send works even if the user only answers Q2.
        findViewById<MaterialButtonToggleGroup>(R.id.toggleClear).check(R.id.btnClearYes)

        findViewById<MaterialButton>(R.id.btnSubmit).setOnClickListener { sendFeedback() }
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun getScanResultsFromIntent(): ArrayList<AppRisk>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("scan_results", ArrayList::class.java) as? ArrayList<AppRisk>
        } else {
            intent.getSerializableExtra("scan_results") as? ArrayList<AppRisk>
        }
    }

    private fun sendFeedback() {
        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.toggleClear)
        val wasClear = toggle.checkedButtonId == R.id.btnClearYes
        val message = findViewById<TextInputEditText>(R.id.inputOpen).text?.toString()?.trim().orEmpty()

        val versionName = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

        val deviceInfo = "Android ${Build.VERSION.RELEASE} | ${Build.MANUFACTURER} ${Build.MODEL}"

        val body = buildString {
            appendLine("=== AppTrap Feedback ===")
            appendLine("Submitted: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine()
            appendLine("--- Quick answer ---")
            appendLine("Result easy to understand: ${if (wasClear) "Yes" else "No"}")
            appendLine()
            appendLine("--- User feedback ---")
            appendLine(message.ifEmpty { "(no written feedback)" })
            appendLine()
            appendLine("--- System info ---")
            appendLine("App version: $versionName")
            appendLine("Device: $deviceInfo")
            appendLine("Dataset size: ${AppDataset.size()} apps")

            if (!lastScanResults.isNullOrEmpty()) {
                val results = lastScanResults!!
                appendLine()
                appendLine("--- Scan summary ---")
                appendLine(
                    "Total: ${results.size} | Risky: ${results.count { it.verdict.severity > 0 }} | " +
                        "Safe: ${results.count { it.verdict.severity == 0 }}"
                )
                appendLine()
                appendLine("Top 3 riskiest apps:")
                results.sortedByDescending { it.verdict.severity }.take(3)
                    .forEachIndexed { i, app ->
                        appendLine("${i + 1}. ${app.appName}: ${app.verdict.label} (${app.verdict.severity})")
                    }
            }
            appendLine("=== End ===")
        }

        sendEmail(versionName, body)
    }

    private fun sendEmail(version: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "AppTrap v$version - Feedback")
            putExtra(Intent.EXTRA_TEXT, body)
        }

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.fb_submit)))
            Toast.makeText(this, getString(R.string.fb_thanks), Toast.LENGTH_SHORT).show()
            finish()
        } catch (_: Exception) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("AppTrap Feedback", body))
            Toast.makeText(this, getString(R.string.no_email_app), Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
