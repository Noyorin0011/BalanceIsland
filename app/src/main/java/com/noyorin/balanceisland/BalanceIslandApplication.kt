package com.noyorin.balanceisland

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.noyorin.balanceisland.worker.BalanceRefreshWorker
import com.noyorin.balanceisland.localization.AppLanguagePreferences
import com.noyorin.balanceisland.service.ServiceRuntimePreferences
import java.util.concurrent.TimeUnit

class BalanceIslandApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguagePreferences.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // A process killed by Android cannot run Service.onDestroy(), so discard any
        // service flag left by the previous process before components start again.
        ServiceRuntimePreferences(this).setServiceRunning(false)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<BalanceRefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "periodic_balance_refresh"
    }
}
