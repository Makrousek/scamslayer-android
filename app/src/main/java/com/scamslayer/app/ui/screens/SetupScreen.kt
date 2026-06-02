package com.scamslayer.app.ui.screens

import com.scamslayer.app.ui.L
import android.Manifest
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.scamslayer.app.ui.MainViewModel
import com.scamslayer.app.ui.theme.ScamOrange
import com.scamslayer.app.ui.theme.ScamRed

@Composable
fun SetupScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Try to auto-detect phone number
    LaunchedEffect(Unit) {
        try {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_NUMBERS
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                @Suppress("MissingPermission")
                val tm = context.getSystemService(TelephonyManager::class.java)
                val detected = tm?.line1Number
                if (!detected.isNullOrBlank()) {
                    phoneNumber = detected
                }
            }
        } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = ScamRed,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Vítejte ve ScamSlayer",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = L.s.setupDesc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = L.s.enterPhone,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = {
                phoneNumber = it
                error = null
            },
            label = { Text("Telefonní číslo") },
            placeholder = { Text("+420 736 353 745") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ScamRed,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = ScamRed,
                cursorColor = ScamRed,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Vaše číslo potřebujeme k přiřazení nahrávek — při přesměrování hovoru je to jediný způsob, jak poznat, že nahrávka patří vám. S nikým ho nesdílíme.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val cleaned = phoneNumber.replace(" ", "").replace("-", "")
                if (!cleaned.startsWith("+")) {
                    error = "Číslo musí začínat mezinárodní předvolbou (např. +420)"
                    return@Button
                }
                if (cleaned.length < 9) {
                    error = "Zadejte platné telefonní číslo"
                    return@Button
                }
                viewModel.completeSetup(cleaned)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ScamRed),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = L.s.continueBtn,
                modifier = Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(200.dp))
    }
}
