package com.synfusion.vault.ui.cleaner

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerDashboard(
    viewModel: CleanerViewModel = hiltViewModel(),
    onVaultTrigger: () -> Unit,
    onDebugTrigger: () -> Unit
) {
    val stats by viewModel.storageStats.collectAsState()
    val isCleaning by viewModel.isCleaning.collectAsState()
    val freedSpace by viewModel.freedSpace.collectAsState()

    var vaultTapCount by remember { mutableIntStateOf(0) }
    var debugTapCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(vaultTapCount) {
        if (vaultTapCount > 0) {
            delay(1500)
            vaultTapCount = 0
        }
    }

    LaunchedEffect(debugTapCount) {
        if (debugTapCount > 0) {
            delay(2000)
            debugTapCount = 0
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
                                    onLongPress = { onVaultTrigger() },
                                    onTap = {
                                        debugTapCount++
                                        if (debugTapCount >= 7) {
                                            debugTapCount = 0
                                            onDebugTrigger()
                                        }
                                    }
                                )
                            }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
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
                        .clickable {
                            vaultTapCount++
                            if (vaultTapCount >= 5) {
                                vaultTapCount = 0
                                onVaultTrigger()
                            }
                        }
                ) {
                    val progress = if (stats.totalSpace > 0) {
                        stats.usedSpace.toFloat() / stats.totalSpace.toFloat()
                    } else 0f

                    val animatedProgress by animateFloatAsState(
                        targetValue = if (isCleaning) 0f else progress,
                        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                        label = "StorageProgress"
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = Color.LightGray.copy(alpha = 0.3f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
                        )

                        drawArc(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF64B5F6), Color(0xFF1976D2))
                            ),
                            startAngle = 135f,
                            sweepAngle = 270f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isCleaning) "Cleaning..." else formatSize(stats.usedSpace),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "of ${formatSize(stats.totalSpace)} used",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Info Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        title = "Junk Files",
                        value = formatSize(stats.junkSize),
                        icon = Icons.Default.Warning,
                        color = Color(0xFFE57373)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        title = "Cache",
                        value = formatSize(stats.cacheSize),
                        icon = Icons.Default.Memory,
                        color = Color(0xFF81C784)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (freedSpace > 0 && !isCleaning) {
                    Text(
                        text = "Freed up ${formatSize(freedSpace)}!",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Button(
                    onClick = { viewModel.startCleaning() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                    enabled = !isCleaning
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = "Clean")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isCleaning) "Cleaning in progress..." else "Clean Junk")
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
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
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
