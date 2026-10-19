package com.example.androidprivacyanalyzer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.Locale
import kotlin.math.min

class FeedbackActivity : AppCompatActivity() {

    private val FEEDBACK_EMAIL = "adamsyfiq424@gmail.com"
    private var lastScanResults: ArrayList<AppRisk>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback_simple)
        constrainFeedbackContentWidth()

        lastScanResults = getScanResultsFromIntent()
        val includeResults = findViewById<MaterialCheckBox>(R.id.chkIncludeResults)
        val resultsAvailable = !lastScanResults.isNullOrEmpty()
        includeResults.isEnabled = resultsAvailable
        if (!resultsAvailable) {
            findViewById<android.widget.TextView>(R.id.txtIncludeResultsHint)
                .setText(R.string.fb_no_results_hint)
        }

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        // Default to "Yes" so Send works even if the user only answers Q2.
        findViewById<MaterialButtonToggleGroup>(R.id.toggleClear).check(R.id.btnClearYes)

        findViewById<MaterialButton>(R.id.btnSubmit).setOnClickListener { showEmailGuide() }
    }

    private fun constrainFeedbackContentWidth() {
        val scroll = findViewById<ScrollView>(R.id.feedbackScroll)
        val content = findViewById<View>(R.id.feedbackContent)
        val maxWidth = (600 * resources.displayMetrics.density).toInt()
        content.post {
            val params = content.layoutParams
            params.width = min(scroll.width, maxWidth)
            content.layoutParams = params
        }
    }

    private fun showEmailGuide() {
        val guide = layoutInflater.inflate(R.layout.dialog_feedback_guide, null, false)
        val includeResults = findViewById<MaterialCheckBox>(R.id.chkIncludeResults).isChecked
        guide.findViewById<android.widget.TextView>(R.id.fbGuidePrivacyText).setText(
            if (includeResults) R.string.fb_guide_privacy_with_results
            else R.string.fb_guide_privacy_without_results
        )
        val dialog = MaterialAlertDialogBuilder(this, R.style.Dialog_Apptrap)
            .setView(guide)
            .setNegativeButton(R.string.fb_guide_cancel, null)
            .setPositiveButton(R.string.fb_guide_continue) { _, _ -> sendFeedback() }
            .create()
        dialog.show()

        // Use a comfortable, tablet-aware width rather than the platform's
        // narrow default. The guide itself scrolls if vertical space is short.
        val metrics = resources.displayMetrics
        val horizontalMargin = (24 * metrics.density).toInt()
        val maxWidth = (640 * metrics.density).toInt()
        val width = min(metrics.widthPixels - horizontalMargin * 2, maxWidth)
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
        val includeResults = findViewById<MaterialCheckBox>(R.id.chkIncludeResults).isChecked

        val versionName = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

        val deviceInfo = "Android ${Build.VERSION.RELEASE} | ${Build.MANUFACTURER} ${Build.MODEL}"

        val resultsFile = if (includeResults && !lastScanResults.isNullOrEmpty()) {
            try {
                createResultsCsv(lastScanResults!!)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.fb_export_failed), Toast.LENGTH_LONG).show()
                return
            }
        } else null

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
            if (resultsFile != null) {
                val results = lastScanResults!!
                appendLine()
                appendLine("Scanned results: ${results.size} apps (attached as ${resultsFile.name}).")
            }
            appendLine("=== End ===")
        }

        val attachmentUri = resultsFile?.let {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", it)
        }
        sendEmail(versionName, body, resultsFile, attachmentUri)
    }

    private fun createResultsCsv(results: List<AppRisk>): File {
        val directory = File(cacheDir, "feedback_exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(directory, "AppTrap_scan_results_$stamp.csv")
        OutputStreamWriter(file.outputStream(), StandardCharsets.UTF_8).use { writer ->
            writer.write("App name,Package name,Assessment,Risk score,Requested permissions\r\n")
            results.sortedByDescending { it.verdict.severity }.forEach { app ->
                val fields = listOf(
                    app.appName,
                    app.packageName,
                    app.verdict.label,
                    "${app.verdict.severity}/100",
                    app.allPerms.distinct().joinToString("; ")
                )
                writer.write(fields.joinToString(",") { csvEscape(it) })
                writer.write("\r\n")
            }
        }
        return file
    }

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun sendEmail(version: String, body: String, attachment: File?, attachmentUri: android.net.Uri?) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "AppTrap v$version - Feedback")
            putExtra(Intent.EXTRA_TEXT, body)
            if (attachmentUri != null) {
                putExtra(Intent.EXTRA_STREAM, attachmentUri)
                clipData = ClipData.newUri(contentResolver, attachment?.name ?: "AppTrap_scan_results.csv", attachmentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.fb_submit)))
            Toast.makeText(this, getString(R.string.fb_thanks), Toast.LENGTH_SHORT).show()
            finish()
        } catch (_: Exception) {
            if (attachment != null) {
                Toast.makeText(this, getString(R.string.no_email_app), Toast.LENGTH_LONG).show()
                return
            }
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("AppTrap Feedback", body))
            Toast.makeText(this, getString(R.string.no_email_app), Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
