package com.kotha.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.kotha.app.data.app.AppForeground
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CovaApplication : Application() {

    @Inject
    lateinit var appForeground: AppForeground

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appForeground.set(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                appForeground.set(false)
            }
        })
    }
}
