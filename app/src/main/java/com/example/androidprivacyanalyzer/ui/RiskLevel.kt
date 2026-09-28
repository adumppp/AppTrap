package com.example.androidprivacyanalyzer.ui

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import com.example.androidprivacyanalyzer.R

/**
 * Single source of truth for how a severity score is presented.
 *
 * Technical labels (CONSISTENT / OVERREACH / DECEPTIVE / SPYWARE-LIKE) stay in
 * the analyzer; the UI only ever shows the friendly strings below.
 */
enum class RiskLevel(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @AttrRes val colorAttr: Int,
    @AttrRes val onContainerAttr: Int,
) {
    CONSISTENT(
        R.string.risk_consistent, R.string.risk_consistent_body,
        R.attr.riskSafe, R.attr.riskSafeOnContainer,
    ),
    OVERREACH(
        R.string.risk_overreach, R.string.risk_overreach_body,
        R.attr.riskCaution, R.attr.riskCautionOnContainer,
    ),
    DECEPTIVE(
        R.string.risk_deceptive, R.string.risk_deceptive_body,
        R.attr.riskWarn, R.attr.riskWarnOnContainer,
    ),
    SPYWARE_LIKE(
        R.string.risk_spyware, R.string.risk_spyware_body,
        R.attr.riskDanger, R.attr.riskDangerOnContainer,
    );

    fun title(context: Context): String = context.getString(titleRes)
    fun body(context: Context): String = context.getString(bodyRes)
    fun color(context: Context): Int = context.themeColor(colorAttr)
    fun onContainer(context: Context): Int = context.themeColor(onContainerAttr)

    /** Pill background matching this level (mirrors the Badge.Apptrap.* styles). */
    fun badgeDrawable(context: Context): Drawable? = context.getDrawable(
        when (this) {
            CONSISTENT -> R.drawable.badge_safe
            OVERREACH -> R.drawable.badge_caution
            DECEPTIVE -> R.drawable.badge_warn
            SPYWARE_LIKE -> R.drawable.badge_danger
        }
    )

    companion object {
        /** Bridges the string labels coming from TrustVerdict.label. */
        fun ofLabel(label: String): RiskLevel = when (label.uppercase()) {
            "CONSISTENT" -> CONSISTENT
            "OVERREACH" -> OVERREACH
            "DECEPTIVE" -> DECEPTIVE
            else -> SPYWARE_LIKE
        }
    }
}

/** Resolves a theme colour attribute (works in light and dark automatically). */
fun Context.themeColor(@AttrRes attr: Int): Int {
    val typed = obtainStyledAttributes(intArrayOf(attr))
    try {
        return typed.getColor(0, 0)
    } finally {
        typed.recycle()
    }
}
