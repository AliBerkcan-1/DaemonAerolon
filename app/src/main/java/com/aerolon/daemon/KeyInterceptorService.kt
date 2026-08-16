package com.aerolon.daemon

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent


class KeyInterceptorService : AccessibilityService() {

    private var lastVolumeDownTime = 0L

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastVolumeDownTime < 400) {
                lastVolumeDownTime = 0L

                val intent = Intent(this, DaemonService::class.java).apply {
                    action = "ACTION_TRIGGER_ASSISTANT"
                }

                startForegroundService(intent)
                return true
            } else {
                lastVolumeDownTime = now
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}