package com.synfusion.vault.ui.cleaner

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppBlocking
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CleanerDashboard(
    viewModel: CleanerViewModel = hiltViewModel(),
    onVaultTrigger: () -> Unit
) {
    val stats by viewModel.storageStats.collectAsState()
    val isCleaning by viewModel.isCleaning.collectAsState()
    val freedSpace by viewModel.freedSpace.collectAsState()

    var vaultTapCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(vaultTapCount) {
        if (vaultTapCount > 0) {
            delay(1500)
            vaultTapCount = 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Storage Cleaner",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { onVaultTrigger() }
                                )
                            }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            Button(
                onClick = { viewModel.startCleaning() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(64.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 12.dp, pressedElevation = 4.dp),
                enabled = !isCleaning
            ) {
                Icon(
                    Icons.Default.CleaningServices,
                    contentDescription = "Clean",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isCleaning) "Cleaning in progress..." else "Clean Junk",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val cardItems = listOf(
                Triple("Junk Files", formatSize(stats.junkSize), Icons.Default.Warning),
                Triple("Cache", formatSize(stats.cacheSize), Icons.Default.Memory),
                Triple("Large Files", "${stats.largeFilesCount} files", Icons.Default.FolderSpecial),
                Triple("Duplicate Media", "${stats.duplicateImagesCount} files", Icons.Default.ContentCopy),
                Triple("App Residuals", "Scanning...", Icons.Default.AppBlocking)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 110.dp // ensure fully scrolls past floating CTA
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header span for Storage Meter
                item(span = { GridItemSpan(2) }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Invisible corner trigger
                        Box(
                            modifier = Modifier
                                .align(Alignment.End)
                                .size(48.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(onLongPress = { onVaultTrigger() })
                                }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Storage Meter
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(200.dp)
                                .combinedClickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        vaultTapCount++
                                        if (vaultTapCount >= 5) {
                                            vaultTapCount = 0
                                            onVaultTrigger()
                                        }
                                    },
                                    onLongClick = {
                                        onVaultTrigger()
                                    }
                                )
                        ) {
                            val progress = if (stats.totalSpace > 0) {
                                stats.usedSpace.toFloat() / stats.totalSpace.toFloat()
                            } else 0f

                            val animatedProgress by animateFloatAsState(
                                targetValue = if (isCleaning) 0f else progress,
                                animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                                label = "StorageProgress"
                            )

                            val trackColor = MaterialTheme.colorScheme.surfaceVariant
                            val startColor = MaterialTheme.colorScheme.primary
                            val endColor = MaterialTheme.colorScheme.tertiary

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = trackColor,
                                    startAngle = 135f,
                                    sweepAngle = 270f,
                                    useCenter = false,
                                    style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
                                )

                                drawArc(
                                    brush = Brush.linearGradient(
                                        colors = listOf(startColor, endColor)
                                    ),
                                    startAngle = 135f,
                                    sweepAngle = 270f * animatedProgress,
                                    useCenter = false,
                                    style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val percentage = (animatedProgress * 100).toInt()
                                Text(
                                    text = if (isCleaning) "Cleaning..." else "$percentage%",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${formatSize(stats.usedSpace)} / ${formatSize(stats.totalSpace)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        if (freedSpace > 0 && !isCleaning) {
                            Text(
                                text = "Freed up ${formatSize(freedSpace)}!",
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                    }
                }

                // Info Cards items
                items(cardItems) { (title, value, icon) ->
                    InfoCard(
                        title = title,
                        value = value,
                        icon = icon
                    )
                }
            }
        }
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
