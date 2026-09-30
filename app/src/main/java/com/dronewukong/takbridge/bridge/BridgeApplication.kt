package com.dronewukong.takbridge.bridge

import android.app.Application
import android.content.Context

/** Application scope prevents UI recreation from creating a second USB/network session. */
open class BridgeApplication : Application() {
    val session by lazy { createSession() }
    protected open fun createSession() = BridgeSession(this)
    companion object {
        fun session(context: Context) = (context.applicationContext as BridgeApplication).session
    }
}
