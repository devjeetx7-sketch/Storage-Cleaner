package com.synfusion.vault.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.synfusion.vault.security.AuthManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    authManager: AuthManager,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val isFirstTime = remember { authManager.isFirstTimeSetup() }

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    var step by remember { mutableStateOf(if (isFirstTime) AuthStep.SET_PIN else AuthStep.UNLOCK) }

    LaunchedEffect(Unit) {
        if (!isFirstTime && authManager.isBiometricEnabled()) {
            (context as? FragmentActivity)?.let { activity ->
                authManager.showBiometricPrompt(
                    activity = activity,
                    onSuccess = onAuthSuccess,
                    onError = { /* fallback to PIN */ }
                )
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))

            when (step) {
                AuthStep.SET_PIN -> {
                    Text("Create a 4-digit PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    PinDots(pin = pin)
                    Spacer(modifier = Modifier.height(32.dp))
                    PinKeypad(
                        onNumberClick = { if (pin.length < 4) pin += it },
                        onBackspaceClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                        onNextClick = {
                            if (pin.length == 4) {
                                step = AuthStep.CONFIRM_PIN
                                error = ""
                            } else {
                                error = "PIN must be 4 digits"
                            }
                        },
                        showNext = pin.length == 4
                    )
                }
                AuthStep.CONFIRM_PIN -> {
                    Text("Confirm PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    PinDots(pin = confirmPin)
                    Spacer(modifier = Modifier.height(32.dp))
                    PinKeypad(
                        onNumberClick = { if (confirmPin.length < 4) confirmPin += it },
                        onBackspaceClick = { if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1) },
                        onNextClick = {
                            if (pin == confirmPin) {
                                authManager.setPin(pin)
                                step = AuthStep.BIOMETRIC_OPT_IN
                            } else {
                                error = "PINs do not match"
                                confirmPin = ""
                            }
                        },
                        showNext = confirmPin.length == 4
                    )
                }
                AuthStep.BIOMETRIC_OPT_IN -> {
                    Text("Enable Biometric Unlock?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = {
                                authManager.setBiometricEnabled(false)
                                onAuthSuccess()
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = CircleShape
                        ) {
                            Text("Skip")
                        }
                        Button(
                            onClick = {
                                authManager.setBiometricEnabled(true)
                                onAuthSuccess()
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = CircleShape
                        ) {
                            Text("Enable")
                        }
                    }
                }
                AuthStep.UNLOCK -> {
                    Text("Enter PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    PinDots(pin = pin)
                    Spacer(modifier = Modifier.height(32.dp))
                    PinKeypad(
                        onNumberClick = {
                            if (pin.length < 4) {
                                pin += it
                                if (pin.length == 4) {
                                    if (authManager.verifyPin(pin)) {
                                        onAuthSuccess()
                                    } else {
                                        error = "Incorrect PIN"
                                        pin = ""
                                    }
                                }
                            }
                        },
                        onBackspaceClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                        onNextClick = { },
                        showNext = false
                    )
                }
            }

            if (error.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun PinDots(pin: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..4) {
            val isFilled = i <= pin.length
            val scale by animateFloatAsState(targetValue = if (isFilled) 1.2f else 1f, label = "DotScale")
            val color by animateColorAsState(
                targetValue = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "DotColor"
            )

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isFilled) Modifier.shadow(4.dp, CircleShape) else Modifier
                    )
            )
        }
    }
}

@Composable
fun PinKeypad(
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onNextClick: () -> Unit,
    showNext: Boolean
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("backspace", "0", "next")
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (row in keys) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (key in row) {
                    when (key) {
                        "backspace" -> {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .clickable { onBackspaceClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Backspace",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                        "next" -> {
                            Box(
                                modifier = Modifier.size(72.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (showNext) {
                                    FloatingActionButton(
                                        onClick = onNextClick,
                                        shape = CircleShape,
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Next"
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { onNumberClick(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class AuthStep {
    SET_PIN, CONFIRM_PIN, BIOMETRIC_OPT_IN, UNLOCK
}
