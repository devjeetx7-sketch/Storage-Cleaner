package com.synfusion.vault.media

import android.net.Uri
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.synfusion.vault.data.VaultEntity
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
    onBack: () -> Unit
) {
    val context = LocalContext.current

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
            when (entity.mediaType) {
                "images" -> {
                    AsyncImage(
                        model = entity,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                "videos", "audio" -> {
                    var tempFilePath by remember { mutableStateOf("") }

                    LaunchedEffect(entity) {
                        var tempFile: File? = null
                        try {
                            withContext(Dispatchers.IO) {
                                val encryptedFile = File(entity.encryptedPath)
                                tempFile = File(context.cacheDir, "${UUID.randomUUID()}.tmp")
                                encryptedFile.inputStream().use { input ->
                                    FileOutputStream(tempFile).use { output ->
                                        encryptionManager.decrypt(input, output)
                                    }
                                }
                                tempFilePath = tempFile?.absolutePath ?: ""
                            }
                        } catch (e: Exception) {
                            tempFile?.delete()
                        }
                    }

                    if (tempFilePath.isNotEmpty()) {
                        val exoPlayer = remember {
                            ExoPlayer.Builder(context).build().apply {
                                val mediaItem = MediaItem.fromUri(Uri.fromFile(File(tempFilePath)))
                                setMediaItem(mediaItem)
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
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }
}
