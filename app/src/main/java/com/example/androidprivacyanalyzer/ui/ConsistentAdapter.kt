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

/** Rows for ConsistentAppsActivity — the reassuring "these look normal" list. */
class ConsistentAdapter(
    private val onClick: (AppRisk) -> Unit,
) : ListAdapter<AppRisk, ConsistentAdapter.Holder>(Diff) {

    object Diff : DiffUtil.ItemCallback<AppRisk>() {
        override fun areItemsTheSame(a: AppRisk, b: AppRisk) = a.packageName == b.packageName
        override fun areContentsTheSame(a: AppRisk, b: AppRisk) =
            a.verdict.severity == b.verdict.severity
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
        val badge: TextView = view.findViewById(R.id.safeBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_consistent_app, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.icon.loadAppIcon(item.packageName)
        holder.name.text = item.appName
        holder.badge.text = RiskLevel.CONSISTENT.title(holder.itemView.context)
        holder.itemView.setOnClickListener { onClick(item) }
    }
}
