package com.thezone.probe

import android.app.Application
import com.thezone.diagnostics.CrashLog

/** Process entry point — installs the on-device crash recorder as early as possible. */
class ZoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
