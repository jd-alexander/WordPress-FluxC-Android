package org.wordpress.android.fluxc.store

import org.wordpress.android.fluxc.persistence.PostSchedulingNotificationSqlUtils
import org.wordpress.android.fluxc.persistence.PostSchedulingNotificationSqlUtils.SchedulingReminderDbModel
import org.wordpress.android.fluxc.store.PostSchedulingNotificationStore.SchedulingReminderModel.Period
import org.wordpress.android.fluxc.store.PostSchedulingNotificationStore.SchedulingReminderModel.Period.OFF
import org.wordpress.android.fluxc.store.PostSchedulingNotificationStore.SchedulingReminderModel.Period.ONE_HOUR
import org.wordpress.android.fluxc.store.PostSchedulingNotificationStore.SchedulingReminderModel.Period.TEN_MINUTES
import org.wordpress.android.fluxc.store.PostSchedulingNotificationStore.SchedulingReminderModel.Period.WHEN_PUBLISHED
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostSchedulingNotificationStore
@Inject constructor(private val sqlUtils: PostSchedulingNotificationSqlUtils) {
    fun schedule(postId: Int, schedulingReminderPeriod: Period): Int? {
        val dbModel = schedulingReminderPeriod.toDbModel()
        return if (dbModel == null) {
            sqlUtils.deletePostSchedulingNotifications(postId)
            null
        } else {
            sqlUtils.insert(postId, dbModel)
        }
    }

    fun deletePostSchedulingNotifications(postId: Int) {
        sqlUtils.deletePostSchedulingNotifications(postId)
    }

    fun getNotification(notificationId: Int): SchedulingReminderModel? {
        val dmModel = sqlUtils.getNotification(notificationId)
        return dmModel?.let { SchedulingReminderModel(it.notificationId, it.postId, it.period.toDomainModel()) }
    }

    fun getSchedulingReminderPeriod(postId: Int): Period {
        return when (sqlUtils.getSchedulingReminderPeriodDbModel(postId)) {
            SchedulingReminderDbModel.Period.ONE_HOUR -> ONE_HOUR
            SchedulingReminderDbModel.Period.TEN_MINUTES -> TEN_MINUTES
            SchedulingReminderDbModel.Period.WHEN_PUBLISHED -> WHEN_PUBLISHED
            null -> OFF
        }
    }

    private fun SchedulingReminderDbModel.Period?.toDomainModel(): Period {
        return when (this) {
            SchedulingReminderDbModel.Period.ONE_HOUR -> ONE_HOUR
            SchedulingReminderDbModel.Period.TEN_MINUTES -> TEN_MINUTES
            SchedulingReminderDbModel.Period.WHEN_PUBLISHED -> WHEN_PUBLISHED
            null -> OFF
        }
    }

    private fun Period.toDbModel(): SchedulingReminderDbModel.Period? {
        return when (this) {
            ONE_HOUR -> SchedulingReminderDbModel.Period.ONE_HOUR
            TEN_MINUTES -> SchedulingReminderDbModel.Period.TEN_MINUTES
            WHEN_PUBLISHED -> SchedulingReminderDbModel.Period.WHEN_PUBLISHED
            OFF -> null
        }
    }

    data class SchedulingReminderModel(
        val notificationId: Int,
        val postId: Int,
        val scheduledTime: Period
    ) {
        enum class Period {
            OFF, ONE_HOUR, TEN_MINUTES, WHEN_PUBLISHED
        }
    }
}
