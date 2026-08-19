package com.novaboard.ime

import android.app.Application
import com.novaboard.ime.util.AppLog

class NovaBoardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
    }
}
