package com.example.androidprivacyanalyzer.ui

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.androidprivacyanalyzer.R
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * Wires view_language_toggle.xml. The toggle is now a visible pill on the main
 * screen instead of a hidden menu item, and it switches locale per-app
 * (no manual Configuration juggling, works on API 26+ via AppCompat).
 */
object LanguageToggle {

    private const val EN = "en"
    private const val MS = "ms"

    /** [view] may be the toggle group itself or any parent containing it. */
    fun bind(view: View) {
        val group = view as? MaterialButtonToggleGroup
            ?: view.findViewById<MaterialButtonToggleGroup>(R.id.langToggle)
            ?: return

        fun render() {
            val target = if (current(group.context) == MS) R.id.btnLangBm else R.id.btnLangEn
            if (group.checkedButtonId != target) group.check(target)
        }

        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val tag = if (checkedId == R.id.btnLangBm) MS else EN
            if (current(group.context) != tag) apply(tag)
        }
        render()
    }

    fun current(context: Context): String =
        AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.get(0)?.language
            ?: context.resources.configuration.locales[0].language

    private fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
