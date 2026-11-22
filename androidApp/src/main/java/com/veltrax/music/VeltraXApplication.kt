package com.veltrax.music

import android.app.Application
import androidx.work.Configuration
import java.util.concurrent.Executors

class VeltraXApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setExecutor(Executors.newFixedThreadPool(2))
            .setMaxSchedulerLimit(2)
            .build()
    }
}
