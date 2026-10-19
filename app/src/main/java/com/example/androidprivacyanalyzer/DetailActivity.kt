package com.example.androidprivacyanalyzer

import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.androidprivacyanalyzer.ui.DeleteGuideDialog
import com.example.androidprivacyanalyzer.ui.PermissionPlainLanguage
import com.example.androidprivacyanalyzer.ui.RiskLevel
import com.example.androidprivacyanalyzer.ui.SignalViews
import com.example.androidprivacyanalyzer.ui.UiAnimations
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator

class DetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        val app: AppRisk? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getSerializableExtra("app", AppRisk::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("app") as? AppRisk
        }

        if (app == null) {
            Toast.makeText(this, getString(R.string.no_app_data), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val v = app.verdict
        val level = RiskLevel.ofLabel(v.label)

        // ===== Header =====
        val imgAppIcon = findViewById<ImageView>(R.id.appIcon)
        try {
            val icon: Drawable = packageManager.getApplicationIcon(app.packageName)
            imgAppIcon.setImageDrawable(icon)
        } catch (_: PackageManager.NameNotFoundException) {
            imgAppIcon.setImageResource(R.drawable.ic_security)
        }

        findViewById<TextView>(R.id.appName).text = app.appName
        findViewById<TextView>(R.id.appRole).text =
            getString(R.string.detail_role, v.claimedRole.lowercase())
        findViewById<TextView>(R.id.packageName).text = app.packageName

        // ===== Verdict card: score ring + friendly badge =====
        val ring = findViewById<CircularProgressIndicator>(R.id.scoreRing)
        ring.max = 100
        ring.setIndicatorColor(level.color(this))
        ring.setProgressCompat(v.severity, true)

        // Score number stays white like the prototype; the ring + badge carry
        // the risk colour.
        val scoreValue = findViewById<TextView>(R.id.scoreValue)
        UiAnimations.countUp(scoreValue, v.severity)

        val riskBadge = findViewById<TextView>(R.id.riskBadge)
        riskBadge.text = level.title(this)
        riskBadge.setTextColor(level.onContainer(this))
        riskBadge.background = level.badgeDrawable(this)

        findViewById<TextView>(R.id.riskExplainer).text = level.body(this)
        findViewById<TextView>(R.id.confidenceLabel).setText(confidenceLabelRes(v.confidence))

        // ===== What made us pause =====
        val worryHeader = findViewById<TextView>(R.id.worryHeader)
        val suspiciousContainer = findViewById<ViewGroup>(R.id.suspiciousContainer)
        // Show only signals a normal user can act on: unexpected capabilities
        // and behavioral warnings. Engine bookkeeping (dataset-miss notes,
        // severity caps) stays in the CSV + TrustLog evidence.
        val worrying = if (level == RiskLevel.CONSISTENT) emptyList() else userFacingSignals(v.signals)
        worryHeader.isVisible = worrying.isNotEmpty()
        SignalViews.bind(suspiciousContainer, worrying, suspicious = true)

        // ===== Everything else looks normal (collapsed) =====
        val normalContainer = findViewById<ViewGroup>(R.id.normalContainer)
        val normalChevron = findViewById<ImageView>(R.id.normalChevron)
        val normalCard = findViewById<ViewGroup>(R.id.normalCard)
        // Capabilities already listed in "What made us pause" must NOT appear
        // again here — flagging a permission and then calling it normal is
        // redundant and misleading.
        val unexpectedPretty = v.unexpectedCapabilities.toSet()
        val normalLines = v.observedCapabilities
            .filter { it !in unexpectedPretty }
            .map { PermissionPlainLanguage.plainSignal(this, it) }

        findViewById<TextView>(R.id.normalCount).text =
            getString(R.string.detail_normal_count, normalLines.size)
        SignalViews.bind(normalContainer, normalLines, suspicious = false)
        normalCard.isVisible = normalLines.isNotEmpty()

        findViewById<View>(R.id.normalHeader).setOnClickListener {
            UiAnimations.toggleSection(normalCard, normalContainer, normalChevron)
        }

        // ===== Decision bar =====
        findViewById<MaterialButton>(R.id.btnDelete).setOnClickListener {
            DeleteGuideDialog.show(this, app.appName, app.packageName)
        }
        findViewById<MaterialButton>(R.id.btnReviewPermissions).setOnClickListener {
            openAppPermissionSettings(app.packageName)
        }
        findViewById<MaterialButton>(R.id.btnKeep).setOnClickListener { finish() }
    }

    private fun confidenceLabelRes(confidence: String): Int = when {
        confidence.startsWith("HIGH") -> R.string.detail_confidence_high
        confidence.startsWith("MEDIUM") -> R.string.detail_confidence_medium
        else -> R.string.detail_confidence_low
    }

    private fun openAppPermissionSettings(targetPackage: String) {
        val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", targetPackage, null)
        }
        try {
            startActivity(settingsIntent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.detail_permissions_settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        // Engine-internal signal prefixes — kept in the verdict/CSV/TrustLog for
        // evidence, but never shown to users (misleading for non-IT audiences).
        private val ENGINE_ONLY_PREFIXES = listOf(
            "App not in dataset",
            "Severity capped",
            "Informational:",
            "Requested permissions match",
            "Role not recognized",
        )

        fun userFacingSignals(signals: List<String>): List<String> =
            signals.filter { s -> ENGINE_ONLY_PREFIXES.none { s.startsWith(it) } }
    }
}
