package com.todoroo.astrid.core

import com.todoroo.andlib.utility.AndroidUtilities
import com.todoroo.astrid.api.BooleanCriterion
import com.todoroo.astrid.api.CustomFilterCriterion
import com.todoroo.astrid.api.MultipleSelectCriterion
import com.todoroo.astrid.api.TextInputCriterion
import com.todoroo.astrid.helper.UUIDHelper
import org.tasks.Strings.isNullOrEmpty
import org.tasks.filters.FilterCriteriaProvider
import timber.log.Timber
import java.util.*

class CriterionInstance {
    lateinit var criterion: CustomFilterCriterion
    var selectedIndex = -1
    var selectedText: String? = null
    var type = TYPE_INTERSECT
    var end = 0
    var start = 0
    var max = 0
    var id: String = UUIDHelper.newUUID()
        private set

    constructor()

    constructor(other: CriterionInstance) {
        id = other.id
        criterion = other.criterion
        selectedIndex = other.selectedIndex
        selectedText = other.selectedText
        type = other.type
        end = other.end
        start = other.start
        max = other.max
    }

    // $NON-NLS-1$
    val titleFromCriterion: String
        get() {
            if (criterion is MultipleSelectCriterion) {
                if (selectedIndex >= 0 && (criterion as MultipleSelectCriterion).entryTitles != null && selectedIndex < (criterion as MultipleSelectCriterion).entryTitles.size) {
                    val title = (criterion as MultipleSelectCriterion).entryTitles[selectedIndex]
                    return criterion.text.replace("?", title)
                }
                return criterion.text
            } else if (criterion is TextInputCriterion) {
                return if (selectedText == null) {
                    criterion.text
                } else criterion.text.replace("?", selectedText!!)
            } else if (criterion is BooleanCriterion) {
                return criterion.name
            }
            throw UnsupportedOperationException("Unknown criterion type") // $NON-NLS-1$
        }

    // $NON-NLS-1$
    val valueFromCriterion: String?
        get() {
            if (type == TYPE_UNIVERSE) {
                return null
            }
            if (criterion is MultipleSelectCriterion) {
                return if (selectedIndex >= 0 && (criterion as MultipleSelectCriterion).entryValues != null && selectedIndex < (criterion as MultipleSelectCriterion).entryValues.size) {
                    (criterion as MultipleSelectCriterion).entryValues[selectedIndex]
                } else criterion.text
            } else if (criterion is TextInputCriterion) {
                return selectedText
            } else if (criterion is BooleanCriterion) {
                return criterion.name
            }
            throw UnsupportedOperationException("Unknown criterion type") // $NON-NLS-1$
        }

    private fun serialize(): String {
        // criterion|entry|text|type|sql
        return listOf(
                escape(criterion.identifier),
                escape(valueFromCriterion),
                escape(criterion.text),
                type,
                criterion.sql ?: "")
                .joinToString(AndroidUtilities.SERIALIZATION_SEPARATOR)
    }

    override fun toString(): String {
        return "CriterionInstance(criterion=$criterion, selectedIndex=$selectedIndex, selectedText=$selectedText, type=$type, end=$end, start=$start, max=$max, id='$id')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CriterionInstance) return false

        if (criterion != other.criterion) return false
        if (selectedIndex != other.selectedIndex) return false
        if (selectedText != other.selectedText) return false
        if (type != other.type) return false
        if (end != other.end) return false
        if (start != other.start) return false
        if (max != other.max) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = criterion.hashCode()
        result = 31 * result + selectedIndex
        result = 31 * result + (selectedText?.hashCode() ?: 0)
        result = 31 * result + type
        result = 31 * result + end
        result = 31 * result + start
        result = 31 * result + max
        result = 31 * result + (id.hashCode() ?: 0)
        return result
    }

    companion object {
        const val TYPE_ADD = 0
        const val TYPE_SUBTRACT = 1
        const val TYPE_INTERSECT = 2
        const val TYPE_UNIVERSE = 3

        suspend fun fromString(
                provider: FilterCriteriaProvider, criterion: String): List<CriterionInstance> {
            if (criterion.isNullOrEmpty()) {
                return emptyList()
            }
            val entries: MutableList<CriterionInstance> = ArrayList()
            for (row in criterion.trim().split("\n")) {
                val split = row
                        .split(AndroidUtilities.SERIALIZATION_SEPARATOR)
                        .map { unescape(it) }
                if (split.size != 4 && split.size != 5) {
                    Timber.e("invalid row: %s", row)
                    return emptyList()
                }
                val entry = CriterionInstance()
                entry.criterion = provider.getFilterCriteria(split[0])
                val value = split[1]
                if (entry.criterion is TextInputCriterion) {
                    entry.selectedText = value
                } else if (entry.criterion is MultipleSelectCriterion) {
                    val multipleSelectCriterion = entry.criterion as MultipleSelectCriterion?
                    if (multipleSelectCriterion!!.entryValues != null) {
                        entry.selectedIndex = multipleSelectCriterion.entryValues.indexOf(value)
                    }
                } else {
                    Timber.d("Ignored value %s for %s", value, entry.criterion)
                }
                entry.type = split[3].toInt()
                entry.criterion.sql = split[4]
                Timber.d("%s -> %s", row, entry)
                entries.add(entry)
            }
            return entries
        }

        private fun escape(item: String?): String {
            return item?.replace(
                    AndroidUtilities.SERIALIZATION_SEPARATOR, AndroidUtilities.SEPARATOR_ESCAPE)
                    ?: "" // $NON-NLS-1$
        }

        private fun unescape(item: String?): String {
            return if (item.isNullOrEmpty()) {
                ""
            } else item!!.replace(
                    AndroidUtilities.SEPARATOR_ESCAPE, AndroidUtilities.SERIALIZATION_SEPARATOR)
        }

        fun serialize(criterion: List<CriterionInstance>): String {
            return criterion
                    .joinToString("\n") { it.serialize() }
                    .trim()
        }
    }
}