package com.example.androidprivacyanalyzer.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.androidprivacyanalyzer.R

/**
 * Builds the plain-language "what this app can do" rows on the detail screen.
 *
 * Signals may arrive either as raw permission constants
 * ("android.permission.RECORD_AUDIO") or as already-friendly analyzer strings —
 * both are handled, so nothing technical ever reaches the screen.
 *
 * Each row is tinted by severity (danger wash, warn wash, or safe wash) to
 * match the polished prototype: the screen reads at a glance.
 */
object SignalViews {

    /** Inflates one row per signal into [container], staggering them in. */
    fun bind(
        container: ViewGroup,
        signals: List<String>,
        suspicious: Boolean,
        whyProvider: (String) -> String? = { null },
    ) {
        container.removeAllViews()
        container.isVisible = signals.isNotEmpty()
        val inflater = LayoutInflater.from(container.context)

        signals.forEachIndexed { index, signal ->
            if (suspicious) {
                // Tinted card row (red / orange wash) for genuinely worrying caps.
                val row = inflater.inflate(R.layout.item_signal, container, false)
                fill(container.context, row, signal, whyProvider(signal))
                container.addView(row)
                stagger(row, index)
            } else {
                // Plain check + text line for normal requests (prototype style:
                // the calm counterweight to the tinted rows).
                val row = inflater.inflate(R.layout.item_normal_line, container, false)
                row.findViewById<TextView>(R.id.normalLineText).text =
                    PermissionPlainLanguage.plainSignal(container.context, signal)
                container.addView(row)
                stagger(row, index)
            }
        }
    }

    private fun fill(
        context: Context,
        row: View,
        signal: String,
        why: String?,
    ) {
        val title: TextView = row.findViewById(R.id.signalTitle)
        val body: TextView = row.findViewById(R.id.signalWhy)
        val icon: ImageView = row.findViewById(R.id.signalIcon)
        val iconChip = row.findViewById<View>(R.id.signalIcon).parent as View
        val rowBg = row.findViewById<LinearLayout>(R.id.signalRow)

        // Determine the severity bucket. The prototype tints signal rows by
        // severity: danger = red, warn = orange. (Normal requests never reach
        // fill() anymore — they use the plain item_normal_line row.)
        val severity = severityOf(signal)

        // Apply the tinted background + icon chip + icon color
        rowBg.setBackgroundResource(severity.cardBg)
        iconChip.setBackgroundResource(severity.chipBg)
        icon.setColorFilter(context.themeColor(severity.attr))

        if (signal.startsWith("android.permission.")) {
            val described = PermissionPlainLanguage.describe(context, signal)
            title.text = described.title
            icon.setImageResource(described.iconRes)
        } else {
            title.text = PermissionPlainLanguage.plainSignal(context, signal)
            icon.setImageResource(R.drawable.ic_eye)
        }

        title.setTextColor(context.themeColor(severity.textAttr))
        body.text = why ?: context.getString(R.string.signal_why_generic)
    }

    private enum class Severity { DANGER, WARN }

    private fun severityOf(signal: String): Severity {
        // Signals that carry no privacy weight (informational) → WARN (orange)
        val s = signal.lowercase()
        val isInfoOnly = s.contains("informational") ||
            s.contains("bluetooth") || s.contains("nfc") || s.contains("phone state")
        return if (isInfoOnly) Severity.WARN else Severity.DANGER
    }

    private val Severity.cardBg get() = when (this) {
        Severity.DANGER -> R.drawable.signal_card_danger
        Severity.WARN -> R.drawable.signal_card_warn
    }
    private val Severity.chipBg get() = when (this) {
        Severity.DANGER -> R.drawable.signal_icon_chip_danger
        Severity.WARN -> R.drawable.signal_icon_chip_warn
    }
    private val Severity.attr get() = when (this) {
        Severity.DANGER -> R.attr.riskDanger
        Severity.WARN -> R.attr.riskWarn
    }
    private val Severity.textAttr get() = when (this) {
        Severity.DANGER -> R.attr.riskDangerOnContainer
        Severity.WARN -> R.attr.riskWarnOnContainer
    }

    private fun stagger(row: View, index: Int) {
        row.alpha = 0f
        row.translationY = 20f
        row.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(index * 60L)
            .setDuration(320)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
            .start()
    }
}
