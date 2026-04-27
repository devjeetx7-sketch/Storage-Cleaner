package com.synfusion.vault

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.synfusion.vault.security.AuthManager
import com.synfusion.vault.security.VaultState
import com.synfusion.vault.ui.cleaner.CleanerDashboard
import com.synfusion.vault.ui.settings.AuthScreen
import com.synfusion.vault.vault.VaultScreen
import com.synfusion.vault.media.MediaViewerScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.synfusion.vault.security.EncryptionManager

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var encryptionManager: EncryptionManager

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

                    Box(modifier = Modifier.fillMaxSize()) {
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
                                // Launch effect instead of navigating in composition
                                LaunchedEffect(vaultState) {
                                    if (vaultState == VaultState.LOCKED) {
                                        navController.navigate("auth") {
                                            popUpTo("vault") { inclusive = true }
                                        }
                                    }
                                }

                                if (vaultState == VaultState.UNLOCKED) {
                                    VaultScreen(
                                        onBack = {
                                            authManager.lockVault()
                                            navController.navigate("cleaner") {
                                                popUpTo(0)
                                            }
                                        },
                                        onOpenMedia = { item ->
                                            navController.navigate("media_viewer/${item.id}")
                                        }
                                    )
                                }
                            }
                            composable("media_viewer/{itemId}") { backStackEntry ->
                                val itemId = backStackEntry.arguments?.getString("itemId")

                                val parentEntry = remember(backStackEntry) {
                                    navController.getBackStackEntry("vault")
                                }
                                val vaultViewModel: com.synfusion.vault.vault.VaultViewModel = hiltViewModel(parentEntry)
                                val items by vaultViewModel.vaultItems.collectAsState()

                                val item = items.find { it.id == itemId }

                                if (item != null) {
                                    MediaViewerScreen(
                                        entity = item,
                                        encryptionManager = encryptionManager,
                                        onBack = {
                                            navController.popBackStack()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
