package com.example.androidprivacyanalyzer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.example.androidprivacyanalyzer.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Replaces the old "delete immediately" behaviour: explains what will happen,
 * then hands the user to the system uninstall screen. Nothing is removed
 * silently, which is what non-technical users expect.
 */
object DeleteGuideDialog {

    fun show(context: Context, appName: String, packageName: String) {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_delete_guide, null, false)

        view.findViewById<TextView>(R.id.txtDeleteTitle).text =
            context.getString(R.string.delete_title, appName)

        val dialog = MaterialAlertDialogBuilder(context, R.style.Dialog_Apptrap)
            .setView(view)
            .create()

        view.findViewById<MaterialButton>(R.id.btnConfirmDelete).setOnClickListener {
            dialog.dismiss()
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                )
            } catch (_: Exception) {
                Toast.makeText(context, R.string.delete_settings_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.btnCancelDelete).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
