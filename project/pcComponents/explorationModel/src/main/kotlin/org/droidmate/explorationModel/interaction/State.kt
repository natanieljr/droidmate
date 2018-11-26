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

/**
 * States have two components, the Id determined by its Widgets image, text and description and the ConfigId defined by the WidgetsProperties.
 ** be aware that the list of widgets is not guaranteed to be sorted in any specific order*/
open class State (_widgets: Collection<Widget>, val isHomeScreen: Boolean = false) {

	val stateId by lazy {
		ConcreteId(uid, configId)
	}

	val uid: UUID by lazy { lazyIds.value.uid }
	val configId: UUID by lazy { lazyIds.value.configId }

	val hasActionableWidgets by lazy{ actionableWidgets.isNotEmpty() }
	val hasEdit: Boolean by lazy { widgets.any { it.isInputField } }
	val widgets by lazy { _widgets.sortedBy { it.id.toString() } 	}

	/**------------------------------- open function default implementations ------------------------------------------**/

	open val actionableWidgets by lazy { widgets.filter { it.isInteractive } }
	open val visibleTargets by lazy { actionableWidgets.filter { it.canInteractWith	}}

	protected open val lazyIds: Lazy<ConcreteId> =
			lazy {
				widgets.fold(ConcreteId(emptyUUID, emptyUUID)) { (id, configId), widget ->
					// e.g. keyboard elements are ignored for uid computation within [addRelevantId]
					// however different selectable auto-completion proposes are only 'rendered'
					// such that we have to include the img id (part of configId) to ensure different state configuration id's if these are different
					ConcreteId(addRelevantId(id, widget), configId + widget.uid + widget.id.configId)
				}
			}


	/**
	 * We ignore keyboard elements from the unique identifier, they will be only part of this states configuration.
	 * For elements without text nlpText only the image is available which may introduce variance just due to sleigh color differences, therefore
	 * non-text elements are only considered if they can be acted upon or are leaf elements
	 */
	protected open fun isRelevantForId(w: Widget): Boolean = ( !isHomeScreen && !w.isKeyboard
			&& (w.nlpText.isNotBlank() || w.isInteractive || w.isLeaf()	)
			)


	@Suppress("SpellCheckingInspection")
	open val isAppHasStoppedDialogBox: Boolean by lazy {
		widgets.any { it.resourceId == "android:id/aerr_close" } &&
				widgets.any { it.resourceId == "android:id/aerr_wait" }
	}

	open val isRequestRuntimePermissionDialogBox: Boolean	by lazy {
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


	/** write CSV
	 *
	 * [uid] => stateId_[HS,{}] as file name (HS is only present if isHomeScreen is true)
	 */
	open fun dump(config: ModelConfig) {
		File( config.widgetFile(stateId,isHomeScreen) ).bufferedWriter().use { all ->
			all.write(StringCreator.widgetHeader(config[sep]))

			widgets.sortedBy { it.uid }.forEach {
				all.newLine()
				all.write( StringCreator.createPropertyString(it, config[sep]))
			}
		}
	}

	companion object {
		private const val resIdRuntimePermissionDialog = "com.android.packageinstaller:id/dialog_container"

		/** dummy element if a state has to be given but no widget data is available */
		@JvmStatic
		val emptyState: State by lazy { State( emptyList() ) }
	}
	/** this function is used to add any widget.uid if it fulfills specific criteria
	 * (i.e. it can be acted upon, has text nlpText or it is a leaf) */
	private fun addRelevantId(id: UUID, w: Widget): UUID = if (isRelevantForId(w)){ id + w.uid } else id

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
