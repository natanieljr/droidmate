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

import org.droidmate.deviceInterface.exploration.PType
import org.droidmate.deviceInterface.exploration.Persistent
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.DeactivatableFlag
import org.droidmate.explorationModel.DummyProperties
import org.droidmate.explorationModel.toUUID
import java.util.*

open class Widget internal constructor(properties: UiElementPropertiesI,
                                       val parentId: ConcreteId?): UiElementPropertiesI {

	override val metaInfo: List<String> = properties.metaInfo

	/** @see computeUId */
	val uid: UUID get() = id.uid

	/** id to characterize the current 'configuration' of an element, e.g. is it definedAsVisible, checked etc
	 * @see computePropertyId */
	val configId: UUID get() = id.configId

	/** A widget mainly consists of two parts, [uid] encompasses the identifying one [image,Text,Description] used for unique identification
	 * and the modifiable properties, like checked, focused etc. identified via [configId] (and possibly [imgId])
	 * @see computeConcreteId
	 */
	@property:Persistent("Unique Id",0, PType.ConcreteId)
	val id by lazy { computeConcreteId() }

	/** This property determines if we could interact with this element, however it may be currently out of screen,
	 * such that we need to navigate to it firs.
	 * To know if we can directly interact with this widget right now check [canInteractWith].
	 */
	val isInteractive: Boolean by lazy { computeInteractive() }

	/** True, if this widget is interactive and currently visible on the screen **/
	val canInteractWith: Boolean by lazy { isVisible && isInteractive }

	@Suppress("MemberVisibilityCanBePrivate")
	val isVisible by lazy{ visibleBounds.isNotEmpty() }

	val hasParent get() = parentHash != 0

	/**------------------------------- open function default implementations ------------------------------------------**/

	open val nlpText: String by lazy { "$hintText $text $contentDesc".replace("\\s+", " ").splitOnCaseSwitch().trim() }

	open fun isLeaf(): Boolean = childHashes.isEmpty()

	protected open fun computeInteractive(): Boolean =
			enabled && definedAsVisible 	&& ( isInputField || clickable || checked ?: false || longClickable || scrollable)

	/**
	 * @see computeUId
	 * @see computePropertyId
	 */
	protected open fun computeConcreteId(): ConcreteId = ConcreteId(computeUId(), computePropertyId())

	/** compute the widget.uid based on its visible natural language content/resourceId if it exists, or based on [uidString] otherwise */
	protected open fun computeUId():UUID =	when {
		!isKeyboard && isInputField -> when { 	// special care for EditText elements, as the input text will change the [text] property
			contentDesc.isNotBlank() -> contentDesc.toUUID()
			resourceId.isNotBlank() -> resourceId.toUUID()
			else -> uidString.toUUID()
		}
		!isKeyboard && nlpText.isNotBlank() -> { // compute id from textual nlpText if there is any
			val ignoreNumbers = nlpText.replace("[0-9]", "")
			if (ignoreNumbers.isNotEmpty()) ignoreNumbers.toUUID()
			else nlpText.toUUID()
		}
		else -> // we have an Widget without any visible text
			if(resourceId.isNotBlank()) resourceId.toUUID()
			else uidString.toUUID()
	}
	// used if we have no NLP text available
	private val uidString by lazy{ listOf(className, packageName, isPassword, isKeyboard, idHash).joinToString(separator = "<;>") }

	/** compute the configuration of this Widget by a subset of its properties.
	 */
	protected open fun computePropertyId(): UUID {
		val relevantProperties = listOf(enabled, definedAsVisible, visibleBounds, text, checked, focused, selected, clickable, longClickable,
				scrollable, isInputField, imgId, xpath, idHash, childHashes) // REMARK we need the xpath/idHash here because this information is not encoded in the uid IF we have some text or resourceId, but on state parsing we need the correct idHash/parentHash to reconstruct the Widget object, this is currently expressed via config id but could be handled by changing the widget-parser queue as well
		return relevantProperties.joinToString("<;>").toUUID()
	}


	companion object {
		/** used for dummy initializations, if nullable is undesirable */
		val emptyWidget by lazy{ Widget(DummyProperties,null) }

		/**------------------------------------------ private methods ---------------------------------------------------**/
		private fun String.splitOnCaseSwitch(): String{
			if(this.isBlank()) return ""
			var newString = ""
			this.forEachIndexed { i, c ->
				newString += when{
					!c.isLetter() -> " "
					c.isUpperCase() && i>0 && this[i-1].isLowerCase() -> " $c"
					else -> c
				}
			}
			return newString
		}
	}

	/*** overwritten functions ***/
	override fun equals(other: Any?): Boolean {
		return when (other) {
			is Widget -> id == other.id
			else -> false
		}
	}

	override fun hashCode(): Int {
		return id.hashCode()
	}

	private val simpleClassName by lazy { className.substring(className.lastIndexOf(".") + 1) }
	override fun toString(): String {
		return "interactive=$isInteractive-${uid}_$configId:$simpleClassName[text=$text; contentDesc=$contentDesc, resourceId=$resourceId, $visibleBounds]"
	}

	/**----------------------------------- final properties from ui extraction -----------------------------------------*/

	final override val imgId: Int = properties.imgId
	final override val visibleBounds: org.droidmate.deviceInterface.exploration.Rectangle = properties.visibleBounds
	final override val isKeyboard: Boolean = properties.isKeyboard
	final override val inputType: Int = properties.inputType
	final override val text: String = properties.text
	final override val hintText: String = properties.hintText
	final override val contentDesc: String = properties.contentDesc
	final override val checked: DeactivatableFlag = properties.checked
	final override val resourceId: String = properties.resourceId
	final override val className: String = properties.className
	final override val packageName: String = properties.packageName
	final override val enabled: Boolean = properties.enabled
	final override val isInputField: Boolean = properties.isInputField
	final override val isPassword: Boolean = properties.isPassword
	final override val clickable: Boolean = properties.clickable
	final override val longClickable: Boolean = properties.longClickable
	final override val scrollable: Boolean = properties.scrollable
	final override val focused: DeactivatableFlag = properties.focused
	final override val selected: Boolean = properties.selected
	final override val boundaries = properties.boundaries
	final override val visibleAreas: List<org.droidmate.deviceInterface.exploration.Rectangle> = properties.visibleAreas
	final override val xpath: String = properties.xpath
	final override val idHash: Int = properties.idHash
	final override val parentHash: Int = properties.parentHash
	final override val childHashes: List<Int> = properties.childHashes
	final override val definedAsVisible: Boolean = properties.definedAsVisible
	final override val hasUncoveredArea: Boolean = properties.hasUncoveredArea
	/** end immutable ui-element properties */
	/* end override */

}
