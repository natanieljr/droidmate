@file:Suppress("unused","DEPRECATION", "UNUSED_PARAMETER")

package org.droidmate.exploration.actions

import org.droidmate.exploration.ExplorationContext

		/**
 * These are the new interface functions to interact with the overall screen
 * The implementation of the actions itself is going to be refactored in the new version and all
 * old ExplorationActions are going to be removed.
 * Instead we are going to have :
 * ExplorationContext and Widgets Actions (via extension function)
 * + a LaunchApp action + ActionQue to handle a set of actions which is executed on the device before fetching a new state
 */

fun ExplorationContext.minimizeMaximize(): AbstractExplorationAction = MinimizeMaximizeExplorationAction
fun ExplorationContext.pressBack(): AbstractExplorationAction = PressBackExplorationAction()
fun ExplorationContext.resetApp(): AbstractExplorationAction = ResetAppExplorationAction() // using ActionQue to issue multiple actions

/**
 * Sets the device rotation. (rotating the device changes its rotation state).
 *
 * @param rotation The value on how much the screen is supposed to be rotated based on its current orientation.
 *  This value should be dividable by 90. *
 */
fun ExplorationContext.rotate(rotation: Int): AbstractExplorationAction = RotateUIExplorationAction(rotation)

/**
 * Performs a swipe from one coordinate to another using the number of steps
 * to determine smoothness and speed. Each step execution is throttled to 5ms
 * per step. So for a 100 steps, the swipe will take about 1/2 second to complete.
 *
 * @param steps is the number of move steps sent to the system
 */
fun ExplorationContext.swipe(start: Pair<Int,Int>,end:Pair<Int,Int>,steps:Int): AbstractExplorationAction = TODO()

fun terminateApp(): AbstractExplorationAction = TerminateExplorationAction()
