@file:Suppress("unused", "DEPRECATION", "UNUSED_PARAMETER")

package org.droidmate.exploration.actions

import org.droidmate.deviceInterface.exploration.*
import org.droidmate.explorationModel.firstCenter
import org.droidmate.explorationModel.firstOrEmpty
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.interaction.widgetTargets

/**
 * These are the new interface functions to interact with any widget.
 * The implementation of the actions itself is going to be refactored in the new version and all
 * old ExplorationActions are going to be removed.
 * Instead we are going to have :
 * ExplorationContext and Widgets Actions (via extension function)
 * + a LaunchApp action + ActionQue to handle a set of actions which is executed on the device before fetching a new state
 */

/**
 * issue a click to [this.uncoveredCoord] if it exists and to the bounderies center otherwise.
 * The whidget has to be clickable and enabled. If it is not definedAsVisible this method will throw an exception
 * (you should use [navigateTo] instead).
 */
@JvmOverloads
fun Widget.click(delay: Long = 0, isVisible: Boolean = false): ExplorationAction {
	if (!(definedAsVisible || isVisible) || !enabled || !clickable)
		throw RuntimeException("ERROR: tried to click non-actable Widget $this")
	widgetTargets.add(this)
	return clickCoordinate().let { (x, y) -> Click(x, y, true, delay) }
}

@JvmOverloads
fun Widget.tick(isVisible: Boolean = false): ExplorationAction {
	if (!(definedAsVisible || isVisible) || !enabled)
		throw RuntimeException("ERROR: tried to tick non-actable (checkbox) Widget $this")
	widgetTargets.add(this)
	return clickCoordinate().let { (x, y) -> Click(x, y, true) }
}

@JvmOverloads
fun Widget.longClick(delay: Long = 0, isVisible: Boolean = false): ExplorationAction {
	if (!(definedAsVisible || isVisible) || !enabled || !longClickable)
		throw RuntimeException("ERROR: tried to long-click non-actable Widget $this")
	widgetTargets.add(this)
	return clickCoordinate().let { (x, y) -> LongClick(x, y, true, delay) }
}

@JvmOverloads
fun Widget.setText(newContent: String, isVisible: Boolean = false, enableValidation: Boolean = true): ExplorationAction {
	if (enableValidation && (!(definedAsVisible || isVisible) || !enabled || !isInputField))
		throw RuntimeException("ERROR: tried to enter text on non-actable Widget $this")
	widgetTargets.add(this)
	return TextInsert(this.idHash, newContent, true)
}

fun Widget.dragTo(x: Int, y: Int, stepSize: Int): ExplorationAction = TODO()
//FIXME the center points may be overlayed by other elements, swiping the corners would be safer
fun Widget.swipeUp(stepSize: Int = this.visibleAreas.firstOrEmpty().height / 2): ExplorationAction = Swipe(Pair(this.visibleBounds.center.first, this.visibleBounds.topY + this.visibleBounds.height), Pair(this.visibleBounds.center.first, this.visibleBounds.topY), stepSize, true)
fun Widget.swipeDown(stepSize: Int = this.visibleAreas.firstOrEmpty().height / 2): ExplorationAction = Swipe(Pair(this.visibleBounds.center.first, this.visibleBounds.topY), Pair(this.visibleBounds.center.first, this.visibleBounds.topY + this.visibleBounds.height), stepSize, true)
fun Widget.swipeLeft(stepSize: Int = this.visibleAreas.firstOrEmpty().width / 2): ExplorationAction = Swipe(Pair(this.visibleBounds.leftX + this.visibleBounds.width, this.visibleBounds.center.second), Pair(this.visibleBounds.leftX, this.visibleBounds.center.second), stepSize, true)
fun Widget.swipeRight(stepSize: Int = this.visibleAreas.firstOrEmpty().width / 2): ExplorationAction = Swipe(Pair(this.visibleBounds.leftX, this.visibleBounds.center.second), Pair(this.visibleBounds.leftX + this.visibleBounds.width, this.visibleBounds.center.second), stepSize, true)

/** navigate to this widget (which may be currently out of screen) and click it */
fun Widget.navigateTo(action: (Widget) -> ExplorationAction): ExplorationAction {
	if (definedAsVisible)
		return action(this)

	TODO()
}

/**
 * Used by RobustDevice which does not currently create [Widget] objects.
 * This function should not be used anywhere else.
 */
fun UiElementPropertiesI.click(): ExplorationAction = visibleAreas.firstCenter().let{ (x, y) ->
	return Click(x, y)
}

 fun Widget.clickCoordinate(): Pair<Int, Int> = visibleAreas.firstCenter()


fun Widget.availableActions(): List<ExplorationAction>{
	val actionList: MutableList<ExplorationAction> = mutableListOf()

	if (this.longClickable)
		actionList.add(this.longClick())

	if (this.clickable)
		actionList.add(this.click())

	if (this.checked != null)
		actionList.add(this.tick())

	if (this.scrollable) {
		actionList.add(this.swipeUp())
		actionList.add(this.swipeDown())
		actionList.add(this.swipeRight())
		actionList.add(this.swipeLeft())
	}

	widgetTargets.clear() // ensure the target is only once in the list and not multiple times
	widgetTargets.add(this)
	return actionList
}