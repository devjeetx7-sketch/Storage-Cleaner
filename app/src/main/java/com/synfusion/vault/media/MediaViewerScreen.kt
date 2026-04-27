package com.synfusion.vault.media

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.synfusion.vault.data.VaultEntity
import com.synfusion.vault.debug.ErrorLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    entity: VaultEntity,
    encryptionManager: com.synfusion.vault.security.EncryptionManager,
    errorLogger: ErrorLogger,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasError by remember { mutableStateOf(false) }

    // FLAG_SECURE for Viewer
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entity.originalName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (hasError) {
                Text(
                    text = "Failed to load media. Check logs.",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                when (entity.mediaType) {
                    "images" -> {
                        AsyncImage(
                            model = entity,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            onState = { state ->
                                if (state is coil.compose.AsyncImagePainter.State.Error) {
                                    hasError = true
                                }
                            }
                        )
                    }
                    "videos", "audio" -> {
                        var tempFilePath by remember { mutableStateOf("") }

                        LaunchedEffect(entity) {
                            var tempFile: File? = null
                            try {
                                withContext(Dispatchers.IO) {
                                    val encryptedFile = File(entity.encryptedPath)
                                    if (!encryptedFile.exists()) {
                                        errorLogger.logError(ErrorLogger.Codes.FILE_MISSING, "File not found", null, entity.mediaType, "view")
                                        hasError = true
                                        return@withContext
                                    }
                                    tempFile = File(context.cacheDir, "${UUID.randomUUID()}.tmp")
                                    encryptedFile.inputStream().use { input ->
                                        FileOutputStream(tempFile!!).use { output ->
                                            encryptionManager.decrypt(input, output)
                                        }
                                    }
                                    tempFilePath = tempFile?.absolutePath ?: ""
                                }
                            } catch (e: Exception) {
                                errorLogger.logError(ErrorLogger.Codes.DECRYPT, "Decryption failed", e, entity.mediaType, "view")
                                tempFile?.delete()
                                hasError = true
                            }
                        }

                        if (tempFilePath.isNotEmpty()) {
                            val exoPlayer = remember {
                                ExoPlayer.Builder(context).build().apply {
                                    val mediaItem = MediaItem.fromUri(Uri.fromFile(File(tempFilePath)))
                                    setMediaItem(mediaItem)
                                    addListener(object : Player.Listener {
                                        override fun onPlayerError(error: PlaybackException) {
                                            errorLogger.logError(
                                                if (entity.mediaType == "videos") ErrorLogger.Codes.PLAY_VID else ErrorLogger.Codes.PLAY_AUD,
                                                "ExoPlayer playback error",
                                                error,
                                                entity.mediaType,
                                                "play"
                                            )
                                            hasError = true
                                        }
                                    })
                                    prepare()
                                    playWhenReady = true
                                }
                            }

                            DisposableEffect(exoPlayer) {
                                onDispose {
                                    exoPlayer.release()
                                    File(tempFilePath).delete()
                                }
                            }

                            AndroidView(
                                factory = {
                                    PlayerView(context).apply {
                                        player = exoPlayer
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (!hasError) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
