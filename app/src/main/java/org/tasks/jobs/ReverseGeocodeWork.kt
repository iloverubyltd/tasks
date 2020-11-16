package org.tasks.jobs

import android.content.Context
import androidx.hilt.Assisted
import androidx.hilt.work.WorkerInject
import androidx.work.WorkerParameters
import org.tasks.LocalBroadcastManager
import org.tasks.analytics.Firebase
import org.tasks.data.LocationDao
import org.tasks.injection.BaseWorker
import org.tasks.location.Geocoder
import timber.log.Timber
import java.io.IOException

class ReverseGeocodeWork @WorkerInject constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        firebase: Firebase,
        private val localBroadcastManager: LocalBroadcastManager,
        private val geocoder: Geocoder,
        private val locationDao: LocationDao) : BaseWorker(context, workerParams, firebase) {

    companion object {
        const val PLACE_ID = "place_id"
    }

    override suspend fun run(): Result {
        val id = inputData.getLong(PLACE_ID, 0)
        if (id == 0L) {
            Timber.e("Missing id")
            return Result.failure()
        }
        val place = locationDao.getPlace(id)
        if (place == null) {
            Timber.e("Can't find place $id")
            return Result.failure()
        }
        return try {
            val result = geocoder.reverseGeocode(place.mapPosition)
            if (result == null) {
                Timber.d("Result was null")
                Result.failure()
            } else {
                result.id = place.id
                result.uid = place.uid
                locationDao.update(result)
                localBroadcastManager.broadcastRefresh()
                Timber.d("found $result")
                Result.success()
            }
        } catch (e: IOException) {
            firebase.reportException(e)
            Result.failure()
        }
    }
}