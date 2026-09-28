package com.example.androidprivacyanalyzer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidprivacyanalyzer.AppRisk
import com.example.androidprivacyanalyzer.R

/**
 * Rows for the "needs a look" list on MainActivity (item_app_risk.xml).
 *
 * All colour/label decisions come from [RiskLevel] so the technical labels in
 * TrustVerdict.label never leak into the UI.
 */
class RiskAdapter(
    private val onClick: (AppRisk) -> Unit,
) : ListAdapter<AppRisk, RiskAdapter.Holder>(Diff) {

    object Diff : DiffUtil.ItemCallback<AppRisk>() {
        override fun areItemsTheSame(a: AppRisk, b: AppRisk) = a.packageName == b.packageName
        override fun areContentsTheSame(a: AppRisk, b: AppRisk) =
            a.verdict.severity == b.verdict.severity &&
                a.verdict.label == b.verdict.label &&
                a.verdict.signals == b.verdict.signals
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
        val role: TextView = view.findViewById(R.id.appRole)
        val badge: TextView = view.findViewById(R.id.riskBadge)
        val score: TextView = view.findViewById(R.id.riskScore)
        val summary: TextView = view.findViewById(R.id.signalsSummary)
        val stripe: View = view.findViewById(R.id.riskStripe)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_app_risk, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        val level = RiskLevel.ofLabel(item.verdict.label)

        holder.icon.loadAppIcon(item.packageName)
        holder.name.text = item.appName

        // "Tap to see what X can reach" hint — falls back to a neutral line.
        holder.role.text = context.getString(R.string.row_role_hint, item.appName)

        holder.badge.text = level.title(context)
        holder.badge.setTextColor(level.onContainer(context))
        holder.badge.background = level.badgeDrawable(context)

        holder.score.text = context.getString(R.string.row_score, item.verdict.severity)
        holder.stripe.setBackgroundColor(level.color(context))

        // Top two reasons only; the rest live on the detail screen.
        holder.summary.text = item.verdict.signals.take(2)
            .joinToString("  ·  ") { PermissionPlainLanguage.plainSignal(context, it) }

        holder.itemView.setOnClickListener { onClick(item) }
    }
}
