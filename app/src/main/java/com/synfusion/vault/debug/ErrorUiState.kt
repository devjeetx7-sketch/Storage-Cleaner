package com.synfusion.vault.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorNotifier(errorLogger: ErrorLogger) {
    val error by errorLogger.latestError.collectAsState(initial = null)
    var showError by remember { mutableStateOf(false) }
    var currentError by remember { mutableStateOf<com.synfusion.vault.data.ErrorEntity?>(null) }

    LaunchedEffect(error) {
        if (error != null) {
            currentError = error
            showError = true
        }
    }

    if (showError && currentError != null) {
        val context = LocalContext.current
        ModalBottomSheet(
            onDismissRequest = { showError = false },
            containerColor = Color(0xFFB71C1C),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "An Error Occurred",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showError = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Code: ${currentError?.errorCode}", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(currentError?.errorMessage ?: "Unknown error")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Type: ${currentError?.mediaType} | Op: ${currentError?.operation}")

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Error Log",
                                "Code: ${currentError?.errorCode}\nMsg: ${currentError?.errorMessage}\nTrace: ${currentError?.stackTrace}")
                            clipboard.setPrimaryClip(clip)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Info")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Vault App Error: ${currentError?.errorCode}\n${currentError?.errorMessage}\n${currentError?.stackTrace}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Report Issue"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB71C1C))
                    ) {
                        Icon(Icons.Default.Report, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Report Issue")
                    }
                }
            }
        }
    }
}
