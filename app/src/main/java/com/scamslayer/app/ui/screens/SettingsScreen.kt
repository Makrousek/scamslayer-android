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
    val scrollState = rememberScrollState()
    val isPremium by viewModel.isPremium.collectAsState()
    val userPhone by viewModel.userPhoneNumber.collectAsState()
    var promoCode by remember { mutableStateOf("") }
    var promoMessage by remember { mutableStateOf<String?>(null) }
    var promoSuccess by remember { mutableStateOf(false) }
    var showChangePhone by remember { mutableStateOf(false) }
    var newPhone by remember { mutableStateOf("") }

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
        val languages = listOf("" to "Auto", "cs" to "Čeština", "en" to "English")
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
                        Text(
                            text = displayLang,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = langExpanded,
                        onDismissRequest = { langExpanded = false }
                    ) {
                        languages.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.setPersonaLanguage(code)
                                    langExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(icon = Icons.Default.PhoneInTalk, title = "Jak to funguje")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                GuideStep(number = "1", text = "Zapněte \"Automatické přesměrování na AI\" na hlavní obrazovce")
                GuideStep(number = "2", text = "Když vám zavolá podvodník, odmítněte hovor červeným tlačítkem")
                GuideStep(number = "3", text = "Operátor automaticky přesměruje odmítnutý hovor na naši AI")
                GuideStep(number = "4", text = "AI persona zvedne a zdržuje podvodníka na lince")
                GuideStep(number = "5", text = "Poslechněte si nahrávku v záložce Nahrávky")
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Normální hovory zvedejte jako obvykle. Na AI jdou pouze odmítnuté hovory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ScamOrange
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Jak funguje přesměrování",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Aplikace nastaví přesměrování obsazených/odmítnutých hovorů přes vašeho operátora (USSD kód **67*). " +
                        "Když odmítnete hovor, operátor ho automaticky přepojí na naše AI číslo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pokud automatické nastavení nefunguje",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Na některých telefonech se může otevřít dialer s USSD kódem (vypadá jako **67*číslo#). " +
                        "Stačí stisknout tlačítko Volat pro potvrzení — " +
                        "tím řeknete operátorovi, kam má odmítnuté hovory přesměrovat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Důležité upozornění",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ScamRed
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ScamSlayer funguje pouze pokud váš operátor podporuje přesměrování hovorů. " +
                        "Předplacené karty většiny operátorů přesměrování nepodporují — " +
                        "v takovém případě aplikace nebude fungovat. " +
                        "Pro plnou funkčnost je potřeba paušální tarif.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(icon = Icons.Default.Shield, title = "Persony")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Každá persona má unikátní osobnost, hlas a příběh. AI zůstane v roli po celý hovor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Můžete si vytvořit vlastní personu s popisem, AI portrétem a hlasem podle vašeho výběru. " +
                        "Vlastní personu můžete přejmenovat a dát jí své skutečné jméno — " +
                        "někteří podvodníci mají telefonní číslo spojené se jménem, takže persona s vaším jménem bude působit důvěryhodněji.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(icon = Icons.Default.Lightbulb, title = "Tipy")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TipItem("Zkoušejte různé persony — některé fungují lépe na určité typy podvodů")
                TipItem("Čím déle AI udrží podvodníka na lince, tím víc mu plýtvá časem")
                TipItem("Sdílejte vtipné nahrávky s přáteli a šiřte povědomí")
                TipItem("Vlastní persony s detailním popisem fungují nejlépe")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Account
        SectionHeader(icon = Icons.Default.Person, title = "Účet")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Telefonní číslo: $userPhone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (showChangePhone) {
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Nové telefonní číslo") },
                        placeholder = { Text("+420...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ScamRed,
                            focusedLabelColor = ScamRed,
                            cursorColor = ScamRed
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val cleaned = newPhone.replace(" ", "").replace("-", "")
                                if (cleaned.startsWith("+") && cleaned.length >= 9) {
                                    viewModel.setUserPhoneNumber(cleaned)
                                    viewModel.loadPersonas()
                                    viewModel.loadRecordings()
                                    showChangePhone = false
                                    newPhone = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ScamRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Uložit")
                        }
                        TextButton(onClick = { showChangePhone = false; newPhone = "" }) {
                            Text("Zrušit")
                        }
                    }
                } else {
                    TextButton(onClick = { showChangePhone = true; newPhone = userPhone }) {
                        Text("Změnit číslo", color = ScamOrange)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(icon = Icons.Default.Info, title = "O aplikaci")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ScamSlayer v1.0",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "AI asistent proti podvodným hovorům. Přesměruje spam hovory na AI, která podvodníka zdržuje a nahrává celý rozhovor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = ScamOrange
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GuideStep(number: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium,
            color = ScamRed,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TipItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "\u2022 ",
            style = MaterialTheme.typography.bodyMedium,
            color = ScamOrange,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
