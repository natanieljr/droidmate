@file:Suppress("unused")

package org.droidmate.deviceInterface.exploration

import java.io.Serializable

private var counter = 0
/** ExplorationActions which are send to the device,
 * the device control driver is then deciding how to execute them based on their type and parameters */
sealed class ExplorationAction : Serializable {
	open val name: String get() = this::class.java.simpleName
	open val hasWidgetTarget = false
	val id: Int = counter++

	fun isTerminate() = name == ActionType.Terminate.name
	fun isFetch() = name == ActionType.FetchGUI.name
}
object EmptyAction: ExplorationAction()

data class Click(val x: Int, val y: Int, override val hasWidgetTarget: Boolean = false, val delay: Long=0): ExplorationAction(){
	companion object {
		val name: String = this::class.java.declaringClass.simpleName
	}
}
fun String.isClick():Boolean = this == Click.name
data class LongClick(val x: Int, val y: Int, override val hasWidgetTarget: Boolean = false, val delay: Long=0): ExplorationAction(){
	companion object {
		val name: String = this::class.java.declaringClass.simpleName
	}
}
fun String.isLongClick():Boolean = this == LongClick.name
data class TextInsert(val idHash: Int, val text:String, override val hasWidgetTarget: Boolean = false): ExplorationAction(){
	companion object {
		val name: String = this::class.java.declaringClass.simpleName
	}
}
fun String.isTextInsert(): Boolean = this == TextInsert.name

enum class ActionType{
	PressBack, PressHome, PressEnter, CloseKeyboard, EnableWifi, MinimizeMaximize, FetchGUI, Terminate;
}
data class GlobalAction(val actionType: ActionType) : ExplorationAction(){ override val name = actionType.name }
fun String.isTerminate():Boolean = this == ActionType.Terminate.name
fun String.isPressBack():Boolean = this == ActionType.PressBack.name
fun String.isFetch():Boolean = this == ActionType.FetchGUI.name

data class RotateUI(val rotation: Int): ExplorationAction()
data class LaunchApp(val packageName: String, val launchActivityDelay: Long = 0, val timeout: Long = 10000) : ExplorationAction() {
	companion object {
		val name: String = this::class.java.declaringClass.simpleName
	}
}
fun String.isLaunchApp():Boolean = this == LaunchApp.name



fun String.isQueueStart() = this == ActionQueue.startName
fun String.isQueueEnd() = this == ActionQueue.endName
data class ActionQueue(val actions: List<ExplorationAction>, val delay: Long): ExplorationAction(){
	override fun toString(): String = "ActionQueue[ ${actions.map { it.toString()+"(${it.id})" }} ](delay=$delay)"

	companion object {
		const val name = "ActionQueue"
		const val startName = "$name-START"
		const val endName = "$name-End"
	}
}

//TODO check if this is still necessary for our tests
data class SimulationAdbClearPackage(val packageName: String) : ExplorationAction()

/**
 * Performs a swipe from one coordinate to another coordinate. You can control
 * the smoothness and speed of the swipe by specifying the number of steps.
 * Each step execution is throttled to 5 milliseconds per step, so for a 100
 * steps, the swipe will take around 0.5 seconds to complete.
 *
 * @param start  the starting coordinate
 * @param end the ending coordinate
 * @param steps is the number of steps for the swipe action
 * @since API Level 18
 */
data class Swipe(val start:Pair<Int,Int>,val end:Pair<Int,Int>,val stepSize:Int = 35, override val hasWidgetTarget: Boolean = false): ExplorationAction() {
	override fun toString(): String = "Swipe[(${start.first},${start.second}) to (${end.first},${end.second})]"
}

/**
 * NOT supported yet.
 *
 * Generates a two-pointer gesture with arbitrary starting and ending points.
 *
 * @param startPoint1 start point of pointer 1
 * @param startPoint2 start point of pointer 2
 * @param endPoint1 end point of pointer 1
 * @param endPoint2 end point of pointer 2
 * @param steps the number of steps for the gesture. Steps are injected
 * about 5 milliseconds apart, so 100 steps may take around 0.5 seconds to complete.
 * @since API Level 18
 */
@Deprecated("This action is not implemented in the device component.")
data class TwoPointerGesture(val idHash: Int, val startPoint1: Pair<Int,Int>, val startPoint2: Pair<Int,Int>,
                             val endPoint1: Pair<Int,Int>, val endPoint2: Pair<Int,Int>, val steps: Int): ExplorationAction()

/**
 * NOT supported yet.

 * Performs a two-pointer gesture, where each pointer moves diagonally
 * toward the other, from the edges to the center of this UI element.
 * @param percent percentage of the object's diagonal length for the pinch gesture
 * @param steps the number of steps for the gesture. Steps are injected
 * about 5 milliseconds apart, so 100 steps may take around 0.5 seconds to complete.
 * @since API Level 18
 */
@Deprecated("This action is not implemented in the device component.")
data class PinchIn(val idHash: Int, val percent: Int, val steps: Int): ExplorationAction()
/** NOT supported yet. */
@Deprecated("This action is not implemented in the device component.")
data class PinchOut(val idHash: Int, val percent: Int, val steps: Int): ExplorationAction()

enum class Direction{
	LEFT, RIGHT, UP, DOWN;
}
/**
 * NOT supported yet.

 * Performs a scroll gesture on this object.
 *
 * @param direction The direction in which to scroll.
 * @param percent The distance to scroll as a percentage of this object's visible size.
 * @param speed The speed at which to perform this gesture in pixels per second.
 */
@Deprecated("This action is not implemented in the device component.")
data class Scroll(val idHash: Int, val direction: Direction, val percent: Float, val speed: Int): ExplorationAction()