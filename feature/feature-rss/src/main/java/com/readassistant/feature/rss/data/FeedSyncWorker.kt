package com.readassistant.feature.rss.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class FeedSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: RssRepositoryImpl
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = try { repository.refreshAllFeeds(); Result.success() } catch (_: Exception) { Result.retry() }
    companion object {
        const val WORK_NAME = "feed_sync_worker"
        fun buildPeriodicRequest(intervalHours: Long = 2) = PeriodicWorkRequestBuilder<FeedSyncWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
    }
}
