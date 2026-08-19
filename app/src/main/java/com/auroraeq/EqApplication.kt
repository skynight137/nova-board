package com.auroraeq.app

import android.app.Application
import android.content.Intent
import com.auroraeq.app.data.repository.EqRepository
import com.auroraeq.app.data.store.ChainStore
import com.auroraeq.app.data.store.PresetStore
import com.auroraeq.app.service.GlobalEqService
import com.auroraeq.app.util.AppLog

/**
 * Simple hand-rolled DI container instead of Hilt/Dagger.
 *
 * Why: Hilt requires KSP/kapt wiring that is fragile to bootstrap in one pass in this environment,
 * and this app is small enough that a single process-wide singleton container gives the same
 * practical benefit (one EqRepository instance shared between the UI and the GlobalEqService)
 * without the extra build-tooling risk. If the app grows, swap this for Hilt without changing call
 * sites much: repository/viewmodel constructors already take their dependencies as constructor
 * params.
 */
class EqApplication : Application() {

    lateinit var chainStore: ChainStore
        private set

    lateinit var presetStore: PresetStore
        private set

    lateinit var eqRepository: EqRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // First, so every other line in this method (and everything else in the
        // process) can log — ChainStore/PresetStore below load persisted state
        // synchronously and can hit their own corrupt-data warnings immediately.
        AppLog.init(this)
        installCrashHandler()

        chainStore = ChainStore(this)
        presetStore = PresetStore(this)
        eqRepository = EqRepository(chainStore, presetStore)

        // Global processing is no longer a toggle — it's the only mode. Start the
        // foreground service unconditionally so the effect chain attaches to
        // session 0 as soon as the app process is created.
        startForegroundService(Intent(this, GlobalEqService::class.java))
    }

    /**
     * Chains onto (not replaces) the platform's default handler, so a crash still terminates the
     * process the normal way — this only adds a write to the local log file first, capturing the
     * stack trace even though the process is about to die.
     */
    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.crash(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

fun Application.asEqApplication(): EqApplication = this as EqApplication
