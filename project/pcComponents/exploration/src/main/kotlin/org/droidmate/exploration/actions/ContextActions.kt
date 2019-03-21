@file:Suppress("unused", "UNUSED_PARAMETER")

package org.droidmate.exploration.actions

import org.droidmate.configuration.ConfigProperties
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.ExplorationContext
import org.droidmate.explorationModel.debugOut
import org.droidmate.explorationModel.interaction.Widget
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * These are the new interface functions to interact with the overall screen
 * The implementation of the actions itself is going to be refactored in the new version and all
 * old ExplorationActions are going to be removed.
 * Instead we are going to have :
 * ExplorationContext and Widgets Actions (via extension function)
 * + a LaunchApp action + ActionQue to handle a set of actions which is executed on the device before fetching a new state
 */

fun ExplorationContext.minimizeMaximize(): ExplorationAction = GlobalAction(ActionType.MinimizeMaximize)
fun ExplorationContext.pressBack(): ExplorationAction = GlobalAction(ActionType.PressBack)

/**
 * Sets the device rotation. (rotating the device changes its rotation state).
 *
 * @param rotation The value on how much the screen is supposed to be rotated based on its current orientation.
 *  This value should be dividable by 90. *
 */
fun ExplorationContext.rotate(rotation: Int): ExplorationAction = RotateUI(rotation)

/**
 * Performs a swipe from one coordinate to another using the number of steps
 * to determine smoothness and speed. Each step execution is throttled to 5ms
 * per step. So for a 100 steps, the swipe will take about 1/2 second to complete.
 *
 * @param steps is the number of move steps sent to the system
 */
fun ExplorationContext.swipe(start: Pair<Int,Int>,end:Pair<Int,Int>,steps:Int=35): ExplorationAction = Swipe(start, end, steps)

/**
 * Create a list of actions which is sequentially executed on the device without any fetch in-between.
 * the parameter [delay] specifies how long to idle until the next action of the queue should be executed.
 * If the queue contains LaunchApp action, the app will be terminated even before executing any action in the queue.
 * Therefore you should only use it as the very first action of the queue or in combination with
 * non-app-specific actions like enable-WiFi.
 */
@JvmOverloads fun ExplorationContext.queue(actions: List<ExplorationAction>, delay:Long=0) = ActionQueue(actions, delay)

//TODO enableWifi takes ~11s therefore we may consider to only do it once on exploration start instead
fun ExplorationContext.resetApp(): ExplorationAction {
    // Using ActionQue to issue multiple actions
    return queue(listOf(LaunchApp(apk.packageName, cfg[ConfigProperties.Exploration.launchActivityDelay]), GlobalAction(ActionType.EnableWifi)))
}
fun terminateApp(): ExplorationAction = GlobalAction(ActionType.Terminate)

/** navigate to widget [w] (which may be currently out of screen) and act upon it by calling [action].
 * This may not work for all apps when the element is located above the current snapshot, i.e. some web-view apps
 * do not report the real element boundaries but rather put others in front,
 * for these we have no way to compute how often to scroll into which direction.
 *
 * 1) if the element is not visible, we traverse the parent structure until we find a visible ancestor (wa)
 * 2) the visible bounds of this ancestor will determine the region we can use to swipe within
 * 3) compute the number of swipes necessary to 'scroll' the element into the visible area
 * 4) compute the 'new' boundary parameters for w and create a new widget (nw) with the properties as w is going to have them after the scroll actions
 * 5) create an action queue with these swipes + the result from @param action(nw)
 *
 * REMARK if keyboard open some buttons may simply not be scrollable to, but this should actually  not be an issue as long as our strategy does not click input fields
 */
fun ExplorationContext.navigateTo(w: Widget, action: (Widget) -> ExplorationAction): ExplorationAction? {
	if (w.isVisible)
		return action(w)

	val widgetMap: Map<Int, Widget> = getCurrentState().widgets.associateBy { it.idHash }
	var wa: Widget? = null
	var waId: Int = w.parentHash
	var hasAncestor = w.hasParent
	while(hasAncestor && wa==null){
		widgetMap[waId].let { a: Widget? ->
			when{
				a == null -> {
					println("ERROR invalid state could not find parentHash $waId within this state ${getCurrentState().stateId}")
					return null
				}
				a.isVisible // check that we do not try to swipe on a tiny area and require a huge ammount of swipes
						&& a.visibleBounds.height>200 && a.visibleBounds.width>200 -> wa = a
				else -> {
					hasAncestor = a.hasParent
					waId = a.parentHash
				}
			}
		}
	}
	if(wa == null){
		debugOut("INFO cannot navigate to widget ${w.id} as it has no visible parent")
		return null
	}
	if(wa!!.visibleBounds.contains(w.boundaries)){  // elements may be hidden behind their cousins on purpose
		debugOut("INFO cannot navigate to widget ${w.id} since it is already in the visible area of its ancestor")
		return null
	}

	// we have found a visible ancestor (wa) (1)
	(wa!!.visibleBounds).let{ area ->// determine the target coordinate (ty,tx) we want to scroll into view (upper-left/lower-right corner)
		val (dy,ny) = when{
			w.boundaries.topY in area.topY .. area.bottomY -> Pair(0, w.boundaries.topY)  // is already (partially) visible
			w.boundaries.topY > area.bottomY -> // the target is below the visible area
				Pair(w.boundaries.bottomY-area.bottomY,area.bottomY-w.boundaries.height) // take the lower corner of the target widget and the new y is the upper left corner
			else ->
				Pair(-(area.topY-w.boundaries.topY), area.topY) // target is above the visible area, take the upper corner of the widget
		}
		val (dx,nx) =when{
			w.boundaries.leftX in area.leftX .. area.rightX -> Pair(0, w.boundaries.leftX)  // is already (partially) visible
			w.boundaries.leftX > area.rightX -> Pair(w.boundaries.rightX-area.rightX, area.rightX-w.boundaries.width) // the element is right from the visible area
			else -> Pair(-(area.leftX-w.boundaries.leftX), area.leftX)
		}


		val actions = swipe(dx,dy,area)
		val newBounds = Rectangle(nx,ny,w.boundaries.width,w.boundaries.height) // the position the widget should have after executing all swipes below
		val newVisibleBounds = Rectangle(nx,ny, min(w.boundaries.width, area.rightX-nx), min(w.boundaries.height, area.bottomY-ny))
		val newPosWidget = w.copy(boundaries = newBounds, visibleBounds = newVisibleBounds, defVisible=true)
		// add the action on the (modified) widget
		actions.add( action(newPosWidget) )
		return ActionQueue(actions, delay = 1000)
	}
}

/** this is a helping function to determine the number of swipes to have an overall swipe distance in width [distX] and height [distY]
 * remark: swipe directions are a bit un-intuitive, e.g. swipe((0,0),(100,100)) will swipe UP and not down
 * use negative values of [distX], [distY] to swipe left, up
 * @param swipeOffset some apps have custom menu bars if we start the swipe on these the swipe will not work, for the end-coordinate that is no issue we can even leave the app area
 *
 * Some apps (e.g. amazon) hide elements with small high just outside of the visible area and change their properties when the user scrolls into the right direction.
 * This method does not work for these cases, the only thing we could do is scroll and fetch until it is in the visible area,
 * or maybe more generally check if there are out of area elements and if so try to scroll into their direction once (
 * we probably would have to keep track of which elements we already saw).
 */
private fun ExplorationContext.swipe(distX:Int, distY:Int, area:Rectangle, swipeOffset: Int = 2):LinkedList<ExplorationAction>{
	val actions = LinkedList<ExplorationAction>()
	val (middleX,middleY) = area.center
	val sxOffset=100 // for right scrolling to prevent the "open menu" feature, implemented in some apps

	// mDistX/Y is the maximal distance we can scroll into X/Y direction with the respective integer sign
	// sx and sy are the start point for the swipe
	val (sx,mDistX) = with(area) {
		when {
			distX == 0 -> Pair(middleX, 0) // if we do not have swipe right/left we start the swipe in the middle of the x coordinate
			distX < 0 -> Pair(leftX + sxOffset, -(max(0,width-sxOffset))) // swipe from left to right (reveal left elements)
			else -> Pair(width - swipeOffset, width-swipeOffset)
		}
	}
	val (sy,mDistY) = with(area) {
		when {
			distY == 0 -> Pair(middleY,0)
			distY < 0 -> Pair(topY + swipeOffset, -(height-swipeOffset)) // swipe from top to bottom (reveal upper elements)
			else -> Pair(height - swipeOffset, height-swipeOffset)
		}
	}

	var dx = 0  // the distance we already swiped on the x/y axis
	var dy = 0
	//mDistX and mDistY are positive if we have to swipe left | up and negative for right | down
	while ((mDistX!=0 || mDistY!=0) && (dx!=distX || dy!=distY)){
		val mx = (distX-dx).let{ distToX -> if(abs(distToX) < abs(mDistX)) distToX else mDistX}
		dx += mx
		val my = (distY-dy).let{ distToy -> if(abs(distToy) < abs(mDistY)) distToy else mDistY}
		dy += my
		actions.add(this.swipe(Pair(sx,sy), Pair(sx-mx,sy-my), steps = 10))
	}

	return actions
}