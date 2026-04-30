package com.synfusion.vault.vault

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.ImageLoader
import com.synfusion.vault.data.VaultEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenMedia: (VaultEntity) -> Unit
) {
    val context = LocalContext.current
    val items by viewModel.vaultItems.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val selectedMediaType by viewModel.selectedMediaType.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val error by viewModel.error.collectAsState()


    // FLAG_SECURE
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Android 11+ Delete original files launcher
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Deleted successfully
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFiles(uris, selectedMediaType) { returnedUris ->
                val mediaStoreUris = returnedUris.filter { it.toString().startsWith("content://media/") }
                val safUris = returnedUris.filterNot { it.toString().startsWith("content://media/") }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mediaStoreUris.isNotEmpty()) {
                    try {
                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUris)
                        deleteRequestLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if (mediaStoreUris.isNotEmpty()) {
                    mediaStoreUris.forEach { uri ->
                        try {
                            context.contentResolver.delete(uri, null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                safUris.forEach { uri ->
                    try {
                        if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
                            android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
                        } else {
                            context.contentResolver.delete(uri, null, null)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    val audioImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFiles(uris, selectedMediaType) { returnedUris ->
                val mediaStoreUris = returnedUris.filter { it.toString().startsWith("content://media/") }
                val safUris = returnedUris.filterNot { it.toString().startsWith("content://media/") }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mediaStoreUris.isNotEmpty()) {
                    try {
                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUris)
                        deleteRequestLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if (mediaStoreUris.isNotEmpty()) {
                    mediaStoreUris.forEach { uri ->
                        try {
                            context.contentResolver.delete(uri, null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                safUris.forEach { uri ->
                    try {
                        if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
                            android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
                        } else {
                            context.contentResolver.delete(uri, null, null)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            if (selectedMediaType == "images" || selectedMediaType == "videos") {
                val visualMediaType = if (selectedMediaType == "images") {
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                } else {
                    ActivityResultContracts.PickVisualMedia.VideoOnly
                }
                importLauncher.launch(PickVisualMediaRequest(visualMediaType))
            } else {
                audioImportLauncher.launch(arrayOf("audio/*"))
            }
        } else {
            permissionDeniedMessage = "Permission denied. Vault cannot import media without storage access."
        }
    }

    fun requestPermissionsAndLaunch() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (selectedMediaType) {
                "images" -> permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
                "videos" -> permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
                "audio" -> permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val notGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            if (selectedMediaType == "images" || selectedMediaType == "videos") {
                val visualMediaType = if (selectedMediaType == "images") {
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                } else {
                    ActivityResultContracts.PickVisualMedia.VideoOnly
                }
                importLauncher.launch(PickVisualMediaRequest(visualMediaType))
            } else {
                audioImportLauncher.launch(arrayOf("audio/*"))
            }
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    Scaffold(
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { requestPermissionsAndLaunch() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 80.dp)
            ) {
                Icon(Icons.Default.Add, "Import", modifier = Modifier.size(36.dp))
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .height(68.dp),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shadowElevation = 14.dp
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    NavigationBarItem(
                        selected = selectedMediaType == "images",
                        onClick = { viewModel.setMediaType("images") },
                        icon = { Icon(Icons.Default.Image, "Images") },
                        label = { Text("Images") },
                        modifier = Modifier.weight(1f)
                    )
                    NavigationBarItem(
                        selected = selectedMediaType == "videos",
                        onClick = { viewModel.setMediaType("videos") },
                        icon = { Icon(Icons.Default.VideoLibrary, "Videos") },
                        label = { Text("Videos") },
                        modifier = Modifier.weight(1f)
                    )
                    NavigationBarItem(
                        selected = selectedMediaType == "audio",
                        onClick = { viewModel.setMediaType("audio") },
                        icon = { Icon(Icons.Default.AudioFile, "Audio") },
                        label = { Text("Audio") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            Column {
                if (selectedItems.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                            Text(
                                "${selectedItems.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                viewModel.exportSelectedItems { _ -> }
                            }, enabled = !isProcessing) {
                                Icon(Icons.Default.Upload, "Export")
                            }
                            IconButton(onClick = { viewModel.deleteSelectedItems() }, enabled = !isProcessing) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }

                        Surface(
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = { Text("Search vault...") },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Vault is empty")
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (selectedMediaType == "audio") 1 else 3),
                            contentPadding = PaddingValues(bottom = 120.dp, start = 8.dp, end = 8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(items, key = { it.id }) { item ->
                                VaultItem(
                                    item = item,
                                    isSelected = selectedItems.contains(item),
                                    onSelect = { viewModel.toggleSelection(item) },
                                    onClick = {
                                        if (selectedItems.isNotEmpty()) {
                                            viewModel.toggleSelection(item)
                                        } else {
                                            onOpenMedia(item)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PremiumCurvyLoader()
                        if (importProgress != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = importProgress!!,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (permissionDeniedMessage != null) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = { permissionDeniedMessage = null }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(permissionDeniedMessage!!)
                }
            }

            if (error != null) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(error!!)
                }
            }
        }
    }
}

@Composable
fun PremiumCurvyLoader() {
    val infiniteTransition = rememberInfiniteTransition(label = "CurvyLoader")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing)
        ),
        label = "Angle"
    )

    val color1 = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    val color2 = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.size(64.dp)) {
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Transparent,
                    color1,
                    color2
                )
            ),
            startAngle = angle,
            sweepAngle = 280f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 8.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultItem(
    item: VaultEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .padding(8.dp)
            .aspectRatio(if (item.mediaType == "audio") 4f else 1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onSelect
            ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.mediaType == "images" || item.mediaType == "videos") {
                AsyncImage(
                    model = item,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (item.mediaType == "videos") {
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(32.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(item.originalName, maxLines = 1)
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }
    }
}
