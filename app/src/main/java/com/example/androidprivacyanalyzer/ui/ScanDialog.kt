package com.example.androidprivacyanalyzer.ui

import android.app.Dialog
import android.content.Context
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.example.androidprivacyanalyzer.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Drives dialog_scan_loading.xml: breathing shield, tweened gradient progress,
 * bumping counters, pulsing "checking" line and a live elapsed timer.
 *
 * Usage:
 *   val scan = ScanDialog(this) { isScanning.set(false) }
 *   scan.show(total = apps.size)
 *   scan.update(done, appName, attention, fine)
 *   scan.dismiss()
 */
class ScanDialog(
    private val context: Context,
    private val onCancel: () -> Unit,
) {
    private var dialog: Dialog? = null
    private var startedAt = 0L
    private var total = 0

    private var root: View? = null
    private var progress: ProgressBar? = null
    private var txtCount: TextView? = null
    private var txtPercent: TextView? = null
    private var txtNow: TextView? = null
    private var txtElapsed: TextView? = null
    private var countAttention: TextView? = null
    private var countFine: TextView? = null

    private val ticker = object : Runnable {
        override fun run() {
            val elapsedView = txtElapsed ?: return
            val seconds = ((SystemClock.elapsedRealtime() - startedAt) / 1000L).toInt()
            elapsedView.text = context.getString(
                R.string.scan_elapsed, seconds / 60, seconds % 60
            )
            elapsedView.postDelayed(this, 1000L)
        }
    }

    fun show(total: Int) {
        this.total = total
        startedAt = SystemClock.elapsedRealtime()

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_scan_loading, null, false)
        root = view
        progress = view.findViewById(R.id.progressBar)
        txtCount = view.findViewById(R.id.txtProgressCount)
        txtPercent = view.findViewById(R.id.txtPercent)
        txtNow = view.findViewById(R.id.txtNowChecking)
        txtElapsed = view.findViewById(R.id.txtElapsed)
        countAttention = view.findViewById(R.id.countAttention)
        countFine = view.findViewById(R.id.countFine)

        UiAnimations.breathe(view.findViewById(R.id.scanShield))
        txtNow?.let { UiAnimations.pulse(it) }

        view.findViewById<MaterialButton>(R.id.btnCancelScan).setOnClickListener {
            onCancel()
            dismiss()
        }

        dialog = MaterialAlertDialogBuilder(context, R.style.Dialog_Apptrap)
            .setView(view)
            .setCancelable(false)
            .create()
            .also { it.show() }

        txtElapsed?.post(ticker)
        update(done = 0, currentApp = null, attention = 0, fine = 0)
    }

    fun update(done: Int, currentApp: String?, attention: Int, fine: Int) {
        if (dialog == null) return
        val percent = if (total == 0) 0 else (done * 100) / total

        progress?.let { UiAnimations.animateProgress(it, percent) }
        txtPercent?.text = context.getString(R.string.scan_percent, percent)
        txtCount?.text = context.getString(R.string.scan_progress, done, total)
        currentApp?.let { app ->
            txtNow?.text = context.getString(R.string.scan_now_checking, app)
        }

        countAttention?.let { UiAnimations.bumpTo(it, attention) }
        countFine?.let { UiAnimations.bumpTo(it, fine) }
    }

    fun dismiss() {
        txtElapsed?.removeCallbacks(ticker)
        dialog?.dismiss()
        dialog = null
    }
}
