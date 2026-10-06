package com.lingion.sleepy

import android.app.Application
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.WorkManager

/** Initializes WorkManager before SleepyApp.onCreate() in instrumented render tests. */
class SleepyRenderTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(application: Application) {
        if (application is SleepyApp) {
            try {
                WorkManager.getInstance(application)
            } catch (_: IllegalStateException) {
                WorkManager.initialize(application, Configuration.Builder().build())
            }
        }
        super.callApplicationOnCreate(application)
    }
}
