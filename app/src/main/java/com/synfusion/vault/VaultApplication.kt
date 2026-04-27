package com.synfusion.vault

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.synfusion.vault.security.AuthManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VaultApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // Auto-lock vault on app background
                authManager.lockVault()
            }
        })
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
