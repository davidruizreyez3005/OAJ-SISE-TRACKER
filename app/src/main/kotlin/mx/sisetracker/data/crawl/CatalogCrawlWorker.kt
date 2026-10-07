package mx.sisetracker.data.crawl

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import mx.sisetracker.SiseApp
import mx.sisetracker.data.settings.CrawlStatus

/**
 * Runs the [CatalogCrawl] in chunks of [RUN_BUDGET_MILLIS] (WorkManager stops
 * a worker after 10 minutes), each one queueing the next, with any network
 * connection (Wi-Fi or mobile data) and whatever the battery level. Failed
 * requests retry with exponential backoff; after [MAX_ATTEMPTS] failed runs
 * in a row the crawl stops and says why.
 */
class CatalogCrawlWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SiseApp).container
        val settings = container.settings
        if (!settings.crawlEnabled.first()) return Result.success()
        return when (val outcome = container.catalogCrawl.run(RUN_BUDGET_MILLIS)) {
            CatalogCrawl.Outcome.Finished -> {
                settings.setCrawlEnabled(false)
                settings.setCrawlStatus(CrawlStatus(CrawlStatus.State.FINISHED))
                Result.success()
            }
            CatalogCrawl.Outcome.OutOfTime -> {
                container.catalogCrawlScheduler.continueLater()
                Result.success()
            }
            CatalogCrawl.Outcome.Stopped -> Result.success()
            is CatalogCrawl.Outcome.Failed -> {
                val circuito = settings.crawlStatus.first().circuito
                if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                    settings.setCrawlEnabled(false)
                    settings.setCrawlStatus(CrawlStatus(CrawlStatus.State.STOPPED, circuito, outcome.error.name))
                    Result.failure()
                } else {
                    settings.setCrawlStatus(CrawlStatus(CrawlStatus.State.WAITING, circuito, outcome.error.name))
                    Result.retry()
                }
            }
        }
    }

    companion object {
        const val RUN_BUDGET_MILLIS = 8 * 60 * 1000L
        const val MAX_ATTEMPTS = 6
    }
}

/** Starts, continues and stops the [CatalogCrawlWorker]. */
class CatalogCrawlScheduler(private val workManager: () -> WorkManager) {

    fun start() = enqueue(ExistingWorkPolicy.KEEP)

    /** Queues the next chunk after the running one. */
    fun continueLater() = enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)

    fun stop() {
        workManager().cancelUniqueWork(WORK_NAME)
    }

    private fun enqueue(policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<CatalogCrawlWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        workManager().enqueueUniqueWork(WORK_NAME, policy, request)
    }

    companion object {
        const val WORK_NAME = "catalog_crawl"
    }
}
