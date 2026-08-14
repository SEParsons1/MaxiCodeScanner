package com.vicarriers.maxicodescanner.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vicarriers.maxicodescanner.visualizeControlCharacters
import com.vicarriers.maxicodescanner.ui.theme.BorderGray
import com.vicarriers.maxicodescanner.ui.theme.Muted

@Composable
fun ScanScreen(
    rawPayload: String,
    tracking: String,
    city: String,
    postalCode: String,
    modifier: Modifier = Modifier,
) {
    DisableSelection {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RawPayloadField(rawPayload)
            ResultField(label = "Tracking number", value = tracking)
            ResultField(label = "City", value = city)
            ResultField(label = "Postal code", value = postalCode)
        }
    }
}

@Composable
private fun RawPayloadField(rawPayload: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Raw payload",
            style = MaterialTheme.typography.labelLarge,
            color = Muted,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp, max = 160.dp)
                .border(1.dp, BorderGray, RoundedCornerShape(4.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = visualizeControlCharacters(rawPayload),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun ResultField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Muted,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderGray, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
        }
    }
}
