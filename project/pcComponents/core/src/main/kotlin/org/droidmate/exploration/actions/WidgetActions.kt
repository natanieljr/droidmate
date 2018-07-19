@file:Suppress("unused", "DEPRECATION", "UNUSED_PARAMETER")

package org.droidmate.exploration.actions

import org.droidmate.deviceInterface.guimodel.*
import org.droidmate.exploration.statemodel.Widget

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
 * The whidget has to be clickable and enabled. If it is not visible this method will throw an exception
 * (you should use [navigateTo] instead).
 */
fun Widget.click(): ExplorationAction{
	widgetTargets.add(this)
	return clickCoordinate().let{ (x,y) -> Click(x, y,true) }
}
fun Widget.longClick(): ExplorationAction{
	widgetTargets.add(this)
	return clickCoordinate().let{ (x,y) -> LongClick(x, y,true) }
}
fun Widget.setText(newContent: String): ExplorationAction{
	widgetTargets.add(this)
	return  TextInsert(this.idHash,newContent,true)
}
fun Widget.dragTo(x:Int,y:Int,stepSize:Int):ExplorationAction = TODO()
fun Widget.swipeUp(stepSize: Int):ExplorationAction = TODO()
fun Widget.swipeDown(stepSize: Int):ExplorationAction = TODO()
fun Widget.swipeLeft(stepSize: Int):ExplorationAction = TODO()
fun Widget.swipeRight(stepSize: Int):ExplorationAction = TODO()

/** navigate to this widget (which may be currently out of screen) and click it */
fun Widget.navigateTo():ExplorationAction = TODO()

/**
 * Used by RobustDevice which does not currently parse Widgets.
 * This function should not be used anywhere else.
 */
fun WidgetData.click(): ExplorationAction {
	val x = center(boundsX, boundsWidth)
	val y = center(boundsY, boundsHeight)
	return Click(x, y)
}
private fun Widget.clickCoordinate(): Pair<Int,Int> =
	uncoveredCoord ?: Pair(bounds.centerX.toInt(),bounds.centerY.toInt())


