package com.scamslayer.app.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scamslayer.app.ui.theme.ScamOrange
import com.scamslayer.app.ui.theme.ScamRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import com.scamslayer.app.data.api.ApiClient
import java.io.File

@Composable
fun AudioPlayer(
    audioUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var localFile by remember { mutableStateOf<File?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Download audio file via OkHttp (supports ngrok header), then prepare MediaPlayer
    LaunchedEffect(audioUrl) {
        isLoading = true
        errorMsg = null
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(audioUrl)
                    .build()
                val response = ApiClient.okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    errorMsg = "HTTP ${response.code}"
                    isLoading = false
                    return@withContext
                }
                val tmpFile = File(context.cacheDir, "audio_${audioUrl.hashCode()}.wav")
                tmpFile.outputStream().use { out ->
                    response.body?.byteStream()?.copyTo(out)
                }
                localFile = tmpFile
                Log.i("AudioPlayer", "Downloaded ${tmpFile.length()} bytes to ${tmpFile.path}")

                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(tmpFile.absolutePath)
                    prepare()
                }
                duration = mp.duration.toLong()
                mediaPlayer = mp
                isPrepared = true
                isLoading = false
                Log.i("AudioPlayer", "MediaPlayer ready, duration=${mp.duration}ms")
            } catch (e: Exception) {
                errorMsg = "Error: ${e.message}"
                isLoading = false
                Log.e("AudioPlayer", "Failed to load audio", e)
            }
        }
    }

    DisposableEffect(audioUrl) {
        onDispose {
            try { mediaPlayer?.release() } catch (_: Exception) {}
            try { localFile?.delete() } catch (_: Exception) {}
        }
    }

    // Completion listener (must set on main thread after player is created)
    LaunchedEffect(mediaPlayer) {
        mediaPlayer?.setOnCompletionListener {
            isPlaying = false
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!isUserSeeking) {
                try {
                    val mp = mediaPlayer ?: break
                    currentPosition = mp.currentPosition.toLong()
                    sliderPosition = if (duration > 0) {
                        currentPosition.toFloat() / duration.toFloat()
                    } else 0f
                } catch (_: Exception) {}
            }
            delay(200L)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        when {
            errorMsg != null -> {
                Text(
                    text = errorMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            isLoading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = ScamRed
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Načítám audio...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(
                        onClick = {
                            val mp = mediaPlayer ?: return@IconButton
                            if (isPlaying) {
                                mp.pause()
                                isPlaying = false
                            } else {
                                mp.start()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = ScamRed,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            val mp = mediaPlayer ?: return@IconButton
                            mp.pause()
                            mp.seekTo(0)
                            isPlaying = false
                            currentPosition = 0L
                            sliderPosition = 0f
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Slider(
                        value = sliderPosition,
                        onValueChange = { value ->
                            isUserSeeking = true
                            sliderPosition = value
                        },
                        onValueChangeFinished = {
                            val mp = mediaPlayer
                            if (mp != null) {
                                val seekPosition = (sliderPosition * duration).toInt()
                                mp.seekTo(seekPosition)
                                currentPosition = seekPosition.toLong()
                            }
                            isUserSeeking = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = ScamRed,
                            activeTrackColor = ScamRed,
                            inactiveTrackColor = ScamOrange.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
