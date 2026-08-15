package com.vicarriers.maxicodescanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * Talks to the existing DataWedge profile named exactly "MaxiCode".
 * Scan results arrive as a broadcast; the barcode plugin is not rewritten.
 */
class DataWedgeController(private val context: Context) {
    companion object {
        const val PROFILE_NAME = "MaxiCode"
        const val SCAN_INTENT_ACTION = "com.vicarriers.maxicodescanner.SCAN"

        private const val DATA_STRING_EXTRA = "com.symbol.datawedge.data_string"
        private const val LEGACY_DATA_STRING_EXTRA =
            "com.motorolasolutions.emdk.datawedge.data_string"
        private const val DECODE_DATA_EXTRA = "com.symbol.datawedge.decode_data"
        private const val LEGACY_DECODE_DATA_EXTRA =
            "com.motorolasolutions.emdk.datawedge.decode_data"
        private const val DATAWEDGE_API_ACTION = "com.symbol.datawedge.api.ACTION"
        private const val DATAWEDGE_PACKAGE = "com.symbol.datawedge"
        private const val SWITCH_TO_PROFILE_EXTRA = "com.symbol.datawedge.api.SWITCH_TO_PROFILE"
        private const val SET_CONFIG_EXTRA = "com.symbol.datawedge.api.SET_CONFIG"
        private const val INTENT_DELIVERY_BROADCAST = "2"

        fun extractScan(intent: Intent?): String? {
            if (intent == null) return null
            val fromBytes = decodeDataString(intent)
            val fromString =
                intent.getStringExtra(DATA_STRING_EXTRA)
                    ?: intent.getStringExtra(LEGACY_DATA_STRING_EXTRA)
            if (!fromBytes.isNullOrEmpty() && hasControlCharacters(fromBytes)) return fromBytes
            if (!fromString.isNullOrEmpty() && hasControlCharacters(fromString)) return fromString
            return fromBytes?.takeIf { it.isNotEmpty() } ?: fromString?.takeIf { it.isNotEmpty() }
        }

        private fun decodeDataString(intent: Intent): String? {
            val extras = intent.extras ?: return null
            val extra = extras.get(DECODE_DATA_EXTRA)
                ?: extras.get(LEGACY_DECODE_DATA_EXTRA)
                ?: return null
            val chunks = when (extra) {
                is ByteArray -> listOf(extra)
                is Array<*> -> extra.mapNotNull { it as? ByteArray }
                is Collection<*> -> extra.mapNotNull { it as? ByteArray }
                else -> emptyList()
            }.filter { it.isNotEmpty() }
            if (chunks.isEmpty()) return null
            return chunks.joinToString(separator = "") { it.toString(Charsets.ISO_8859_1) }
        }

        private fun hasControlCharacters(value: String): Boolean =
            value.any { ch ->
                ch == MaxiCodeParser.GS || ch == MaxiCodeParser.RS || ch == MaxiCodeParser.EOT
            }
    }

    private var receiver: BroadcastReceiver? = null

    fun register(onScanReceived: (String) -> Unit) {
        if (receiver != null) return

        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val payload = extractScan(intent) ?: return
                if (payload.isNotEmpty()) onScanReceived(payload)
            }
        }

        val filter = IntentFilter(SCAN_INTENT_ACTION).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        ContextCompat.registerReceiver(
            context,
            broadcastReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        receiver = broadcastReceiver
    }

    fun unregister() {
        receiver?.let(context::unregisterReceiver)
        receiver = null
    }

    fun switchToProfile() {
        sendApi(
            Intent(DATAWEDGE_API_ACTION).apply {
                putExtra(SWITCH_TO_PROFILE_EXTRA, PROFILE_NAME)
            },
        )
    }

    /** Call on resume so DataWedge activates this profile without a manual Associated Apps entry. */
    fun associateAppAndConfigureOutput() {
        associateApp()
        applyBroadcastOutput()
        switchToProfile()
    }

    /**
     * Programmatically associates this package/activity with the existing "MaxiCode" profile.
     * The profile is not associated in the DataWedge UI.
     */
    fun associateApp() {
        val appConfig = Bundle().apply {
            putString("PACKAGE_NAME", context.packageName)
            putStringArray(
                "ACTIVITY_LIST",
                arrayOf("${context.packageName}.MainActivity", "*"),
            )
        }
        val profileConfig = Bundle().apply {
            putString("PROFILE_NAME", PROFILE_NAME)
            putString("PROFILE_ENABLED", "true")
            putString("CONFIG_MODE", "UPDATE")
            putParcelableArray("APP_LIST", arrayOf(appConfig))
        }
        sendApi(
            Intent(DATAWEDGE_API_ACTION).apply {
                putExtra(SET_CONFIG_EXTRA, profileConfig)
            },
        )
    }

    /**
     * Updates only Intent/Keystroke output on the existing MaxiCode profile.
     * Does not reset barcode settings (MaxiCode-only decoder stays as configured).
     */
    fun applyBroadcastOutput() {
        val intentPlugin = Bundle().apply {
            putString("PLUGIN_NAME", "INTENT")
            putString("RESET_CONFIG", "true")
            putBundle(
                "PARAM_LIST",
                Bundle().apply {
                    putString("intent_output_enabled", "true")
                    putString("intent_action", SCAN_INTENT_ACTION)
                    putString("intent_category", Intent.CATEGORY_DEFAULT)
                    putString("intent_delivery", INTENT_DELIVERY_BROADCAST)
                },
            )
        }
        val keystrokePlugin = Bundle().apply {
            putString("PLUGIN_NAME", "KEYSTROKE")
            putString("RESET_CONFIG", "true")
            putBundle(
                "PARAM_LIST",
                Bundle().apply {
                    putString("keystroke_output_enabled", "false")
                },
            )
        }
        val profileConfig = Bundle().apply {
            putString("PROFILE_NAME", PROFILE_NAME)
            putString("PROFILE_ENABLED", "true")
            putString("CONFIG_MODE", "UPDATE")
            putParcelableArrayList("PLUGIN_CONFIG", arrayListOf(intentPlugin, keystrokePlugin))
        }
        sendApi(
            Intent(DATAWEDGE_API_ACTION).apply {
                putExtra(SET_CONFIG_EXTRA, profileConfig)
            },
        )
    }

    private fun sendApi(intent: Intent) {
        intent.setPackage(DATAWEDGE_PACKAGE)
        context.sendOrderedBroadcast(intent, null)
    }
}
