package com.example.androidprivacyanalyzer

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.androidprivacyanalyzer.ui.LanguageToggle
import com.example.androidprivacyanalyzer.ui.RiskAdapter
import com.example.androidprivacyanalyzer.ui.ScanDialog
import com.example.androidprivacyanalyzer.ui.UiAnimations
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: RiskAdapter
    private lateinit var root: ViewGroup
    private lateinit var heroIdle: View
    private lateinit var heroSummary: View
    private lateinit var resultsGroup: View
    private lateinit var emptyState: View
    private lateinit var recyclerRisk: RecyclerView
    private lateinit var txtSummaryTitle: TextView
    private lateinit var txtSummaryBody: TextView
    private lateinit var txtAttentionCount: TextView
    private lateinit var btnScan: MaterialButton
    private lateinit var btnSafeApps: MaterialButton

    private var lastScanResults: List<AppRisk> = emptyList()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isScanning = AtomicBoolean(false)

    private var scanDialog: ScanDialog? = null
    private var scanStartTime: Long = 0
    private var targetDurationMs: Long = 15000 // Default 15 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // First run: explain in plain words what the check does, once.
        if (!getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("onboarding_done", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        setContentView(R.layout.activity_main)

        AppDataset.load(this)

        root = findViewById(R.id.root)
        heroIdle = findViewById(R.id.heroIdle)
        heroSummary = findViewById(R.id.heroSummary)
        resultsGroup = findViewById(R.id.resultsGroup)
        emptyState = findViewById(R.id.emptyState)
        UiAnimations.breathe(emptyState.findViewById(R.id.emptyIcon))
        txtSummaryTitle = findViewById(R.id.txtSummaryTitle)
        txtSummaryBody = findViewById(R.id.txtSummaryBody)
        btnScan = findViewById(R.id.btnScan)
        btnSafeApps = findViewById(R.id.btnSafeApps)
        txtAttentionCount = findViewById(R.id.txtAttentionCount)

        recyclerRisk = findViewById(R.id.recyclerRisk)
        recyclerRisk.layoutManager = LinearLayoutManager(this)
        recyclerRisk.layoutAnimation =
            AnimationUtils.loadLayoutAnimation(this, R.anim.layout_anim_fall_down)
        adapter = RiskAdapter { appRisk -> openDetail(appRisk) }
        recyclerRisk.adapter = adapter

        // Prominent EN/BM pill in the header + breathing hero shield.
        LanguageToggle.bind(findViewById(R.id.langToggle))
        UiAnimations.breathe(findViewById(R.id.idleShield))

        btnScan.setOnClickListener { startScan() }
        btnSafeApps.setOnClickListener { openConsistentApps() }

        emptyState.findViewById<View>(R.id.btnEmptySeeAll).setOnClickListener { openConsistentApps() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isScanning.get()) {
                    Toast.makeText(this@MainActivity, getString(R.string.scan_in_progress), Toast.LENGTH_SHORT).show()
                    return
                }
                if (shouldShowFeedbackDialog()) {
                    showOptionalExitDialog()
                } else {
                    finish()
                }
            }
        })
    }

    // Adaptive pacing so the scan dialog tells its story instead of flashing by.
    private fun calculateTargetDuration(appCount: Int): Long {
        return when {
            appCount <= 50 -> 10000L   // 10 seconds for small devices
            appCount <= 100 -> 15000L  // 15 seconds for medium devices
            appCount <= 200 -> 25000L  // 25 seconds for large devices
            else -> 35000L             // 35 seconds for very large devices
        }
    }

    private fun startScan() {
        if (!isScanning.compareAndSet(false, true)) return
        btnScan.isEnabled = false

        val pm = packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val nonSystemApps = installedApps.filter {
            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        }
        targetDurationMs = calculateTargetDuration(nonSystemApps.size)

        val dialog = ScanDialog(this) { cancelScan() }
        scanDialog = dialog
        dialog.show(nonSystemApps.size)

        TrustLog.beginScan(this, nonSystemApps.size)

        executor.execute {
            try {
                scanStartTime = System.currentTimeMillis()

                var attention = 0
                var fine = 0
                val results = performScan(nonSystemApps) { current, appName, verdict ->
                    if (verdict != null) {
                        if (verdict.severity > 0) attention++ else fine++
                    }
                    mainHandler.post {
                        scanDialog?.update(current, appName, attention, fine)
                    }
                }

                if (!isScanning.get()) return@execute // cancelled — stay quiet

                // Keep the dialog up for the paced duration so progress feels real.
                val elapsed = System.currentTimeMillis() - scanStartTime
                val deadline = System.currentTimeMillis() + (targetDurationMs - elapsed)
                while (isScanning.get() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(100)
                }

                if (!isScanning.get()) return@execute // cancelled during pacing

                mainHandler.post {
                    scanDialog?.dismiss()
                    scanDialog = null
                    isScanning.set(false)
                    btnScan.isEnabled = true
                    onScanComplete(results)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    scanDialog?.dismiss()
                    scanDialog = null
                    isScanning.set(false)
                    btnScan.isEnabled = true
                    Toast.makeText(this, getString(R.string.scan_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun cancelScan() {
        isScanning.set(false)
        TrustLog.line("!! SCAN CANCELLED BY USER")
        scanDialog?.dismiss()
        scanDialog = null
        btnScan.isEnabled = true
        Toast.makeText(this, getString(R.string.scan_cancelled), Toast.LENGTH_SHORT).show()
    }

    private fun performScan(
        nonSystemApps: List<ApplicationInfo>,
        onProgress: (current: Int, appName: String, verdict: TrustVerdict?) -> Unit
    ): List<AppRisk> {
        val pm = packageManager
        val results = ArrayList<AppRisk>()
        val total = nonSystemApps.size

        // Spread the work across the paced duration.
        val delayPerApp = if (total > 0) targetDurationMs / total else 0

        nonSystemApps.forEachIndexed { index, app ->
            if (!isScanning.get()) return@forEachIndexed

            val pkg = app.packageName
            val name = pm.getApplicationLabel(app).toString()

            val pkgInfo = try {
                if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(
                        pkg,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                }
            } catch (_: Exception) {
                TrustLog.line("!! SKIPPED $packageName — PackageManager lookup failed")
                onProgress(index + 1, name, null)
                return@forEachIndexed
            }

            val perms = pkgInfo.requestedPermissions?.toList() ?: emptyList()
            val dangerousCount = perms.count {
                it in setOf(
                    "android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.SEND_SMS",
                    "android.permission.READ_CONTACTS", "android.permission.READ_CALL_LOG",
                    "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.RECORD_AUDIO", "android.permission.CAMERA",
                    "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.RECEIVE_BOOT_COMPLETED",
                    "android.permission.REQUEST_INSTALL_PACKAGES",
                    "android.permission.USE_BIOMETRIC",
                    "android.permission.BLUETOOTH_CONNECT",
                    "android.permission.NFC"
                )
            }

            val verdict = TrustEngine.analyze(this, packageName = pkg, appName = name, perms = perms)
            results.add(
                AppRisk(
                    packageName = pkg,
                    appName = name,
                    dangerousPerms = dangerousCount,
                    score = verdict.severity,
                    level = verdict.label,
                    verdict = verdict,
                    allPerms = perms
                )
            )

            onProgress(index + 1, name, verdict)

            if (delayPerApp > 0 && index < total - 1) {
                Thread.sleep(delayPerApp)
            }
        }
        return results
    }

    private fun onScanComplete(results: List<AppRisk>) {
        lastScanResults = results

        // ── Engine debug log: session footer (label summary) ────────
        TrustLog.endScan(
            totalScanned = results.size,
            labelCounts = results.groupingBy { it.verdict.label }.eachCount()
        )

        val riskyApps = results.filter { it.verdict.severity > 0 }.sortedByDescending { it.verdict.severity }
        val top = riskyApps.take(10)
        val consistentCount = results.count { it.verdict.severity == 0 }

        // Cross-fade idle hero -> summary + results (or the celebration card).
        UiAnimations.morph(root) {
            heroIdle.visibility = View.GONE
            heroSummary.visibility = View.VISIBLE
            if (top.isEmpty()) {
                resultsGroup.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            } else {
                resultsGroup.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
            }
        }

        txtSummaryTitle.text = getString(R.string.home_summary_title, results.size)
        txtSummaryBody.text = getString(R.string.home_summary_body, riskyApps.size, consistentCount)
        txtAttentionCount.text = getString(R.string.home_section_count, riskyApps.size)

        setDistribution(results)

        adapter.submitList(top)
        recyclerRisk.scheduleLayoutAnimation()

        btnScan.text = getString(R.string.home_rescan_cta)
        btnSafeApps.visibility = View.VISIBLE
    }

    /** Distribution bar: one coloured segment per risk bucket, weighted by count. */
    private fun setDistribution(results: List<AppRisk>) {
        val segments = listOf(
            R.id.barDanger to results.count { it.verdict.label == "SPYWARE-LIKE" },
            R.id.barWarn to results.count { it.verdict.label == "DECEPTIVE" },
            R.id.barCaution to results.count { it.verdict.label == "OVERREACH" },
            R.id.barSafe to results.count { it.verdict.label == "CONSISTENT" },
        )
        var total = segments.sumOf { it.second }
        if (total == 0) total = 1
        val bars = segments.map { findViewById<View>(it.first) }
        val targets = segments.map { it.second.toFloat() / total }
        // Lively 0.9s tween instead of a snap (prototype: transition-all).
        UiAnimations.animateBarWeights(bars, targets)
    }

    private fun openDetail(appRisk: AppRisk) {
        val intent = Intent(this, DetailActivity::class.java)
        intent.putExtra("app", appRisk)
        startActivity(intent)
    }

    private fun shouldShowFeedbackDialog(): Boolean {
        return !getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("dont_ask_feedback", false)
    }

    private fun saveDontAskAgain(dontAsk: Boolean) {
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("dont_ask_feedback", dontAsk)
            .apply()
    }

    private fun showOptionalExitDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_exit_feedback, null)
        val btnGiveFeedback = dialogView.findViewById<MaterialButton>(R.id.btnGiveFeedback)
        val btnJustExit = dialogView.findViewById<MaterialButton>(R.id.btnJustExit)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val chkDontAskAgain = dialogView.findViewById<CheckBox>(R.id.chkDontAskAgain)

        val dialog = MaterialAlertDialogBuilder(this, R.style.Dialog_Apptrap)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnGiveFeedback.setOnClickListener {
            if (chkDontAskAgain.isChecked) saveDontAskAgain(true)
            dialog.dismiss()
            openFeedbackActivity()
        }
        btnJustExit.setOnClickListener {
            if (chkDontAskAgain.isChecked) saveDontAskAgain(true)
            dialog.dismiss()
            finish()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun openFeedbackActivity() {
        val intent = Intent(this, FeedbackActivity::class.java)
        val bundle = Bundle().apply {
            putSerializable("scan_results", ArrayList(lastScanResults))
        }
        intent.putExtras(bundle)
        startActivity(intent)
    }

    private fun openConsistentApps() {
        if (lastScanResults.isEmpty()) {
            Toast.makeText(this, getString(R.string.nothing_to_export), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, ConsistentAppsActivity::class.java)
        val bundle = Bundle().apply {
            putSerializable("scan_results", ArrayList(lastScanResults))
        }
        intent.putExtras(bundle)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
