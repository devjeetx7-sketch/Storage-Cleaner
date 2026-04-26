package com.synfusion.vault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.synfusion.vault.security.AuthManager
import com.synfusion.vault.security.VaultState
import com.synfusion.vault.ui.cleaner.CleanerDashboard
import com.synfusion.vault.ui.settings.AuthScreen
import com.synfusion.vault.vault.VaultScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val vaultState by authManager.vaultState.collectAsState()

                    NavHost(navController = navController, startDestination = "cleaner") {
                        composable("cleaner") {
                            CleanerDashboard(
                                onVaultTrigger = {
                                    navController.navigate("auth")
                                }
                            )
                        }
                        composable("auth") {
                            AuthScreen(
                                authManager = authManager,
                                onAuthSuccess = {
                                    navController.navigate("vault") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("vault") {
                            if (vaultState == VaultState.UNLOCKED) {
                                VaultScreen(
                                    onBack = {
                                        authManager.lockVault()
                                        navController.navigate("cleaner") {
                                            popUpTo(0)
                                        }
                                    }
                                )
                            } else {
                                navController.navigate("auth") {
                                    popUpTo("vault") { inclusive = true }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Auto-lock vault on background
        authManager.lockVault()
    }
}
