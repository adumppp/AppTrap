package com.example.androidprivacyanalyzer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.androidprivacyanalyzer.ui.ConsistentAdapter
import com.google.android.material.button.MaterialButton

class ConsistentAppsActivity : AppCompatActivity() {

    @Suppress("UNCHECKED_CAST")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consistent_apps)

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        val recycler = findViewById<RecyclerView>(R.id.recyclerConsistent)
        recycler.layoutManager = LinearLayoutManager(this)

        val allResults: ArrayList<AppRisk> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("scan_results", ArrayList::class.java) as? ArrayList<AppRisk>
                    ?: arrayListOf()
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("scan_results") as? ArrayList<AppRisk> ?: arrayListOf()
            }

        val consistentApps = allResults.filter { it.verdict.severity == 0 }.sortedBy { it.appName }

        val txtSafeSub = findViewById<TextView>(R.id.txtSafeSub)
        txtSafeSub.text = if (consistentApps.isEmpty()) {
            getString(R.string.empty_body)
        } else {
            getString(R.string.safe_sub, consistentApps.size)
        }

        val adapter = ConsistentAdapter { appRisk ->
            startActivity(Intent(this, DetailActivity::class.java).apply {
                putExtra("app", appRisk)
            })
        }
        recycler.adapter = adapter
        adapter.submitList(consistentApps)
    }
}
