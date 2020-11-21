package org.tasks.sync

import android.app.Dialog
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.use
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.todoroo.astrid.gtasks.auth.GtasksLoginActivity
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.BuildConfig
import org.tasks.R
import org.tasks.auth.SignInActivity
import org.tasks.caldav.CaldavAccountSettingsActivity
import org.tasks.dialogs.DialogBuilder
import org.tasks.etesync.EteSyncAccountSettingsActivity
import org.tasks.preferences.fragments.Synchronization.Companion.REQUEST_CALDAV_SETTINGS
import org.tasks.preferences.fragments.Synchronization.Companion.REQUEST_GOOGLE_TASKS
import org.tasks.preferences.fragments.Synchronization.Companion.REQUEST_TASKS_ORG
import org.tasks.themes.DrawableUtil
import javax.inject.Inject

@AndroidEntryPoint
class AddAccountDialog : DialogFragment() {

    @Inject lateinit var dialogBuilder: DialogBuilder

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val services = requireActivity().resources.getStringArray(R.array.synchronization_services)
        val descriptions = requireActivity().resources.getStringArray(R.array.synchronization_services_description)
        val typedArray = requireActivity().resources.obtainTypedArray(R.array.synchronization_services_icons)
        val icons = typedArray.use {
            val newArr = IntArray(it.length())
            for (i in newArr.indices) {
                newArr[i] = it.getResourceId(i, 0)
            }
            newArr
        }
        val supportedArray = requireActivity().resources.obtainTypedArray(R.array.synchronization_services_supported)
        val supported = supportedArray.use {
            val newArr = BooleanArray(it.length())
            for (i in newArr.indices) {
                newArr[i] = it.getBoolean(i, true)
            }
            newArr
        }

        val adapter: ArrayAdapter<String> = object : ArrayAdapter<String>(
                requireActivity(), R.layout.simple_list_item_2_themed, R.id.text1, services) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                if (!supported[position]) {
                    view.visibility = View.GONE
                    return view
                }
                view.findViewById<TextView>(R.id.text1).text = services[position]
                view.findViewById<TextView>(R.id.text2).text = descriptions[position]
                val icon = view.findViewById<ImageView>(R.id.image_view)
                icon.setImageDrawable(DrawableUtil.getWrapped(context, icons[position]))
                if (position == 2) {
                    icon.drawable.setTint(context.getColor(R.color.icon_tint))
                }
                return view
            }
        }
        return dialogBuilder
                .newDialog()
                .setTitle(R.string.choose_synchronization_service)
                .setSingleChoiceItems(adapter, -1) { dialog, which ->
                    if (supported[which]) {
                        when (which) {
                            0 -> if (BuildConfig.FLAVOR == "generic") {
                                dialogBuilder
                                    .newDialog(R.string.github_sponsor_login)
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            } else {
                                activity?.startActivityForResult(
                                    Intent(activity, SignInActivity::class.java),
                                    REQUEST_TASKS_ORG
                                )
                            }
                            1 -> activity?.startActivityForResult(
                                Intent(activity, GtasksLoginActivity::class.java),
                                REQUEST_GOOGLE_TASKS
                            )
                            2 -> activity?.startActivityForResult(
                                Intent(activity, CaldavAccountSettingsActivity::class.java),
                                REQUEST_CALDAV_SETTINGS
                            )
                            3 -> activity?.startActivityForResult(
                                Intent(activity, EteSyncAccountSettingsActivity::class.java),
                                REQUEST_CALDAV_SETTINGS
                            )
                            4 -> activity?.startActivity(
                                Intent(ACTION_VIEW, Uri.parse(getString(R.string.url_davx5)))
                            )
                        }
                    }
                    dialog.dismiss()
                }
                .setNeutralButton(R.string.help) { _, _ ->
                    activity?.startActivity(
                            Intent(
                                    ACTION_VIEW,
                                    Uri.parse(context?.getString(R.string.help_url_sync))
                            )
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    companion object {
        fun newAccountDialog(targetFragment: Fragment, rc: Int): AddAccountDialog {
            val dialog = AddAccountDialog()
            dialog.setTargetFragment(targetFragment, rc)
            return dialog
        }
    }
}
