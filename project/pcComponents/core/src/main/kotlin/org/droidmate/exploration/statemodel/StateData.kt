// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.exploration.statemodel

import org.droidmate.configuration.ConfigProperties.ModelProperties.dump.sep
import org.droidmate.exploration.statemodel.Widget.Companion.widgetHeader
import java.io.File
import java.util.*

/**
 * States have two components, the Id determined by its Widgets image, text and description and the ConfigId defined by the WidgetsProperties.
 ** be aware that the list of widgets is not guaranteed to be sorted in any specific order*/
class StateData /*private*/(private val _widgets: Lazy<List<Widget>>,
                            val topNodePackageName: String = "", val androidLauncherPackageName: String = "", //TODO check if androidLauncherPackageName really necessary
                            val isHomeScreen: Boolean = false,
                            val isAppHasStoppedDialogBox: Boolean = false,
                            val isRequestRuntimePermissionDialogBox: Boolean = false) {

	constructor(widgets: Set<Widget>, homeScreen:Boolean, topPackage: String) : this(lazyOf(widgets.toList()),isHomeScreen = homeScreen, topNodePackageName = topPackage)

	val widgets by lazy { _widgets.value.sortedBy { it.id.dumpString() }.distinctBy { it.id } }

//  constructor(widgets: Collection<Widget>, topNodePackageName:String, androidLauncherPackageName:String,
//              isHomeScreen: Boolean, isAppHasStoppedDialogBox: Boolean,
//              isRequestRuntimePermissionDialogBox: Boolean = false, isCompleteActionUsingDialogBox: Boolean,
//              isSelectAHomeAppDialogBox: Boolean = false, isUseLauncherAsHomeDialogBox: Boolean
//  )
//        :this(widgets/*.sortedBy { it.uid.dumpString() }*/,topNodePackageName, androidLauncherPackageName, isHomeScreen,
//      isAppHasStoppedDialogBox,isRequestRuntimePermissionDialogBox,isCompleteActionUsingDialogBox,
//      isSelectAHomeAppDialogBox,isUseLauncherAsHomeDialogBox)

	// ignore nonInteractive parent views from ID computations to better re-identify state unique ids but consider them for the configIds
	private val lazyIds: Lazy<ConcreteId> =
			lazy {
				widgets.fold(Pair(emptyUUID, emptyUUID)) { (id, configId), widget ->  // e.g. keyboard elements have a different package-name and are therefore ignored for uid computation
					// however different selectable auto-completion proposes are only 'rendered' such that we have to include the img id to ensure different state configuration id's if these are different
					Pair(addRelevantId(id, widget), configId + if(ignoredTarget(widget)) widget.uid + widget.propertyId else widget.propertyId)
				}
			}
	private val ignoredTarget:(Widget)->Boolean = { w -> (w.packageName != topNodePackageName && w.canBeActedUpon)
		|| (!w.hasContent())  // alternatively we could just always add uid to configId to be sure all cases are covered?
	}

	val uid: UUID by lazy { lazyIds.value.first }
	val configId: UUID by lazy { lazyIds.value.second }
	val stateId by lazy {
		ConcreteId(uid, configId)
	}
	/** id computed like uid while ignoring all edit fields */
	val iEditId: UUID by lazy {
		//lazyIds.value.third
		widgets.fold(emptyUUID) { iEdit, widget -> addRelevantNonEdit(iEdit, widget) }
	}

	val actionableWidgets by lazy { widgets.filter { it.canBeActedUpon } }
	val distinctTargets by lazy { actionableWidgets.filter { it.isLeaf || it.uncoveredCoord!=null }}
	val hasEdit: Boolean by lazy { widgets.any { it.isEdit } }

	// for elements without text content only the image is available which may introduce variance just due to sligh color differences, therefore
	// non-text elements are only considered if they can be acted upon and don't have actable descendents
	fun isRelevantForId(w: Widget): Boolean = !isHomeScreen && w.packageName == topNodePackageName && (w.hasContent() || (w.isLeaf && w.canBeActedUpon) || (w.canBeActedUpon && !w.hasActableDescendant)
			)
	/** this function is used to add any widget.uid if it fulfills specific criteria (i.e. it belongs to the app, can be acted upon, has text content or it is a leaf) */
	private fun addRelevantId(id: UUID, w: Widget): UUID = if (isRelevantForId(w)) id + w.uid else id

	private fun addRelevantNonEdit(id: UUID, w: Widget): UUID = if (w.isEdit) addRelevantId(id, w) else id

	/** determine which UID this state would have, if it ignores [widgets] for the id computation
	 * this is used to identify consequent states where interacted edit fields are to be ignored
	 * for UID computation (instead the initial edit field UID will be restored) */
	fun idWhenIgnoring(widgets: Collection<Widget>): UUID = widgets.fold(emptyUUID) { id, w ->
		if (!widgets.contains(w)) addRelevantId(id, w) else id
	}

	val hasActionableWidgets by lazy{ actionableWidgets.isNotEmpty() }

	/** write CSV
	 *
	 * [uid] => state_id as file name
	 */
	fun dump(config: ModelConfig) {
		File(config.widgetFile(stateId,isHomeScreen,topNodePackageName)).bufferedWriter().use { all ->
			all.write(widgetHeader(config[sep]))

			widgets.sortedBy { it.uid }.forEach {
				all.newLine()
				all.write(it.dataString(config[sep]))
			}
		}
	}

	companion object {
		// this is basically extending the primary constructor
//    @JvmStatic operator fun invoke(widgets: Set<Widget>): StateData = StateData(widgets.toList()//.sortedBy { it.uid.dumpString() }
//    )

		// to load the model from previously stored files
		@JvmStatic
		fun fromFile(widgets: Set<Widget>, homeScreen:Boolean, topPackage: String): StateData = StateData(widgets,homeScreen,topPackage)

		/** dummy element if a state has to be given but no widget data is available */
		@JvmStatic
		val emptyState: StateData by lazy { StateData(lazy{ emptyList<Widget>() }) }

		/** compute the unique id containing all widgets with [widgetIds] and the [iEditId]
		 * assume that the given set of ids are all relevant for the uid computation*/
		@JvmStatic
		fun computeIds(widgetIds: List<UUID>, editIds: List<UUID>): Pair<UUID, UUID> =
				widgetIds.fold(Pair(emptyUUID, emptyUUID)
				) { (id, iEdit): Pair<UUID, UUID>, widget ->
					Pair(id + widget, if (editIds.contains(id)) iEdit else (iEdit + id))
				}

	}

	override fun equals(other: Any?): Boolean {
		return when (other) {
			is StateData -> uid == other.uid && configId == other.configId
			else -> false
		}
	}

	override fun hashCode(): Int {
		return uid.hashCode() + configId.hashCode()
	}

	override fun toString(): String {
		return "StateData[${stateId.dumpString()}, widgets=${widgets.size}]"
	}
}
