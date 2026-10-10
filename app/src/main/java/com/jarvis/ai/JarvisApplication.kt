package com.jarvis.ai

import android.app.Application
import com.jarvis.ai.di.appContainer
import com.jarvis.ai.di.initAppContainer
import com.jarvis.ai.scheduling.NotificationHelper

class JarvisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initAppContainer(this)
        NotificationHelper.ensureChannels(this)
        appContainer().applyVoiceSettings()
        appContainer().loadModelIfConfigured()
    }
}
