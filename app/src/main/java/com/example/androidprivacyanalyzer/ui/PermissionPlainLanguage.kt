package com.example.androidprivacyanalyzer.ui

import android.Manifest
import android.content.Context
import android.widget.ImageView
import com.example.androidprivacyanalyzer.R

/**
 * Turns raw permission names into sentences a non-technical user understands.
 * Never show a permission constant in the UI — always route it through here.
 *
 * Usage:
 *   val row = PermissionPlainLanguage.describe(context, "android.permission.READ_CONTACTS")
 *   signalTitle.text = row.title
 *   signalIcon.setImageResource(row.iconRes)
 */
object PermissionPlainLanguage {

    data class Described(val title: String, val iconRes: Int)

    fun describe(context: Context, permission: String): Described {
        val (stringRes, iconRes) = when (permission) {
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.GET_ACCOUNTS -> R.string.perm_contacts to R.drawable.ic_eye

            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS -> R.string.perm_sms to R.drawable.ic_eye

            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION -> R.string.perm_location to R.drawable.ic_eye

            Manifest.permission.CAMERA -> R.string.perm_camera to R.drawable.ic_eye
            Manifest.permission.RECORD_AUDIO -> R.string.perm_mic to R.drawable.ic_eye

            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG -> R.string.perm_call_log to R.drawable.ic_eye

            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE -> R.string.perm_phone to R.drawable.ic_security

            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> R.string.perm_storage to R.drawable.ic_security

            Manifest.permission.SYSTEM_ALERT_WINDOW -> R.string.perm_overlay to R.drawable.ic_overlay
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE -> R.string.perm_accessibility to R.drawable.ic_overlay
            Manifest.permission.REQUEST_INSTALL_PACKAGES -> R.string.perm_install to R.drawable.ic_overlay
            Manifest.permission.RECEIVE_BOOT_COMPLETED -> R.string.perm_boot to R.drawable.ic_security

            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR -> R.string.perm_calendar to R.drawable.ic_security

            Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE -> R.string.perm_notifications to R.drawable.ic_notifications
            Manifest.permission.INTERNET -> R.string.perm_internet to R.drawable.ic_security

            else -> null to R.drawable.ic_security
        }

        val title = stringRes
            ?.let(context::getString)
            ?: context.getString(R.string.perm_generic, humanize(permission))

        return Described(title, iconRes)
    }

    /**
     * TrustEngine capability keys ("sms", "contacts", …) → friendly copy.
     * Returns null for keys without a dedicated string.
     */
    private fun capabilityString(key: String): Int? = when (key.lowercase()) {
        "contacts" -> R.string.perm_contacts
        "sms" -> R.string.perm_sms
        "location" -> R.string.perm_location
        "camera" -> R.string.perm_camera
        "mic" -> R.string.perm_mic
        "call_log" -> R.string.perm_call_log
        "phone" -> R.string.perm_phone
        "storage" -> R.string.perm_storage
        "overlay" -> R.string.perm_overlay
        "install_unknown", "install" -> R.string.perm_install
        "boot" -> R.string.perm_boot
        "notifications" -> R.string.perm_notifications
        "network", "internet" -> R.string.perm_internet
        else -> null
    }

    /** TrustEngine's pretty capability names ("Contacts access") → friendly copy. */
    private fun prettyCapabilityString(pretty: String): Int? = when (pretty.lowercase()) {
        "contacts access" -> R.string.perm_contacts
        "sms access" -> R.string.perm_sms
        "location access" -> R.string.perm_location
        "camera access" -> R.string.perm_camera
        "microphone access" -> R.string.perm_mic
        "call log access" -> R.string.perm_call_log
        "phone state access" -> R.string.perm_phone
        "storage access" -> R.string.perm_storage
        "overlay permission" -> R.string.perm_overlay
        "install unknown apps" -> R.string.perm_install
        "runs after restart" -> R.string.perm_boot
        "notification access" -> R.string.perm_notifications
        "network access" -> R.string.perm_internet
        else -> null
    }

    private val unexpectedCapability =
        Regex("""Unexpected capability '([A-Za-z0-9_]+)'""")

    /**
     * Best-effort translation of an analyzer signal/capability string into
     * plain language. Falls back to the original text when no mapping exists.
     */
    fun plainSignal(context: Context, raw: String): String {
        unexpectedCapability.find(raw)?.let { match ->
            capabilityString(match.groupValues[1])?.let { return context.getString(it) }
        }
        prettyCapabilityString(raw)?.let { return context.getString(it) }
        return raw
    }

    /** Loads the real app icon, falling back to the shield placeholder. */
    fun loadIcon(imageView: ImageView, packageName: String) {
        try {
            val icon = imageView.context.packageManager.getApplicationIcon(packageName)
            imageView.setImageDrawable(icon)
        } catch (_: Exception) {
            imageView.setImageResource(R.drawable.ic_security)
        }
    }

    /** Last-resort fallback: ACCESS_WIFI_STATE -> "access wifi state". */
    private fun humanize(permission: String): String =
        permission.substringAfterLast('.').replace('_', ' ').lowercase()
}

/** Convenience extension used by the adapters. */
fun ImageView.loadAppIcon(packageName: String) =
    PermissionPlainLanguage.loadIcon(this, packageName)
