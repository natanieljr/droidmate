@file:Suppress("unused", "DEPRECATION", "UNUSED_PARAMETER")

package org.droidmate.exploration.actions

import org.droidmate.exploration.statemodel.Widget
import org.droidmate.uiautomator_daemon.guimodel.*

/**
 * These are the new interface functions to interact with any widget.
 * The implementation of the actions itself is going to be refactored in the new version and all
 * old ExplorationActions are going to be removed.
 * Instead we are going to have :
 * ExplorationContext and Widgets Actions (via extension function)
 * + a LaunchApp action + ActionQue to handle a set of actions which is executed on the device before fetching a new state
 */
fun Widget.click() = ClickExplorationAction(this)
fun Widget.getClickAction() = clickCoordinate().let{ (x,y) -> CoordinateClickAction(x,y) }
fun Widget.getLongClickAction() = clickCoordinate().let{ (x,y) -> CoordinateLongClickAction(x,y) }
fun Widget.longClick() = LongClickExplorationAction(this)
fun Widget.setText(newContent: String) = EnterTextExplorationAction(newContent,this)
fun Widget.dragTo(x:Int,y:Int,stepSize:Int):AbstractExplorationAction = TODO()
fun Widget.swipeUp(stepSize: Int):AbstractExplorationAction = TODO()
fun Widget.swipeDown(stepSize: Int):AbstractExplorationAction = TODO()
fun Widget.swipeLeft(stepSize: Int):AbstractExplorationAction = TODO()
fun Widget.swipeRight(stepSize: Int):AbstractExplorationAction = TODO()

/** used by RobustDevice which does not currently parse Widgets */
fun WidgetData.click(): Action {
	val x = center(boundsX, boundsWidth)
	val y = center(boundsY, boundsHeight)
	return CoordinateClickAction(x,y)
}
private fun Widget.clickCoordinate(): Pair<Int,Int> =
	uncoveredCoord ?: Pair(bounds.centerX.toInt(),bounds.centerY.toInt())


