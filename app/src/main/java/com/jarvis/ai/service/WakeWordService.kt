package com.jarvis.ai.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.jarvis.ai.core.wakeword.PorcupineWakeWordEngine
import com.jarvis.ai.di.AppContainer
import com.jarvis.ai.di.appContainer
import com.jarvis.ai.scheduling.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service hosting continuous "Jarvis" wake-word detection.
 *
 * Android 14+ requires FOREGROUND_SERVICE_MICROPHONE plus a visible
 * notification for microphone use outside the foreground activity — both
 * declared. On detection the word is published to [AppContainer.wakeWordEvents];
 * the HUD decides whether to auto-listen (app in foreground) or stay quiet.
 */
class WakeWordService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: PorcupineWakeWordEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Configuration is read off the main thread; setup continues async.
        serviceScope.launch {
            val container = appContainer()
            val settings = container.settings.snapshot()

            if (!settings.wakeWordEnabled || settings.picovoiceKey.isBlank()) {
                // Nothing to do; user hasn't configured the wake word yet.
                stopSelf()
                return@launch
            }

            startAsForeground()

            try {
                val wakeEngine = PorcupineWakeWordEngine(
                    context = this@WakeWordService,
                    accessKey = settings.picovoiceKey,
                    sensitivity = 0.7f
                )
                engine = wakeEngine
                launch {
                    wakeEngine.detections.collect {
                        container.wakeWordEvents.tryEmit(Unit)
                    }
                }
                wakeEngine.start()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Wake word service failed to start: ${e.message}")
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        engine?.stop()
        engine = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = NotificationHelper.showServiceNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
        }
    }

    companion object {
        private const val TAG = "WakeWordService"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WakeWordService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
}
