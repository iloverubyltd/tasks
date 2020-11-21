package org.tasks.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.huawei.hms.location.Geofence
import com.huawei.hms.location.GeofenceData
import com.todoroo.andlib.utility.DateUtilities
import com.todoroo.astrid.reminders.ReminderService
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.Notifier
import org.tasks.data.LocationDao
import org.tasks.data.Place
import org.tasks.injection.InjectingJobIntentService
import org.tasks.notifications.Notification
import org.tasks.time.DateTimeUtils
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceTransitionsIntentService : InjectingJobIntentService() {
    @Inject lateinit var locationDao: LocationDao
    @Inject lateinit var notifier: Notifier

    override suspend fun doWork(intent: Intent) {
        val geofenceData = GeofenceData.getDataFromIntent(intent)
        if (geofenceData.isFailure()) {
            Timber.e("geofence error code %s", geofenceData.errorCode)
            return
        }
        val transitionType = geofenceData.conversion
        val triggeringGeofences = geofenceData.convertingGeofenceList
        Timber.i("Received geofence transition/conversion: %s, %s", transitionType, triggeringGeofences)
        if (transitionType == Geofence.ENTER_GEOFENCE_CONVERSION || transitionType == Geofence.EXIT_GEOFENCE_CONVERSION) {
            triggeringGeofences.forEach {
                triggerNotifications(it, transitionType == Geofence.ENTER_GEOFENCE_CONVERSION)
            }
        } else {
            Timber.w("invalid geofence trigger type (conversion): %s", transitionType)
        }
    }

    private suspend fun triggerNotifications(convertingGeofence: Geofence, arrival: Boolean) {
        val uniqueId = convertingGeofence.uniqueId
        try {
            val place = locationDao.getPlace(uniqueId)
            if (place == null) {
                Timber.e("Can't find place for uniqueId %s", uniqueId)
                return
            }
            val geofences = if (arrival) {
                locationDao.getArrivalGeofences(place.uid!!, DateUtilities.now())
            } else {
                locationDao.getDepartureGeofences(place.uid!!, DateUtilities.now())
            }
            geofences
                    .map { toNotification(place, it, arrival) }
                    .let { notifier.triggerNotifications(it) }
        } catch (e: Exception) {
            Timber.e(e, "Error triggering (converting) geofence %s: %s", uniqueId, e.message)
        }
    }

    private fun toNotification(place: Place, geofence: org.tasks.data.Geofence?, arrival: Boolean): Notification {
        val notification = Notification()
        notification.taskId = geofence!!.task
        notification.type = if (arrival) ReminderService.TYPE_GEOFENCE_ENTER else ReminderService.TYPE_GEOFENCE_EXIT
        notification.timestamp = DateTimeUtils.currentTimeMillis()
        notification.location = place.id
        return notification
    }

    class Broadcast : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            enqueueWork(
                    context,
                    GeofenceTransitionsIntentService::class.java,
                    JOB_ID_GEOFENCE_TRANSITION,
                    intent)
        }
    }
}