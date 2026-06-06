package com.scamslayer.app.ui.screens
import com.scamslayer.app.ui.L

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.scamslayer.app.R
import com.scamslayer.app.data.model.CustomPersonaDetail
import com.scamslayer.app.ui.MainViewModel
import com.scamslayer.app.ui.theme.ScamOrange
import com.scamslayer.app.ui.theme.ScamRed
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults

private val PERSONA_LANGUAGES = listOf(
    "cs" to "Čeština",
    "en" to "English",
    "en-IN" to "English (Indian)",
    "en-CN" to "English (Chinese)",
    "de" to "Deutsch",
    "es" to "Español",
    "fr" to "Français",
    "it" to "Italiano",
    "pt" to "Português",
    "pl" to "Polski",
    "sk" to "Slovenčina",
    "uk" to "Українська",
    "ru" to "Русский",
    "nl" to "Nederlands",
    "sv" to "Svenska",
    "da" to "Dansk",
    "no" to "Norsk",
    "fi" to "Suomi",
    "ja" to "日本語",
    "ko" to "한국어",
    "zh" to "中文",
    "ar" to "العربية",
    "hi" to "हिन्दी",
    "tr" to "Türkçe",
    "vi" to "Tiếng Việt",
)

@Composable
fun EditPersonaScreen(
    viewModel: MainViewModel,
    personaId: String,
    onBack: () -> Unit
) {
    val strings by L.current.collectAsState()  // triggers recomposition on language change
    val uiState by viewModel.uiState.collectAsState()

    val isCustom = personaId.startsWith("custom_")

    var detail by remember { mutableStateOf<CustomPersonaDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var isRegeneratingPortrait by remember { mutableStateOf(false) }
    var isSavingProfile by remember { mutableStateOf(false) }
    var portraitKey by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf("") }
    var editedDescription by remember { mutableStateOf("") }
    var editedAge by remember { mutableIntStateOf(40) }
    var editedIsMale by remember { mutableStateOf(true) }
    var editedLanguage by remember { mutableStateOf("cs") }
    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var nameChanged by remember { mutableStateOf(false) }
    var editedSystemPrompt by remember { mutableStateOf("") }
    var systemPromptExpanded by remember { mutableStateOf(false) }
    var isSavingPrompt by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ScamRed,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = ScamRed,
        cursorColor = ScamRed,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
    )

    // Load persona detail
    LaunchedEffect(personaId) {
        if (isCustom) {
            viewModel.loadCustomPersonaDetail(personaId) { result, err ->
                if (result != null) {
                    detail = result
                    editedName = result.name
                    editedDescription = result.originalDescription
                    editedAge = result.age
                    editedIsMale = result.gender != "female"
                    editedLanguage = result.language
                    editedSystemPrompt = result.systemPrompt
                } else {
                    error = err ?: "Nepodařilo se načíst personu"
                }
                isLoading = false
            }
        } else {
            // System persona — build detail from personas list
            val persona = uiState.personas.find { it.id == personaId }
            if (persona != null) {
                detail = CustomPersonaDetail(
                    id = persona.id,
                    name = persona.name,
                    description = persona.description,
                    portraitUrl = persona.portraitUrl,
                    voiceId = "",
                    voiceSettings = emptyMap(),
                    isCustom = false,
                    language = persona.language
                )
                editedLanguage = persona.language
            } else {
                error = "Persona nenalezena"
            }
            isLoading = false
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(L.s.deletePersona, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "Opravdu chcete smazat personu \"${detail?.name}\"? Tuto akci nelze vrátit zpět.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteCustomPersona(personaId)
                    onBack()
                }) {
                    Text("Smazat", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Zrušit", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isCustom) L.s.editPersona else L.s.personaDetail,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        when {
            isLoading -> {
                CircularProgressIndicator(
                    color = ScamRed,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
            error != null -> {
                Text(text = error!!, color = MaterialTheme.colorScheme.error)
            }
            detail != null -> {
                val d = detail!!

                // Portrait + Name
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val localPortrait = when (d.id) {
                            "babicka_bozena" -> R.drawable.persona_babicka_bozena
                            "deda_frantisek" -> R.drawable.persona_deda_frantisek
                            "mlada_tereza" -> R.drawable.persona_mlada_tereza

                            "it_honza" -> R.drawable.persona_it_honza
                            else -> null
                        }
                        if (localPortrait != null) {
                            Image(
                                painter = painterResource(id = localPortrait),
                                contentDescription = d.name,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else if (d.portraitUrl != null) {
                            AsyncImage(
                                model = viewModel.getFullUrl(d.portraitUrl) + "?v=$portraitKey",
                                contentDescription = d.name,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isCustom) {
                            OutlinedTextField(
                                value = editedName,
                                onValueChange = {
                                    editedName = it
                                    nameChanged = it != d.name
                                },
                                label = { Text(L.s.name) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors
                            )
                        } else {
                            Text(
                                text = d.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = d.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isCustom) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Regenerate buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    isRegeneratingPortrait = true
                                    viewModel.regeneratePortrait(personaId) {
                                        isRegeneratingPortrait = false
                                        portraitKey++
                                    }
                                },
                                enabled = !isRegeneratingPortrait
                            ) {
                                if (isRegeneratingPortrait) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = ScamOrange
                                    )
                                } else {
                                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp), tint = ScamOrange)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(L.s.newPortrait, color = ScamOrange)
                            }

                        }
                        } // end if (isCustom)
                    }
                }

                // Language dropdown (for ALL personas, including system)
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = L.s.personaLanguage,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                        ExposedDropdownMenuBox(
                            expanded = languageDropdownExpanded,
                            onExpandedChange = { languageDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = PERSONA_LANGUAGES.firstOrNull { it.first == editedLanguage }?.second ?: editedLanguage,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageDropdownExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                colors = textFieldColors
                            )
                            ExposedDropdownMenu(
                                expanded = languageDropdownExpanded,
                                onDismissRequest = { languageDropdownExpanded = false }
                            ) {
                                PERSONA_LANGUAGES.forEach { (code, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            if (!isCustom && code != editedLanguage) {
                                                // For system personas: switch to equivalent in new language
                                                isSavingProfile = true
                                                viewModel.switchPersonaLanguage(personaId, code) { newId ->
                                                    isSavingProfile = false
                                                    if (newId != null) {
                                                        onBack()
                                                    }
                                                }
                                            }
                                            editedLanguage = code
                                            languageDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (isCustom) {
                Spacer(modifier = Modifier.height(16.dp))

                // Description + Age
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = editedDescription,
                            onValueChange = { editedDescription = it },
                            label = { Text(L.s.personaDescription) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            colors = textFieldColors
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "${L.s.age}: $editedAge",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = editedAge.toFloat(),
                            onValueChange = { editedAge = it.toInt() },
                            valueRange = 18f..90f,
                            colors = SliderDefaults.colors(
                                thumbColor = ScamRed,
                                activeTrackColor = ScamRed
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = L.s.gender,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { editedIsMale = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (editedIsMale) ScamRed else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (editedIsMale) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(L.s.male)
                            }
                            Button(
                                onClick = { editedIsMale = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!editedIsMale) ScamRed else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (!editedIsMale) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(L.s.female)
                            }
                        }

                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // System prompt
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = L.s.aiInstructions,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { systemPromptExpanded = !systemPromptExpanded }) {
                                Text(
                                    text = if (systemPromptExpanded) L.s.hide else L.s.show,
                                    color = ScamOrange
                                )
                            }
                        }

                        if (systemPromptExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = editedSystemPrompt.ifEmpty { "Žádné instrukce" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = L.s.changeInstructionsHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Save button
                Button(
                    onClick = {
                        if (nameChanged && editedName.isNotBlank()) {
                            viewModel.updatePersonaName(personaId, editedName.trim())
                        }
                        val genderStr = if (editedIsMale) "male" else "female"
                        val languageChanged = editedLanguage != d.language
                        val profileChanged = editedDescription != d.originalDescription || editedAge != d.age || genderStr != d.gender || languageChanged
                        if (editedDescription.isNotBlank() && profileChanged) {
                            isSavingProfile = true
                            viewModel.updateProfile(personaId, editedDescription.trim(), editedAge, genderStr, editedLanguage) {
                                isSavingProfile = false
                                viewModel.selectPersona(personaId)
                                onBack()
                            }
                        } else {
                            viewModel.selectPersona(personaId)
                            onBack()
                        }
                    },
                    enabled = !isSavingProfile,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ScamRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSavingProfile) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(L.s.generatingAndSaving)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(L.s.saveAndUse)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Spacer(modifier = Modifier.height(8.dp))

                // Delete button
                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(L.s.deletePersona)
                }
                } // end if (isCustom)
            }
        }

        Spacer(modifier = Modifier.height(300.dp))
    }
}
