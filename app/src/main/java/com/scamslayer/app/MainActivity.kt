package com.scamslayer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scamslayer.app.ui.MainViewModel
import com.scamslayer.app.ui.ScamSlayerNavigation
import com.scamslayer.app.ui.screens.SetupScreen
import com.scamslayer.app.ui.theme.ScamRed
import com.scamslayer.app.ui.theme.ScamSlayerTheme

class MainActivity : ComponentActivity() {

    private var permissionsGranted by mutableStateOf(false)
    private var navigateTo by mutableStateOf<String?>(null)

    private val requiredPermissions = buildList {
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_PHONE_NUMBERS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionsGranted = checkPermissions()
        navigateTo = intent?.getStringExtra("navigate_to")

        setContent {
            ScamSlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (permissionsGranted) {
                        val viewModel: MainViewModel = viewModel()
                        val isSetupComplete by viewModel.isSetupComplete.collectAsState()

                        when (isSetupComplete) {
                            null -> {
                                // Checking setup status
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = ScamRed,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            false -> {
                                SetupScreen(viewModel = viewModel)
                            }
                            true -> {
                                ScamSlayerNavigation(
                                    viewModel = viewModel,
                                    initialRoute = navigateTo
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Potřebná oprávnění",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "ScamSlayer potřebuje následující oprávnění:\n\n" +
                                        "- Telefonní hovory: přesměrování a detekce odmítnutých hovorů\n" +
                                        "- Telefonní číslo: automatická detekce vašeho čísla\n" +
                                        "- Oznámení: upozornění na nové nahrávky",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Start
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            Button(
                                onClick = { requestAllPermissions() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ScamRed
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Povolit oprávnění",
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        navigateTo = intent?.getStringExtra("navigate_to")
        if (navigateTo == "recordings") {
            ViewModelProvider(this)[MainViewModel::class.java].loadRecordings()
        }
    }

    override fun onResume() {
        super.onResume()
        permissionsGranted = checkPermissions()
    }

    private fun checkPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAllPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }
}
