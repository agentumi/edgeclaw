package com.edgeclaw.mobile

import android.app.Application

/**
 * EdgeClaw Application class.
 * Initializes core services on application start.
 */
class EdgeClawApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Future: initialize Rust core via JNI/UniFFI bindings
        // Future: initialize crash reporting
        // Future: initialize analytics
    }
}
