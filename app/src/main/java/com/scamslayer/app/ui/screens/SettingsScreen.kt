package com.scamslayer.app.ui.screens
import com.scamslayer.app.ui.L

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scamslayer.app.ui.MainViewModel
import com.scamslayer.app.ui.theme.ScamOrange
import com.scamslayer.app.ui.theme.ScamRed

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val strings by L.current.collectAsState()  // triggers recomposition on language change
    val scrollState = rememberScrollState()
    val isPremium by viewModel.isPremium.collectAsState()
    val userPhone by viewModel.userPhoneNumber.collectAsState()
    var promoCode by remember { mutableStateOf("") }
    var promoMessage by remember { mutableStateOf<String?>(null) }
    var promoSuccess by remember { mutableStateOf(false) }
    var showChangePhone by remember { mutableStateOf(false) }
    var newPhone by remember { mutableStateOf("") }

    // Use strings.lang as key to force full recomposition on language change
    androidx.compose.runtime.key(strings.lang) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                tint = ScamRed,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = L.s.guide,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Language selector
        val currentLang by viewModel.personaLanguage.collectAsState()
        val languages = listOf(
            "" to "Auto", "cs" to "Čeština", "en" to "English",
            "de" to "Deutsch", "es" to "Español", "fr" to "Français",
            "it" to "Italiano", "pt" to "Português", "pl" to "Polski",
            "sk" to "Slovenčina", "uk" to "Українська", "ru" to "Русский",
            "nl" to "Nederlands", "sv" to "Svenska", "da" to "Dansk",
            "no" to "Norsk", "fi" to "Suomi", "ja" to "日本語",
            "ko" to "한국어", "zh" to "中文", "ar" to "العربية",
            "hi" to "हिन्दी", "tr" to "Türkçe", "vi" to "Tiếng Việt",
        )
        val displayLang = languages.find { it.first == currentLang }?.second ?: "Auto"
        var langExpanded by remember { mutableStateOf(false) }

        SectionHeader(icon = Icons.Default.Info, title = L.s.languageLabel)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box {
                    Button(
                        onClick = { langExpanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(displayLang, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    }
                    DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        languages.forEach { (code, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                viewModel.setPersonaLanguage(code)
                                langExpanded = false
                            })
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(icon = Icons.Default.PhoneInTalk, title = L.s.howItWorks)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                GuideStep(number = "1", text = L.s.step1)
                GuideStep(number = "2", text = L.s.step2)
                GuideStep(number = "3", text = L.s.step3)
                GuideStep(number = "4", text = L.s.step4)
                GuideStep(number = "5", text = L.s.step5)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = L.s.normalCallsNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = ScamOrange
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = L.s.howForwardingWorks,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = L.s.forwardingExplanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = L.s.ifAutoDoesntWork,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = L.s.dialerExplanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = L.s.importantNote,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ScamRed
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = L.s.prepaidWarning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(icon = Icons.Default.Shield, title = L.s.personas)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = L.s.personasDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = L.s.customPersonaDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(icon = Icons.Default.Lightbulb, title = L.s.tips)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TipItem(L.s.tip1)
                TipItem(L.s.tip2)
                TipItem(L.s.tip3)
                TipItem(L.s.tip4)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Account
        SectionHeader(icon = Icons.Default.Person, title = L.s.account)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${L.s.phoneNumber}: $userPhone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (showChangePhone) {
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text(L.s.phoneNumber) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ScamRed,
                            focusedLabelColor = ScamRed,
                            cursorColor = ScamRed
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (newPhone.isNotBlank()) {
                                viewModel.setUserPhoneNumber(newPhone.trim())
                                showChangePhone = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ScamRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(L.s.save)
                    }
                } else {
                    TextButton(onClick = { showChangePhone = true; newPhone = userPhone }) {
                        Text(L.s.changeNumber, color = ScamOrange)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(icon = Icons.Default.Info, title = L.s.about)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ScamSlayer v1.4",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = L.s.aboutDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
    } // end key(strings.lang)
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = ScamRed, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun GuideStep(number: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(text = "$number. ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = ScamRed, modifier = Modifier.width(24.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TipItem(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(text = "\u2022 ", style = MaterialTheme.typography.bodyMedium, color = ScamOrange, modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
