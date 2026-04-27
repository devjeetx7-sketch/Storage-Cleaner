package com.synfusion.vault.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isFirstTime) "Setup Vault" else "Unlock Vault") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
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
            Spacer(modifier = Modifier.height(48.dp))

            when (step) {
                AuthStep.SET_PIN -> {
                    Text("Create a 4-digit PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4) pin = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            if (pin.length == 4) {
                                step = AuthStep.CONFIRM_PIN
                                error = ""
                            } else {
                                error = "PIN must be 4 digits"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ) {
                        Text("Next", style = MaterialTheme.typography.titleMedium)
                    }
                }
                AuthStep.CONFIRM_PIN -> {
                    Text("Confirm PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 4) confirmPin = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            if (pin == confirmPin) {
                                authManager.setPin(pin)
                                step = AuthStep.BIOMETRIC_OPT_IN
                            } else {
                                error = "PINs do not match"
                                confirmPin = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ) {
                        Text("Confirm", style = MaterialTheme.typography.titleMedium)
                    }
                }
                AuthStep.BIOMETRIC_OPT_IN -> {
                    Text("Enable Biometric Unlock?", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = {
                                authManager.setBiometricEnabled(false)
                                onAuthSuccess()
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            Text("Skip")
                        }
                        Button(
                            onClick = {
                                authManager.setBiometricEnabled(true)
                                onAuthSuccess()
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            Text("Enable")
                        }
                    }
                }
                AuthStep.UNLOCK -> {
                    Text("Enter PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 4) pin = it
                            if (pin.length == 4) {
                                if (authManager.verifyPin(pin)) {
                                    onAuthSuccess()
                                } else {
                                    error = "Incorrect PIN"
                                    pin = ""
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
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

enum class AuthStep {
    SET_PIN, CONFIRM_PIN, BIOMETRIC_OPT_IN, UNLOCK
}
