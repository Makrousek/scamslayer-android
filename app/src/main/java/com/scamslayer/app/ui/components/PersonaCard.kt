package com.scamslayer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.scamslayer.app.R
import com.scamslayer.app.data.model.PersonaDto
import com.scamslayer.app.ui.theme.ScamOrange
import com.scamslayer.app.ui.theme.ScamRed

private fun getPersonaPortrait(personaId: String): Int? {
    return when (personaId) {
        "babicka_bozena" -> R.drawable.persona_babicka_bozena
        "deda_frantisek" -> R.drawable.persona_deda_frantisek
        "mlada_tereza" -> R.drawable.persona_mlada_tereza
        "it_honza" -> R.drawable.persona_it_honza
        else -> null
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PersonaCard(
    persona: PersonaDto,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onEditPortrait: (() -> Unit)? = null,
    portraitUrl: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                ScamRed.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, ScamRed)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val localPortrait = getPersonaPortrait(persona.id)
            val portraitModifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .then(
                    if (onEditPortrait != null) Modifier.clickable { onEditPortrait() }
                    else Modifier
                )
            if (localPortrait != null) {
                Image(
                    painter = painterResource(id = localPortrait),
                    contentDescription = persona.name,
                    modifier = portraitModifier,
                    contentScale = ContentScale.Crop
                )
            } else if (portraitUrl != null) {
                AsyncImage(
                    model = portraitUrl,
                    contentDescription = persona.name,
                    modifier = portraitModifier,
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = portraitModifier,
                    shape = CircleShape,
                    color = if (isSelected) ScamRed else ScamOrange.copy(alpha = 0.2f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(14.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            ScamOrange
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = persona.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = persona.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }


            // Voice preview button
            IconButton(
                onClick = { if (isPlaying) onStopPreview() else onPlayPreview() }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop preview" else "Play voice preview",
                    tint = if (isPlaying) ScamRed else ScamOrange,
                    modifier = Modifier.size(28.dp)
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = ScamRed,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
