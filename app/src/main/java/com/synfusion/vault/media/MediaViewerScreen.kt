package com.synfusion.vault.media

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.synfusion.vault.data.VaultEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    items: List<VaultEntity>,
    initialIndex: Int,
    encryptionManager: com.synfusion.vault.security.EncryptionManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { items.size })
    val currentEntity = items[pagerState.currentPage]

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
                title = { Text(currentEntity.originalName, maxLines = 1, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            pageSpacing = 16.dp
        ) { page ->
            val entity = items[page]
            MediaViewerItem(
                entity = entity,
                isActive = page == pagerState.currentPage,
                encryptionManager = encryptionManager
            )
        }
    }
}

@Composable
fun MediaViewerItem(
    entity: VaultEntity,
    isActive: Boolean,
    encryptionManager: com.synfusion.vault.security.EncryptionManager
) {
    val context = LocalContext.current
    var hasError by remember { mutableStateOf(false) }
    var tempFilePath by remember { mutableStateOf("") }
    var isDecrypting by remember { mutableStateOf(true) }
    var decryptionProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(entity) {
        isDecrypting = true
        decryptionProgress = 0f
        tempFilePath = ""
        hasError = false

        withContext(Dispatchers.IO) {
            val encryptedFile = File(entity.encryptedPath)
            if (!encryptedFile.exists()) {
                hasError = true
                isDecrypting = false
                return@withContext
            }

            val tempFile = File(context.cacheDir, "view_${UUID.randomUUID()}.tmp")
            try {
                encryptedFile.inputStream().use { input ->
                    BufferedInputStream(input).use { bufferedInput ->
                        FileOutputStream(tempFile).use { output ->
                            BufferedOutputStream(output).use { bufferedOutput ->
                                encryptionManager.decryptWithProgress(bufferedInput, bufferedOutput, entity.size) { progress ->
                                    decryptionProgress = progress
                                }
                            }
                        }
                    }
                }
                tempFilePath = tempFile.absolutePath
            } catch (e: Exception) {
                tempFile.delete()
                hasError = true
            } finally {
                isDecrypting = false
            }
        }
    }

    DisposableEffect(tempFilePath) {
        onDispose {
            if (tempFilePath.isNotEmpty()) {
                File(tempFilePath).delete()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (hasError) {
            Text("Failed to load media.", color = Color.Red)
        } else {
            // Instant preview with thumbnail using Crossfade for smooth transition
            Crossfade(targetState = tempFilePath.isNotEmpty(), animationSpec = tween(500), label = "MediaTransition") { loaded ->
                if (loaded) {
                    // Full Resolution Media
                    when (entity.mediaType) {
                        "images" -> {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(File(tempFilePath))
                                    .size(coil.size.Size.ORIGINAL)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        "videos", "audio" -> {
                            VideoPlayer(path = tempFilePath, isActive = isActive)
                        }
                    }
                } else {
                    // Thumbnail Placeholder (Instant)
                    if (entity.thumbnailPath != null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = File(entity.thumbnailPath),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                alpha = 0.5f
                            )
                            if (isDecrypting) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("${(decryptionProgress * 100).toInt()}%", color = Color.White)
                                }
                            }
                        }
                    } else if (isDecrypting) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${(decryptionProgress * 100).toInt()}%", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(path: String, isActive: Boolean) {
    val context = LocalContext.current
    val exoPlayer = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(File(path)))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = isActive
        }
    }

    LaunchedEffect(isActive) {
        exoPlayer.playWhenReady = isActive
        if (!isActive) exoPlayer.pause()
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
