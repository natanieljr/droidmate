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

package org.droidmate.explorationModel.interaction

import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.dump.sep
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.plus
import org.droidmate.explorationModel.retention.StringCreator
import java.io.File
import java.util.*

//TODO cleanup
/**
 * States have two components, the Id determined by its Widgets image, text and description and the ConfigId defined by the WidgetsProperties.
 ** be aware that the list of widgets is not guaranteed to be sorted in any specific order*/
open class State (_widgets: Lazy<Collection<Widget>>, //TODO instead we probably want widgets as deferred value
                 //val displayedWindows: List<AppWindow> = emptyList(), // for now we do not need it, so we will probably remove it from DeviceResponse
                  val isHomeScreen: Boolean = false) {

	constructor(widgets: Collection<Widget>, homeScreen:Boolean) : this(lazyOf(widgets),
			isHomeScreen=homeScreen)

	val widgets by lazy { _widgets.value.sortedBy { it.id.toString() } 	}

	@Suppress("SpellCheckingInspection")
	val isAppHasStoppedDialogBox: Boolean by lazy {
		widgets.any { it.resourceId == "android:id/aerr_close" } &&
				widgets.any { it.resourceId == "android:id/aerr_wait" }
	}

	val isRequestRuntimePermissionDialogBox: Boolean	by lazy {
		widgets.any { // identify if we have a permission request
			it.resourceId == resIdRuntimePermissionDialog  || // maybe it is safer to check for packageName 'com.google.android.packageinstaller' only?
					// handle cases for apps who 'customize' this request and use own resourceIds e.g. Home-Depot
					when(it.text.toUpperCase()) {
						"ALLOW", "DENY", "DON'T ALLOW" -> true
						else -> false
					}
		}
				// check that we have a ok or allow button
				&& widgets.any{it.text.toUpperCase().let{ wText -> wText == "ALLOW" || wText == "OK" } }
	}

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
					widgets.fold(ConcreteId(emptyUUID, emptyUUID)) { (id, configId), widget ->  // e.g. keyboard elements have a different package-name and are therefore ignored for uid computation
						// however different selectable auto-completion proposes are only 'rendered' such that we have to include the img id to ensure different state configuration id's if these are different
						ConcreteId(addRelevantId(id, widget), configId + widget.uid + widget.id.configId)
					}
				}

		val uid: UUID by lazy { lazyIds.value.uid }
		val configId: UUID by lazy { lazyIds.value.configId }
		val stateId by lazy {
			ConcreteId(uid, configId)
		}
		/** id computed like uid while ignoring all edit fields */
		val iEditId: UUID by lazy {
			//lazyIds.value.third
			widgets.fold(emptyUUID) { iEdit, widget -> addRelevantNonEdit(iEdit, widget) }
		}

	//FIXME differentiate between interactive widgets and currently visible interactive widgets
		val actionableWidgets by lazy { widgets.filter { it.isInteractive } }
		val distinctTargets by lazy { actionableWidgets.filter { !it.isKeyboard && (it.isLeaf() || (it.isInteractive //&& !it.hasActableDescendant
				)) //FIXME this is a bit stricter than the uncovered coordinate -> if we need it overwrite generateWidgets function
			//|| it.uncoveredCoord!=null
		}}
		val hasEdit: Boolean by lazy { widgets.any { it.isInputField } }
//	val focusedWindows: List<String> by lazy { displayedWindows.mapNotNull { if(it.hasInputFocus) it.pkgName else null } }

		// for elements without text nlpText only the image is available which may introduce variance just due to sligh color differences, therefore
		// non-text elements are only considered if they can be acted upon and don't have actable descendents
		//TODO make this an open function, do we want to compare against apk.packageName or currently active/focused package?
		fun isRelevantForId(w: Widget): Boolean = (!isHomeScreen //&&  focusedWindows.contains(w.packageName)
				&& (w.nlpText.isNotBlank() || (w.isLeaf() && w.isInteractive) || (w.isInteractive //&& !w.hasActableDescendant //TODO right now we do not compute if there are interactive descendants
				)
				))
		/** this function is used to add any widget.uid if it fulfills specific criteria (i.e. it belongs to the app, can be acted upon, has text nlpText or it is a leaf) */
		private fun addRelevantId(id: UUID, w: Widget): UUID = if (isRelevantForId(w)){ id + w.uid } else id

		private fun addRelevantNonEdit(id: UUID, w: Widget): UUID = if (w.isInputField) addRelevantId(id, w) else id

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
			File(config.widgetFile(stateId,isHomeScreen//,topNodePackageName
			)).bufferedWriter().use { all ->
				all.write(StringCreator.widgetHeader(config[sep]))

				widgets.sortedBy { it.uid }.forEach {
					all.newLine()
					all.write( StringCreator.createPropertyString(it, config[sep]))
				}
			}
		}

		companion object {
			private const val resIdRuntimePermissionDialog = "com.android.packageinstaller:id/dialog_container"

			// this is basically extending the primary constructor
//    @JvmStatic operator fun invoke(widgets: Set<Widget>): State = State(widgets.toList()//.sortedBy { it.uid.dumpString() }
//    )

		/** dummy element if a state has to be given but no widget data is available */
		@JvmStatic
		val emptyState: State by lazy { State(lazy { emptyList<Widget>() }) }

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
				is State -> uid == other.uid && configId == other.configId
				else -> false
			}
		}

		override fun hashCode(): Int {
			return uid.hashCode() + configId.hashCode()
		}

		override fun toString(): String {
			return "State[$stateId, widgets=${widgets.size}]"
		}

}
