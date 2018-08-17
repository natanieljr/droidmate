@file:Suppress("unused")

package org.droidmate.deviceInterface.guimodel

import java.io.Serializable

/** ExplorationActions which are send to the device,
 * the device control driver is then deciding how to execute them based on their type and parameters */
sealed class ExplorationAction : Serializable{
	open val name: String get() = this::class.java.simpleName
	open val hasWidgetTarget = false

	fun isTerminate() = name == ActionType.Terminate.name
	fun isFetch() = name == ActionType.FetchGUI.name
}
object EmptyAction: ExplorationAction()

data class Click(val x: Int, val y: Int, override val hasWidgetTarget: Boolean = false, val delay: Long=0): ExplorationAction(){
	companion object {
		const val name = "Click"
	}
}
fun String.isClick():Boolean = this == Click.name
data class LongClick(val x: Int, val y: Int, override val hasWidgetTarget: Boolean = false, val delay: Long=0): ExplorationAction(){
	companion object {
		const val name = "LongClick"
	}
}
fun String.isLongClick():Boolean = this == LongClick.name
data class TextInsert(val idHash: Int, val text:String, override val hasWidgetTarget: Boolean = false): ExplorationAction()

enum class ActionType{
	PressBack, PressHome, PressEnter, CloseKeyboard, EnableWifi, MinimizeMaximize, FetchGUI, Terminate;
}
data class GlobalAction(val actionType: ActionType) : ExplorationAction(){ override val name = actionType.name }
fun String.isTerminate():Boolean = this == ActionType.Terminate.name
fun String.isPressBack():Boolean = this == ActionType.PressBack.name
fun String.isFetch():Boolean = this == ActionType.FetchGUI.name

data class RotateUI(val rotation: Int): ExplorationAction()
data class LaunchApp(val appLaunchIconName: String, val timeout: Long = 10000) : ExplorationAction(){
	companion object {
		const val name = "LaunchApp"
	}
}
fun String.isLaunchApp():Boolean = this == LaunchApp.name

data class Swipe(val start:Pair<Int,Int>,val end:Pair<Int,Int>,val stepSize:Int, override val hasWidgetTarget: Boolean = false): ExplorationAction() {
	override fun toString(): String = "Swipe[(${start.first},${start.second}) to (${end.first},${end.second})]"
}

fun String.isQueueStart() = this == ActionQueue.startName
fun String.isQueueEnd() = this == ActionQueue.endName
data class ActionQueue(val actions: List<ExplorationAction>,val delay: Long): ExplorationAction(){
	override fun toString(): String = "ActionQueue[ ${actions.map { it.toString() }} ](delay=$delay)"

	companion object {
		const val name = "ActionQueue"
		const val startName = "$name-START"
		const val endName = "$name-End"
	}
}

//TODO check if this is still necessary for our tests
data class SimulationAdbClearPackage(val packageName: String) : ExplorationAction()
