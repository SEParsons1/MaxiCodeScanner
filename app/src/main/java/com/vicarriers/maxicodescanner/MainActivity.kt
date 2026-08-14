package com.vicarriers.maxicodescanner

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.vicarriers.maxicodescanner.ui.ScanScreen
import com.vicarriers.maxicodescanner.ui.theme.MaxiCodeScannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val scanBuffer = StringBuilder()
    private lateinit var postalDirectory: PostalDirectory
    private lateinit var dataWedge: DataWedgeController
    private lateinit var decodeSound: DecodeSound
    private lateinit var ernNames: ErnNamesClient

    private var rawPayload by mutableStateOf("")
    private var tracking by mutableStateOf("")
    private var name by mutableStateOf("")
    private var city by mutableStateOf("")
    private var postalCode by mutableStateOf("")
    private var nameLookupGeneration = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        postalDirectory = PostalDirectory(this)
        dataWedge = DataWedgeController(this)
        decodeSound = DecodeSound(this)
        ernNames = ErnNamesClient()
        setContent {
            MaxiCodeScannerTheme {
                Surface(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    ScanScreen(
                        rawPayload = rawPayload,
                        tracking = tracking,
                        name = name,
                        city = city,
                        postalCode = postalCode,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dataWedge.register(::applyScan)
    }

    override fun onResume() {
        super.onResume()
        dataWedge.associateAppAndConfigureOutput()
    }

    override fun onStop() {
        dataWedge.unregister()
        super.onStop()
    }

    override fun onDestroy() {
        decodeSound.release()
        postalDirectory.close()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val unicode = event.unicodeChar
        val isScanKey =
            event.keyCode == KeyEvent.KEYCODE_ENTER ||
                event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                event.keyCode == KeyEvent.KEYCODE_DEL ||
                unicode != 0
        if (!isScanKey) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true

        when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                applyScan(scanBuffer.toString())
                scanBuffer.clear()
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                if (scanBuffer.isNotEmpty()) {
                    scanBuffer.deleteCharAt(scanBuffer.lastIndex)
                }
                return true
            }
        }

        scanBuffer.append(unicode.toChar())
        return true
    }

    private fun applyScan(raw: String) {
        if (raw.isEmpty()) return
        rawPayload = raw
        val parsed = MaxiCodeParser.parse(raw)
        tracking = MaxiCodeParser.formatTracking(parsed.tracking.orEmpty().uppercase())
        postalCode = parsed.postalCode.orEmpty().uppercase()
        city = postalDirectory.cityForPostal(parsed.postalCode).orEmpty()
        name = ""
        val compactTracking = parsed.tracking
        if (compactTracking == null) return
        decodeSound.play()
        val generation = ++nameLookupGeneration
        lifecycleScope.launch(Dispatchers.IO) {
            val found = ernNames.nameForTracking(compactTracking).orEmpty()
            if (generation != nameLookupGeneration) return@launch
            withContext(Dispatchers.Main) {
                if (generation == nameLookupGeneration) {
                    name = found
                }
            }
        }
    }
}
