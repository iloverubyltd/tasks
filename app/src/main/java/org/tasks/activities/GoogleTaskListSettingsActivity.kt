package org.tasks.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import butterknife.BindView
import com.google.android.material.textfield.TextInputEditText
import com.google.api.services.tasks.model.TaskList
import com.todoroo.astrid.activity.MainActivity
import com.todoroo.astrid.activity.TaskListFragment
import com.todoroo.astrid.api.GtasksFilter
import com.todoroo.astrid.service.TaskDeleter
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tasks.R
import org.tasks.Strings.isNullOrEmpty
import org.tasks.data.GoogleTaskAccount
import org.tasks.data.GoogleTaskList
import org.tasks.data.GoogleTaskListDao
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GoogleTaskListSettingsActivity : BaseListSettingsActivity() {
    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var googleTaskListDao: GoogleTaskListDao
    @Inject lateinit var taskDeleter: TaskDeleter

    @BindView(R.id.name)
    lateinit var name: TextInputEditText

    @BindView(R.id.progress_bar)
    lateinit var progressView: ProgressBar

    private var isNewList = false
    private lateinit var gtasksList: GoogleTaskList
    private val createListViewModel: CreateListViewModel by viewModels()
    private val renameListViewModel: RenameListViewModel by viewModels()
    private val deleteListViewModel: DeleteListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        gtasksList = intent.getParcelableExtra(EXTRA_STORE_DATA)
                ?: GoogleTaskList().apply {
                    isNewList = true
                    account = intent.getParcelableExtra<GoogleTaskAccount>(EXTRA_ACCOUNT)!!.account
                }
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            selectedColor = gtasksList.getColor()!!
            selectedIcon = gtasksList.getIcon()!!
        }
        if (isNewList) {
            name.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(name, InputMethodManager.SHOW_IMPLICIT)
        } else {
            name.setText(gtasksList.title)
        }
        if (createListViewModel.inProgress
                || renameListViewModel.inProgress
                || deleteListViewModel.inProgress) {
            showProgressIndicator()
        }
        createListViewModel.observe(this, this::onListCreated, this::requestFailed)
        renameListViewModel.observe(this, this::onListRenamed, this::requestFailed)
        deleteListViewModel.observe(this, this::onListDeleted, this::requestFailed)
        updateTheme()
    }

    override val isNew: Boolean
        get() = isNewList

    override val toolbarTitle: String?
        get() = if (isNew) getString(R.string.new_list) else gtasksList.title!!

    private fun showProgressIndicator() {
        progressView.visibility = View.VISIBLE
    }

    private fun hideProgressIndicator() {
        progressView.visibility = View.GONE
    }

    private fun requestInProgress() = progressView.visibility == View.VISIBLE

    override suspend fun save() {
        if (requestInProgress()) {
            return
        }
        val newName = newName
        if (newName.isNullOrEmpty()) {
            Toast.makeText(this, R.string.name_cannot_be_empty, Toast.LENGTH_LONG).show()
            return
        }
        when {
            isNewList -> {
                showProgressIndicator()
                createListViewModel.createList(gtasksList.account!!, newName)
            }
            nameChanged() -> {
                showProgressIndicator()
                renameListViewModel.renameList(gtasksList, newName)
            }
            else -> {
                if (colorChanged() || iconChanged()) {
                    gtasksList.setColor(selectedColor)
                    gtasksList.setIcon(selectedIcon)
                    googleTaskListDao.insertOrReplace(gtasksList)
                    setResult(
                            Activity.RESULT_OK,
                            Intent(TaskListFragment.ACTION_RELOAD)
                                    .putExtra(MainActivity.OPEN_FILTER, GtasksFilter(gtasksList)))
                }
                finish()
            }
        }
    }

    override fun finish() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(name.windowToken, 0)
        super.finish()
    }

    override val layout: Int
        get() = R.layout.activity_google_task_list_settings

    override fun promptDelete() {
        if (!requestInProgress()) {
            super.promptDelete()
        }
    }

    override suspend fun delete() {
        showProgressIndicator()
        deleteListViewModel.deleteList(gtasksList)
    }

    override fun discard() {
        if (!requestInProgress()) {
            super.discard()
        }
    }

    private val newName: String
        get() = name.text.toString().trim { it <= ' ' }

    override fun hasChanges(): Boolean {
        return if (isNewList) {
            selectedColor >= 0 || !newName.isNullOrEmpty()
        } else colorChanged() || nameChanged() || iconChanged()
    }

    private fun colorChanged() = selectedColor != gtasksList.getColor()

    private fun iconChanged() = selectedIcon != gtasksList.getIcon()

    private fun nameChanged() = newName != gtasksList.title

    private suspend fun onListCreated(taskList: TaskList) {
        gtasksList.remoteId = taskList.id
        gtasksList.title = taskList.title
        gtasksList.setColor(selectedColor)
        gtasksList.setIcon(selectedIcon)
        gtasksList.id = googleTaskListDao.insertOrReplace(gtasksList)
        setResult(
                Activity.RESULT_OK, Intent().putExtra(MainActivity.OPEN_FILTER, GtasksFilter(gtasksList)))
        finish()
    }

    private suspend fun onListDeleted(deleted: Boolean) {
        if (deleted) {
            taskDeleter.delete(gtasksList)
            setResult(Activity.RESULT_OK, Intent(TaskListFragment.ACTION_DELETED))
            finish()
        }
    }

    private suspend fun onListRenamed(taskList: TaskList) {
        gtasksList.title = taskList.title
        gtasksList.setColor(selectedColor)
        gtasksList.setIcon(selectedIcon)
        googleTaskListDao.insertOrReplace(gtasksList)
        setResult(
                Activity.RESULT_OK,
                Intent(TaskListFragment.ACTION_RELOAD)
                        .putExtra(MainActivity.OPEN_FILTER, GtasksFilter(gtasksList)))
        finish()
    }

    private fun requestFailed(error: Throwable) {
        Timber.e(error)
        hideProgressIndicator()
        Toast.makeText(this, R.string.gtasks_GLA_errorIOAuth, Toast.LENGTH_LONG).show()
        return
    }

    companion object {
        const val EXTRA_ACCOUNT = "extra_account"
        const val EXTRA_STORE_DATA = "extra_store_data"
    }
}