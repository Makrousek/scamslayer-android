package com.scamslayer.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.scamslayer.app.R
import com.scamslayer.app.ui.MainViewModel
import com.scamslayer.app.ui.components.PersonaCard
import com.scamslayer.app.ui.theme.ScamOrange
import com.scamslayer.app.ui.theme.ScamRed
import com.scamslayer.app.ui.theme.StatusActive
import com.scamslayer.app.ui.theme.StatusInactive

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onCreatePersona: () -> Unit = {},
    onEditPersona: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPersonaId by viewModel.selectedPersonaId.collectAsState()
    val showOnAllCalls by viewModel.showOnAllCalls.collectAsState()
    val previewPlaying by viewModel.previewPlaying.collectAsState()
    val scrollState = rememberScrollState()
    var personaToDelete by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = ScamRed,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "ScamSlayer",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val isActive = showOnAllCalls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(12.dp),
                            shape = CircleShape,
                            color = if (isActive) StatusActive else StatusInactive
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showOnAllCalls) "Přesměrování aktivní" else "Připraveno",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isActive) StatusActive else StatusInactive
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current Persona
                val currentPersona = uiState.personas.find { it.id == selectedPersonaId }
                val portraitRes = when (selectedPersonaId) {
                    "babicka_bozena" -> R.drawable.persona_babicka_bozena
                    "deda_frantisek" -> R.drawable.persona_deda_frantisek
                    "mlada_tereza" -> R.drawable.persona_mlada_tereza

                    "it_honza" -> R.drawable.persona_it_honza
                    else -> null
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val customPortraitUrl = currentPersona?.portraitUrl?.let { viewModel.getFullUrl(it) }
                    if (portraitRes != null) {
                        Image(
                            painter = painterResource(id = portraitRes),
                            contentDescription = currentPersona?.name,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else if (customPortraitUrl != null) {
                        AsyncImage(
                            model = customPortraitUrl,
                            contentDescription = currentPersona?.name,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = ScamOrange.copy(alpha = 0.15f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.padding(14.dp),
                                tint = ScamOrange
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Aktivní persona",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentPersona?.name ?: "Žádná persona nevybrána",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (currentPersona != null) {
                            Text(
                                text = currentPersona.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Forward toggle (always visible)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Automatické přesměrování",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Přesměruje odmítnuté hovory přes operátora. Nefunguje u předplacených karet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showOnAllCalls,
                    onCheckedChange = { viewModel.setShowOnAllCalls(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ScamRed,
                        checkedTrackColor = ScamRed.copy(alpha = 0.5f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Persona Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Vybrat personu",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (uiState.isLoadingPersonas) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = ScamRed
                )
            } else {
                TextButton(onClick = { viewModel.loadPersonas() }) {
                    Text(text = "Obnovit", color = ScamOrange)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.personasError != null) {
            Text(
                text = uiState.personasError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (uiState.personas.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                uiState.personas.forEach { persona ->
                    PersonaCard(
                        persona = persona,
                        isSelected = persona.id == selectedPersonaId,
                        isPlaying = previewPlaying == persona.id,
                        onClick = { viewModel.selectPersona(persona.id) },
                        onPlayPreview = { viewModel.playVoicePreview(persona.id) },
                        onStopPreview = { viewModel.stopVoicePreview() },
                        onDelete = if (persona.isCustom) {
                            { personaToDelete = persona.id }
                        } else null,
                        onEditPortrait = { onEditPersona(persona.id) },
                        portraitUrl = persona.portraitUrl?.let { viewModel.getFullUrl(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        } else if (!uiState.isLoadingPersonas) {
            Text(
                text = "Žádné persony nejsou k dispozici. Zkontrolujte připojení.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Create Persona button
        Button(
            onClick = onCreatePersona,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ScamOrange),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Vytvořit vlastní personu")
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // Delete confirmation dialog
    if (personaToDelete != null) {
        val personaName = uiState.personas.find { it.id == personaToDelete }?.name ?: "this persona"
        AlertDialog(
            onDismissRequest = { personaToDelete = null },
            title = { Text("Smazat personu", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "Opravdu chcete smazat personu \"$personaName\"?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    personaToDelete?.let { viewModel.deleteCustomPersona(it) }
                    personaToDelete = null
                }) {
                    Text("Smazat", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { personaToDelete = null }) {
                    Text("Zrušit", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
