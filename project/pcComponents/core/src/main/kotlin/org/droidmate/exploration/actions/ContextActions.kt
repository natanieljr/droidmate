@file:Suppress("unused", "UNUSED_PARAMETER")

package org.droidmate.exploration.actions

import org.droidmate.configuration.ConfigProperties
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.ExplorationContext

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
