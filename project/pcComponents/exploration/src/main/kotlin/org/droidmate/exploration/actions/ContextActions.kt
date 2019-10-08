@file:Suppress("unused", "UNUSED_PARAMETER")

package org.droidmate.exploration.actions

import org.droidmate.configuration.ConfigProperties
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.ExplorationContext
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.interaction.navigateTo

/**
 * These are the new interface functions to interact with the overall screen
 * The implementation of the actions itself is going to be refactored in the new version and all
 * old ExplorationActions are going to be removed.
 * Instead we are going to have :
 * ExplorationContext and Widgets Actions (via extension function)
 * + a LaunchApp action + ActionQue to handle a set of actions which is executed on the device before fetching a new state
 */

@Deprecated("no context required", replaceWith = ReplaceWith("ExplorationAction.minimizeMaximize()"))
fun ExplorationContext<*,*,*>.minimizeMaximize(): ExplorationAction = GlobalAction(ActionType.MinimizeMaximize)
@Deprecated("no context required", replaceWith = ReplaceWith("ExplorationAction.pressBack()"))
fun ExplorationContext<*,*,*>.pressBack(): ExplorationAction = GlobalAction(ActionType.PressBack)

fun ExplorationAction.Companion.minimizeMaximize() = GlobalAction(ActionType.MinimizeMaximize)
fun ExplorationAction.Companion.pressBack() =	GlobalAction(ActionType.PressBack)
fun ExplorationAction.Companion.closeAndReturn() =
	ActionQueue(listOf(GlobalAction(ActionType.CloseKeyboard),GlobalAction(ActionType.PressBack)),100)

/**
 * Sets the device rotation. (rotating the device changes its rotation state).
 *
 * @param rotation The value on how much the screen is supposed to be rotated based on its current orientation.
 *  This value should be dividable by 90. *
 */
@Deprecated("no context required", replaceWith = ReplaceWith("ExplorationAction.rotate()"))
fun ExplorationContext<*,*,*>.rotate(rotation: Int): ExplorationAction = RotateUI(rotation)
fun ExplorationAction.rotate(rotation: Int) = RotateUI(rotation)

/**
 * Performs a swipe from one coordinate to another using the number of steps
 * to determine smoothness and speed. Each step execution is throttled to 5ms
 * per step. So for a 100 steps, the swipe will take about 1/2 second to complete.
 *
 * @param steps is the number of move steps sent to the system
 */
@Deprecated("interface improvement", replaceWith = ReplaceWith("ExplorationAction.swipe(start,end,steps)"))
fun ExplorationContext<*,*,*>.swipe(start: Pair<Int,Int>,end:Pair<Int,Int>,steps:Int=35): ExplorationAction = Swipe(start, end, steps)

/**
 * Create a list of actions which is sequentially executed on the device without any fetch in-between.
 * the parameter [delay] specifies how long to idle until the next action of the queue should be executed.
 * If the queue contains LaunchApp action, the app will be terminated even before executing any action in the queue.
 * Therefore you should only use it as the very first action of the queue or in combination with
 * non-app-specific actions like enable-WiFi.
 */
@Deprecated("interface improvement", replaceWith = ReplaceWith("ExplorationAction.queue(actions,delay)"))
@JvmOverloads fun ExplorationContext<*,*,*>.queue(actions: List<ExplorationAction>,
                                                  delay:Long=0,
                                                  screenshotForEach: Boolean =false) = ActionQueue(actions, delay, screenshotForEach)
fun ExplorationAction.Companion.queue(actions: List<ExplorationAction>,
                                      delay:Long=0,
                                      screenshotForEach: Boolean =false) = ActionQueue(actions, delay, screenshotForEach)

//TODO enableWifi takes ~11s therefore we may consider to only do it once on exploration start instead
fun ExplorationContext<*,*,*>.launchApp(): ExplorationAction = ExplorationAction.launchApp(apk.packageName, cfg[ConfigProperties.Exploration.launchActivityDelay])
fun ExplorationAction.Companion.launchApp(packageName: String, launchDelay: Long) = queue(listOf(LaunchApp(packageName, launchDelay),
	GlobalAction(ActionType.EnableWifi),
	GlobalAction(ActionType.CloseKeyboard)))

fun ExplorationContext<*,*,*>.resetApp(): ExplorationAction = ExplorationAction.resetApp(apk.packageName, cfg[ConfigProperties.Exploration.launchActivityDelay])
fun ExplorationAction.Companion.resetApp(packageName: String, launchDelay: Long) = LaunchApp(packageName, launchDelay)

@Deprecated("interface improvement", replaceWith = ReplaceWith("ExplorationAction.terminateApp()"))
fun terminateApp(): ExplorationAction = GlobalAction(ActionType.Terminate)
fun ExplorationAction.Companion.terminateApp() = GlobalAction(ActionType.Terminate)

fun ExplorationContext<*,*,*>.navigateTo(w: Widget, action: (Widget) -> ExplorationAction): ExplorationAction?
	= getCurrentState().navigateTo(w,action)

