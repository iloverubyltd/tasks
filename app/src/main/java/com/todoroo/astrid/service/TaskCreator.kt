package com.todoroo.astrid.service

import com.google.ical.values.RRule
import com.todoroo.andlib.utility.DateUtilities
import com.todoroo.astrid.api.CaldavFilter
import com.todoroo.astrid.api.Filter
import com.todoroo.astrid.api.GtasksFilter
import com.todoroo.astrid.api.PermaSql
import com.todoroo.astrid.dao.TaskDao
import com.todoroo.astrid.data.Task
import com.todoroo.astrid.data.Task.Companion.createDueDate
import com.todoroo.astrid.gcal.GCalHelper
import com.todoroo.astrid.helper.UUIDHelper
import com.todoroo.astrid.utility.TitleParser.parse
import org.tasks.R
import org.tasks.Strings.isNullOrEmpty
import org.tasks.data.*
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.preferences.Preferences
import timber.log.Timber
import java.util.*
import javax.inject.Inject

class TaskCreator @Inject constructor(
        private val gcalHelper: GCalHelper,
        private val preferences: Preferences,
        private val tagDataDao: TagDataDao,
        private val taskDao: TaskDao,
        private val tagDao: TagDao,
        private val googleTaskDao: GoogleTaskDao,
        private val defaultFilterProvider: DefaultFilterProvider,
        private val caldavDao: CaldavDao,
        private val locationDao: LocationDao) {

    suspend fun basicQuickAddTask(title: String): Task {
        var title = title
        title = title.trim { it <= ' ' }
        val task = createWithValues(title)
        taskDao.createNew(task)
        val gcalCreateEventEnabled = preferences.isDefaultCalendarSet && task.hasDueDate() // $NON-NLS-1$
        if (!task.title.isNullOrEmpty()
                && gcalCreateEventEnabled
                && task.calendarURI.isNullOrEmpty()) {
            val calendarUri = gcalHelper.createTaskEvent(task, preferences.defaultCalendar)
            task.calendarURI = calendarUri.toString()
        }
        createTags(task)
        val addToTop = preferences.addTasksToTop()
        if (task.hasTransitory(GoogleTask.KEY)) {
            googleTaskDao.insertAndShift(
                    GoogleTask(task.id, task.getTransitory<String>(GoogleTask.KEY)!!), addToTop)
        } else if (task.hasTransitory(CaldavTask.KEY)) {
            caldavDao.insert(
                    task, CaldavTask(task.id, task.getTransitory<String>(CaldavTask.KEY)), addToTop)
        } else {
            val remoteList = defaultFilterProvider.getDefaultList()
            if (remoteList is GtasksFilter) {
                googleTaskDao.insertAndShift(
                        GoogleTask(task.id, remoteList.remoteId), addToTop)
            } else if (remoteList is CaldavFilter) {
                caldavDao.insert(
                        task, CaldavTask(task.id, remoteList.uuid), addToTop)
            }
        }
        if (task.hasTransitory(Place.KEY)) {
            val place = locationDao.getPlace(task.getTransitory<String>(Place.KEY)!!)
            if (place != null) {
                locationDao.insert(Geofence(place.uid, preferences))
            }
        }
        taskDao.save(task, null)
        return task
    }

    suspend fun createWithValues(title: String?): Task {
        return create(null, title)
    }

    suspend fun createWithValues(filter: Filter?, title: String?): Task {
        return create(filter?.valuesForNewTasks, title)
    }

    /**
     * Create task from the given content values, saving it. This version doesn't need to start with a
     * base task model.
     */
    private suspend fun create(values: Map<String, Any>?, title: String?): Task {
        val task = Task()
        task.creationDate = DateUtilities.now()
        task.modificationDate = DateUtilities.now()
        if (title != null) {
            task.title = title.trim { it <= ' ' }
        }
        task.uuid = UUIDHelper.newUUID()
        task.priority = preferences.defaultPriority
        task.dueDate = createDueDate(
                preferences.getIntegerFromString(R.string.p_default_urgency_key, Task.URGENCY_NONE),
                0)
        preferences.getStringValue(R.string.p_default_recurrence)
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    task.setRecurrence(
                            RRule(it),
                            preferences.getIntegerFromString(R.string.p_default_recurrence_from, 0) == 1)
                }
        val setting = preferences.getIntegerFromString(R.string.p_default_hideUntil_key, Task.HIDE_UNTIL_NONE)
        preferences.getStringValue(R.string.p_default_location)
                ?.takeIf { it.isNotBlank() }
                ?.let { task.putTransitory(Place.KEY, it) }
        task.hideUntil = task.createHideUntil(setting, 0)
        setDefaultReminders(preferences, task)
        val tags = ArrayList<String>()
        if (values != null && values.isNotEmpty()) {
            for (item in values.entries) {
                val key = item.key
                var value: Any? = item.value
                when (key) {
                    Tag.KEY -> tags.add(value as String)
                    GoogleTask.KEY, CaldavTask.KEY, Place.KEY -> task.putTransitory(key, value!!)
                    else -> {
                        if (value is String) {
                            value = PermaSql.replacePlaceholdersForNewTask(value as String?)
                        }
                        when (key) {
                            "dueDate" -> task.dueDate = java.lang.Long.valueOf((value as String?)!!)
                            "importance" -> task.priority = Integer.valueOf((value as String?)!!)
                            else -> {
                            }
                        }
                    }
                }
            }
        }
        if (tags.isEmpty()) {
            preferences.getStringValue(R.string.p_default_tags)
                    ?.split(",")
                    ?.map { tagDataDao.getByUuid(it) }
                    ?.mapNotNull { it?.name }
                    ?.let { tags.addAll(it) }
        }
        try {
            parse(tagDataDao, task, tags)
        } catch (e: Throwable) {
            Timber.e(e)
        }
        task.setTags(tags)
        return task
    }

    suspend fun createTags(task: Task) {
        for (tag in task.tags) {
            var tagData = tagDataDao.getTagByName(tag)
            if (tagData == null) {
                tagData = TagData()
                tagData.name = tag
                tagDataDao.createNew(tagData)
            }
            tagDao.insert(Tag(task, tagData))
        }
    }

    companion object {
        private fun setDefaultReminders(preferences: Preferences, task: Task) {
            task.reminderPeriod = (DateUtilities.ONE_HOUR
                    * preferences.getIntegerFromString(R.string.p_rmd_default_random_hours, 0))
            task.reminderFlags = preferences.defaultReminders or preferences.defaultRingMode
        }
    }
}