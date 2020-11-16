package org.tasks.caldav

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import at.bitfire.dav4jvm.exception.HttpException
import butterknife.BindView
import butterknife.OnTextChanged
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.todoroo.astrid.activity.MainActivity
import com.todoroo.astrid.activity.TaskListFragment
import com.todoroo.astrid.api.CaldavFilter
import com.todoroo.astrid.helper.UUIDHelper
import com.todoroo.astrid.service.TaskDeleter
import kotlinx.coroutines.runBlocking
import org.tasks.R
import org.tasks.Strings.isNullOrEmpty
import org.tasks.activities.BaseListSettingsActivity
import org.tasks.data.CaldavAccount
import org.tasks.data.CaldavCalendar
import org.tasks.data.CaldavDao
import org.tasks.ui.DisplayableException
import java.net.ConnectException
import javax.inject.Inject

abstract class BaseCaldavCalendarSettingsActivity : BaseListSettingsActivity() {
    @Inject lateinit var caldavDao: CaldavDao
    @Inject lateinit var taskDeleter: TaskDeleter

    @BindView(R.id.root_layout)
    lateinit var root: LinearLayout

    @BindView(R.id.name)
    lateinit var name: TextInputEditText

    @BindView(R.id.name_layout)
    lateinit var nameLayout: TextInputLayout

    @BindView(R.id.progress_bar)
    lateinit var progressView: ProgressBar

    private var caldavCalendar: CaldavCalendar? = null
    private lateinit var caldavAccount: CaldavAccount

    override val layout: Int
        get() = R.layout.activity_caldav_calendar_settings

    override fun onCreate(savedInstanceState: Bundle?) {
        val intent = intent
        caldavCalendar = intent.getParcelableExtra(EXTRA_CALDAV_CALENDAR)
        super.onCreate(savedInstanceState)
        caldavAccount = if (caldavCalendar == null) {
            intent.getParcelableExtra(EXTRA_CALDAV_ACCOUNT)!!
        } else {
            runBlocking { caldavDao.getAccountByUuid(caldavCalendar!!.account!!)!! }
        }
        if (savedInstanceState == null) {
            if (caldavCalendar != null) {
                name.setText(caldavCalendar!!.name)
                selectedColor = caldavCalendar!!.color
                selectedIcon = caldavCalendar!!.getIcon()!!
            }
        }
        if (caldavCalendar == null) {
            name.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(name, InputMethodManager.SHOW_IMPLICIT)
        }
        updateTheme()
    }

    override val isNew: Boolean
        get() = caldavCalendar == null

    override val toolbarTitle: String
        get() = if (isNew) getString(R.string.new_list) else caldavCalendar!!.name!!

    @OnTextChanged(R.id.name)
    fun onNameChanged() {
        nameLayout.error = null
    }

    override suspend fun save() {
        if (requestInProgress()) {
            return
        }
        val name = newName
        if (name.isNullOrEmpty()) {
            nameLayout.error = getString(R.string.name_cannot_be_empty)
            return
        }
        if (caldavCalendar == null) {
            showProgressIndicator()
            createCalendar(caldavAccount, name, selectedColor)
        } else if (nameChanged() || colorChanged()) {
            showProgressIndicator()
            updateNameAndColor(caldavAccount, caldavCalendar!!, name, selectedColor)
        } else if (iconChanged()) {
            updateCalendar()
        } else {
            finish()
        }
    }

    protected abstract suspend fun createCalendar(caldavAccount: CaldavAccount, name: String, color: Int)

    protected abstract suspend fun updateNameAndColor(
            account: CaldavAccount, calendar: CaldavCalendar, name: String, color: Int)

    protected abstract suspend fun deleteCalendar(
            caldavAccount: CaldavAccount, caldavCalendar: CaldavCalendar)

    private fun showProgressIndicator() {
        progressView.visibility = View.VISIBLE
    }

    private fun hideProgressIndicator() {
        progressView.visibility = View.GONE
    }

    private fun requestInProgress(): Boolean {
        return progressView.visibility == View.VISIBLE
    }

    protected fun requestFailed(t: Throwable) {
        hideProgressIndicator()
        when (t) {
            is HttpException -> showSnackbar(t.message)
            is DisplayableException -> showSnackbar(t.resId)
            is ConnectException -> showSnackbar(R.string.network_error)
            else -> showSnackbar(R.string.error_adding_account, t.message!!)
        }
        return
    }

    private fun showSnackbar(resId: Int, vararg formatArgs: Any) {
        showSnackbar(getString(resId, *formatArgs))
    }

    private fun showSnackbar(message: String?) {
        val snackbar = Snackbar.make(root, message!!, 8000)
                .setTextColor(getColor(R.color.snackbar_text_color))
                .setActionTextColor(getColor(R.color.snackbar_action_color))
        snackbar
                .view
                .setBackgroundColor(getColor(R.color.snackbar_background))
        snackbar.show()
    }

    protected suspend fun createSuccessful(url: String?) {
        val caldavCalendar = CaldavCalendar()
        caldavCalendar.uuid = UUIDHelper.newUUID()
        caldavCalendar.account = caldavAccount.uuid
        caldavCalendar.url = url
        caldavCalendar.name = newName
        caldavCalendar.color = selectedColor
        caldavCalendar.setIcon(selectedIcon)
        caldavDao.insert(caldavCalendar)
        setResult(
                Activity.RESULT_OK,
                Intent().putExtra(MainActivity.OPEN_FILTER, CaldavFilter(caldavCalendar)))
        finish()
    }

    protected suspend fun updateCalendar() {
        caldavCalendar!!.name = newName
        caldavCalendar!!.color = selectedColor
        caldavCalendar!!.setIcon(selectedIcon)
        caldavDao.update(caldavCalendar!!)
        setResult(
                Activity.RESULT_OK,
                Intent(TaskListFragment.ACTION_RELOAD)
                        .putExtra(MainActivity.OPEN_FILTER, CaldavFilter(caldavCalendar)))
        finish()
    }

    override fun hasChanges(): Boolean {
        return if (caldavCalendar == null) !newName.isNullOrEmpty() || selectedColor != 0 || selectedIcon != -1 else nameChanged() || iconChanged() || colorChanged()
    }

    private fun nameChanged(): Boolean {
        return caldavCalendar!!.name != newName
    }

    private fun colorChanged(): Boolean {
        return selectedColor != caldavCalendar!!.color
    }

    private fun iconChanged(): Boolean {
        return selectedIcon != caldavCalendar!!.getIcon()
    }

    private val newName: String
        get() = name.text.toString().trim { it <= ' ' }

    override fun finish() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(name.windowToken, 0)
        super.finish()
    }

    override fun discard() {
        if (!requestInProgress()) {
            super.discard()
        }
    }

    override fun promptDelete() {
        if (!requestInProgress()) {
            super.promptDelete()
        }
    }

    override suspend fun delete() {
        showProgressIndicator()
        deleteCalendar(caldavAccount, caldavCalendar!!)
    }

    protected suspend fun onDeleted(deleted: Boolean) {
        if (deleted) {
            taskDeleter.delete(caldavCalendar!!)
            setResult(Activity.RESULT_OK, Intent(TaskListFragment.ACTION_DELETED))
            finish()
        }
    }

    companion object {
        const val EXTRA_CALDAV_CALENDAR = "extra_caldav_calendar"
        const val EXTRA_CALDAV_ACCOUNT = "extra_caldav_account"
    }
}