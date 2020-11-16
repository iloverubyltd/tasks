/*
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.Behavior.DragCallback
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import com.todoroo.andlib.utility.AndroidUtilities
import com.todoroo.andlib.utility.DateUtilities
import com.todoroo.astrid.api.Filter
import com.todoroo.astrid.dao.TaskDao
import com.todoroo.astrid.data.Task
import com.todoroo.astrid.notes.CommentsController
import com.todoroo.astrid.repeats.RepeatControlSet
import com.todoroo.astrid.timers.TimerPlugin
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tasks.Event
import org.tasks.R
import org.tasks.Strings.isNullOrEmpty
import org.tasks.analytics.Firebase
import org.tasks.data.*
import org.tasks.databinding.FragmentTaskEditBinding
import org.tasks.date.DateTimeUtils.newDateTime
import org.tasks.dialogs.DialogBuilder
import org.tasks.dialogs.Linkify
import org.tasks.files.FileHelper
import org.tasks.fragments.TaskEditControlSetFragmentManager
import org.tasks.notifications.NotificationManager
import org.tasks.preferences.Preferences
import org.tasks.themes.ThemeColor
import org.tasks.ui.SubtaskControlSet
import org.tasks.ui.TaskEditControlFragment
import org.tasks.ui.TaskEditViewModel
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class TaskEditFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var userActivityDao: UserActivityDao
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var dialogBuilder: DialogBuilder
    @Inject lateinit var context: Activity
    @Inject lateinit var taskEditControlSetFragmentManager: TaskEditControlSetFragmentManager
    @Inject lateinit var commentsController: CommentsController
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var firebase: Firebase
    @Inject lateinit var timerPlugin: TimerPlugin
    @Inject lateinit var linkify: Linkify

    val editViewModel: TaskEditViewModel by viewModels()
    lateinit var binding: FragmentTaskEditBinding
    private var showKeyboard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = requireArguments()
        editViewModel.setup(
                args.getParcelable(EXTRA_TASK)!!,
                args.getParcelable(EXTRA_LIST)!!,
                args.getParcelable(EXTRA_LOCATION),
                args.getParcelableArrayList(EXTRA_TAGS)!!,
                args.getLongArray(EXTRA_ALARMS)!!)
        val activity = requireActivity() as MainActivity
        editViewModel.cleared.observe(activity, Observer<Event<Boolean>> {
            activity.removeTaskEditFragment()
        })
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentTaskEditBinding.inflate(inflater)
        val view: View = binding.root
        val model = editViewModel.task!!
        val themeColor: ThemeColor = requireArguments().getParcelable(EXTRA_THEME)!!
        val toolbar = binding.toolbar
        toolbar.navigationIcon = context.getDrawable(R.drawable.ic_outline_save_24px)
        toolbar.setNavigationOnClickListener {
            lifecycleScope.launch {
                save()
            }
        }
        val backButtonSavesTask = preferences.backButtonSavesTask()
        toolbar.inflateMenu(R.menu.menu_task_edit_fragment)
        val menu = toolbar.menu
        val delete = menu.findItem(R.id.menu_delete)
        delete.isVisible = !model.isNew
        delete.setShowAsAction(
                if (backButtonSavesTask) MenuItem.SHOW_AS_ACTION_NEVER else MenuItem.SHOW_AS_ACTION_IF_ROOM)
        val discard = menu.findItem(R.id.menu_discard)
        discard.isVisible = backButtonSavesTask
        discard.setShowAsAction(
                if (model.isNew) MenuItem.SHOW_AS_ACTION_IF_ROOM else MenuItem.SHOW_AS_ACTION_NEVER)
        if (savedInstanceState == null) {
            showKeyboard = model.isNew && model.title.isNullOrEmpty()
        }
        val params = binding.appbarlayout.layoutParams as CoordinatorLayout.LayoutParams
        params.behavior = AppBarLayout.Behavior()
        val behavior = params.behavior as AppBarLayout.Behavior?
        behavior!!.setDragCallback(object : DragCallback() {
            override fun canDrag(appBarLayout: AppBarLayout): Boolean {
                return false
            }
        })
        toolbar.setOnMenuItemClickListener(this)
        themeColor.apply(binding.collapsingtoolbarlayout, toolbar)
        val title = binding.title
        title.setText(model.title)
        title.setHorizontallyScrolling(false)
        title.setTextColor(themeColor.colorOnPrimary)
        title.setHintTextColor(themeColor.hintOnPrimary)
        title.maxLines = 5
        title.addTextChangedListener { 
            editViewModel.title = title.text.toString().trim { it <= ' ' }
        }
        if (model.isNew || preferences.getBoolean(R.string.p_hide_check_button, false)) {
            binding.fab.visibility = View.INVISIBLE
        } else if (editViewModel.completed!!) {
            title.paintFlags = title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            binding.fab.setImageResource(R.drawable.ic_outline_check_box_outline_blank_24px)
        }
        binding.fab.setOnClickListener {
            if (editViewModel.completed!!) {
                editViewModel.completed = false
                title.paintFlags = title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.fab.setImageResource(R.drawable.ic_outline_check_box_24px)
            } else {
                editViewModel.completed = true
                lifecycleScope.launch {
                    save()
                }
            }
        }
        if (AndroidUtilities.atLeastQ()) {
            title.verticalScrollbarThumbDrawable = ColorDrawable(themeColor.hintOnPrimary)
        }
        binding.appbarlayout.addOnOffsetChangedListener(
                OnOffsetChangedListener { appBarLayout: AppBarLayout, verticalOffset: Int ->
                    if (verticalOffset == 0) {
                        title.visibility = View.VISIBLE
                        binding.collapsingtoolbarlayout.isTitleEnabled = false
                    } else if (abs(verticalOffset) < appBarLayout.totalScrollRange) {
                        title.visibility = View.INVISIBLE
                        binding.collapsingtoolbarlayout.title = title.text
                        binding.collapsingtoolbarlayout.isTitleEnabled = true
                    }
                })
        if (!model.isNew) {
            lifecycleScope.launch {
                notificationManager.cancel(model.id)
            }
            if (preferences.getBoolean(R.string.p_linkify_task_edit, false)) {
                linkify.linkify(title)
            }
        }
        commentsController.initialize(model, binding.comments)
        commentsController.reloadView()
        val fragmentManager = childFragmentManager
        val taskEditControlFragments =
                taskEditControlSetFragmentManager.getOrCreateFragments(fragmentManager)
        val visibleSize = taskEditControlSetFragmentManager.visibleSize
        val fragmentTransaction = fragmentManager.beginTransaction()
        for (i in taskEditControlFragments.indices) {
            val taskEditControlFragment = taskEditControlFragments[i]
            val tag = getString(taskEditControlFragment.controlId())
            fragmentTransaction.replace(
                    TaskEditControlSetFragmentManager.TASK_EDIT_CONTROL_FRAGMENT_ROWS[i],
                    taskEditControlFragment,
                    tag)
            if (i >= visibleSize) {
                fragmentTransaction.hide(taskEditControlFragment)
            }
        }
        fragmentTransaction.commit()
        for (i in visibleSize - 1 downTo 1) {
            binding.controlSets.addView(inflater.inflate(R.layout.task_edit_row_divider, binding.controlSets, false), i)
        }
        return view
    }

    override fun onResume() {
        super.onResume()

        if (showKeyboard) {
            binding.title.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.title, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        AndroidUtilities.hideKeyboard(activity)
        if (item.itemId == R.id.menu_delete) {
            deleteButtonClick()
            return true
        } else if (item.itemId == R.id.menu_discard) {
            discardButtonClick()
            return true
        }
        return false
    }

    suspend fun stopTimer(): Task {
        val model = editViewModel.task!!
        timerPlugin.stopTimer(model)
        val elapsedTime = DateUtils.formatElapsedTime(model.elapsedSeconds.toLong())
        addComment(String.format(
                "%s %s\n%s %s",  // $NON-NLS-1$
                getString(R.string.TEA_timer_comment_stopped),
                DateUtilities.getTimeString(context, newDateTime()),
                getString(R.string.TEA_timer_comment_spent),
                elapsedTime),
                null)
        return model
    }

    suspend fun startTimer(): Task {
        val model = editViewModel.task!!
        timerPlugin.startTimer(model)
        addComment(String.format(
                "%s %s",
                getString(R.string.TEA_timer_comment_started),
                DateUtilities.getTimeString(context, newDateTime())),
                null)
        return model
    }

    /** Save task model from values in UI components  */
    suspend fun save() = withContext(NonCancellable) {
        val tlf = (activity as MainActivity?)?.taskListFragment
        val saved = editViewModel.save()
        if (saved && editViewModel.isNew) {
            tlf?.let { taskListFragment ->
                val model = editViewModel.task!!
                taskListFragment.onTaskCreated(model.uuid)
                if (!model.calendarURI.isNullOrEmpty()) {
                    taskListFragment.makeSnackbar(R.string.calendar_event_created, model.title)
                            ?.setAction(R.string.action_open) {
                                val uri = model.calendarURI
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                taskListFragment.startActivity(intent)
                            }
                            ?.show()
                }
            }
        }
    }

    /*
   * ======================================================================
   * =============================================== model reading / saving
   * ======================================================================
   */
    private val repeatControlSet: RepeatControlSet?
        get() = getFragment<RepeatControlSet>(RepeatControlSet.TAG)

    private val subtaskControlSet: SubtaskControlSet?
        get() = getFragment<SubtaskControlSet>(SubtaskControlSet.TAG)

    private fun <T : TaskEditControlFragment?> getFragment(tag: Int): T? {
        return childFragmentManager.findFragmentByTag(getString(tag)) as T?
    }

   /*
   * ======================================================================
   * ======================================================= event handlers
   * ======================================================================
   */
    fun discardButtonClick() {
       if (editViewModel.hasChanges()) {
           dialogBuilder
                   .newDialog(R.string.discard_confirmation)
                   .setPositiveButton(R.string.keep_editing, null)
                   .setNegativeButton(R.string.discard) { _, _ -> discard() }
                   .show()
       } else {
           discard()
       }
    }

    private fun deleteButtonClick() {
        dialogBuilder
                .newDialog(R.string.DLG_delete_this_task_question)
                .setPositiveButton(R.string.ok) { _, _ -> delete() }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun discard() = lifecycleScope.launch {
        editViewModel.discard()
    }

    private fun delete() = lifecycleScope.launch {
        editViewModel.delete()
    }

    /*
   * ======================================================================
   * ========================================== UI component helper classes
   * ======================================================================
   */
    fun onDueDateChanged() {
        repeatControlSet?.onDueDateChanged()
    }

    fun onRemoteListChanged(filter: Filter?) {
        subtaskControlSet?.onRemoteListChanged(filter)
    }

    fun addComment(message: String?, picture: Uri?) {
        val model = editViewModel.task!!
        val userActivity = UserActivity()
        if (picture != null) {
            val output = FileHelper.copyToUri(context, preferences.attachmentsDirectory!!, picture)
            userActivity.setPicture(output)
        }
        userActivity.message = message
        userActivity.targetId = model.uuid
        userActivity.created = DateUtilities.now()
        lifecycleScope.launch {
            withContext(NonCancellable) {
                userActivityDao.createNew(userActivity)
            }
            commentsController.reloadView()
        }
    }

    companion object {
        const val TAG_TASKEDIT_FRAGMENT = "taskedit_fragment"
        private const val EXTRA_TASK = "extra_task"
        private const val EXTRA_LIST = "extra_list"
        private const val EXTRA_LOCATION = "extra_location"
        private const val EXTRA_TAGS = "extra_tags"
        private const val EXTRA_ALARMS = "extra_alarms"
        private const val EXTRA_THEME = "extra_theme"

        fun newTaskEditFragment(
                task: Task,
                list: Filter,
                location: Location?,
                tags: ArrayList<TagData>,
                alarms: ArrayList<Alarm>,
                themeColor: ThemeColor?): TaskEditFragment {
            val taskEditFragment = TaskEditFragment()
            val arguments = Bundle()
            arguments.putParcelable(EXTRA_TASK, task)
            arguments.putParcelable(EXTRA_LIST, list)
            arguments.putParcelable(EXTRA_LOCATION, location)
            arguments.putParcelableArrayList(EXTRA_TAGS, tags)
            arguments.putLongArray(EXTRA_ALARMS, alarms.map { it.time }.toLongArray())
            arguments.putParcelable(EXTRA_THEME, themeColor)
            taskEditFragment.arguments = arguments
            return taskEditFragment
        }
    }
}