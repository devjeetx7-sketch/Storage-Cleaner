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
            TopAppBar(title = { Text(if (isFirstTime) "Setup Vault" else "Unlock Vault") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))

            when (step) {
                AuthStep.SET_PIN -> {
                    Text("Create a 4-digit PIN", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4) pin = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (pin.length == 4) {
                                step = AuthStep.CONFIRM_PIN
                                error = ""
                            } else {
                                error = "PIN must be 4 digits"
                            }
                        }
                    ) {
                        Text("Next")
                    }
                }
                AuthStep.CONFIRM_PIN -> {
                    Text("Confirm PIN", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 4) confirmPin = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (pin == confirmPin) {
                                authManager.setPin(pin)
                                step = AuthStep.BIOMETRIC_OPT_IN
                            } else {
                                error = "PINs do not match"
                                confirmPin = ""
                            }
                        }
                    ) {
                        Text("Confirm")
                    }
                }
                AuthStep.BIOMETRIC_OPT_IN -> {
                    Text("Enable Biometric Unlock?", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(32.dp))
                    Row {
                        OutlinedButton(
                            onClick = {
                                authManager.setBiometricEnabled(false)
                                onAuthSuccess()
                            }
                        ) {
                            Text("Skip")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                authManager.setBiometricEnabled(true)
                                onAuthSuccess()
                            }
                        ) {
                            Text("Enable")
                        }
                    }
                }
                AuthStep.UNLOCK -> {
                    Text("Enter PIN", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
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
                        singleLine = true
                    )
                }
            }

            if (error.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

enum class AuthStep {
    SET_PIN, CONFIRM_PIN, BIOMETRIC_OPT_IN, UNLOCK
}
