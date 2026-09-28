package com.example.androidprivacyanalyzer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.androidprivacyanalyzer.ui.LanguageToggle
import com.example.androidprivacyanalyzer.ui.UiAnimations
import com.google.android.material.button.MaterialButton

/**
 * First-run explainer (Theme.Apptrap.Onboarding). Shown once from MainActivity;
 * both buttons persist the flag and close back to the home screen.
 */
class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        // Language chooser is the FIRST thing users see.
        LanguageToggle.bind(findViewById(android.R.id.content))

        fillPoint(R.id.point1, R.drawable.ic_shield_check, R.string.onb_point_1_title, R.string.onb_point_1_body)
        fillPoint(R.id.point2, R.drawable.ic_security, R.string.onb_point_2_title, R.string.onb_point_2_body)
        fillPoint(R.id.point3, R.drawable.ic_info, R.string.onb_point_3_title, R.string.onb_point_3_body)

        findViewById<MaterialButton>(R.id.btnStart).setOnClickListener { done() }
        findViewById<MaterialButton>(R.id.btnSkip).setOnClickListener { done() }
    }

    override fun onResume() {
        super.onResume()
        UiAnimations.breathe(findViewById(R.id.heroShield))
    }

    private fun fillPoint(id: Int, icon: Int, title: Int, body: Int) {
        val point = findViewById<View>(id)
        point.findViewById<ImageView>(R.id.pointIcon).setImageResource(icon)
        point.findViewById<TextView>(R.id.pointTitle).setText(title)
        point.findViewById<TextView>(R.id.pointBody).setText(body)
    }

    private fun done() {
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_done", true)
            .apply()
        finish()
    }
}
