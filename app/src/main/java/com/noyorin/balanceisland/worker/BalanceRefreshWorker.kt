package com.noyorin.balanceisland.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noyorin.balanceisland.data.BalanceRepository

class BalanceRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        BalanceRepository(applicationContext).refreshAll()
        return Result.success()
    }
}
