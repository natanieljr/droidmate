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
@file:Suppress("unused")

package org.droidmate.uiautomator_daemon.guimodel

// Do not include anything from java.awt.* because of incompatibility reasons
import java.io.Serializable

/**
 * Created by J.H. on 05.02.2018.
 */
sealed class Action : Serializable

enum class WidgetSelector {
	ResourceId,
	ClassName,
	ContentDesc,
	XPath
}

@Deprecated("is internally the same as CoordinateClick to the center -> is going to be removed in next version",
		replaceWith = ReplaceWith("CoordinateClickAction(x,y)"))
data class ClickAction(val xPath: String,
                       val resId: String = "") : Action()

data class CoordinateClickAction(val x: Int,
                                 val y: Int) : Action() {
}

@Deprecated("is internally the same as CoordinateClick to the center -> is going to be removed in next version",
		replaceWith = ReplaceWith("CoordinateLongClickAction(x,y)"))
data class LongClickAction(val xPath: String, val resId: String = "") : Action()

data class CoordinateLongClickAction(val x: Int,
                                     val y: Int) : Action() {
}

data class TextAction(val xPath: String, val resId: String = "", val text: String) : Action()

@Deprecated("this is going to be removed when fetch-synchronization proves stable")
data class WaitAction(val target: String, val criteria: WidgetSelector) : Action()

class SwipeAction private constructor(val start: Pair<Int, Int>? = null,
                                      val dst: Pair<Int, Int>? = null, val xPath: String = "",
                                      val direction: String = "") : Action() {
	constructor(_start: Pair<Int, Int>, _dst: Pair<Int, Int>) : this(start = _start, dst = _dst)
	constructor(xPath: String, _direction: String) : this(xPath = xPath, direction = _direction)
}

object PressBackAction : Action()
object PressHomeAction : Action()
object EnableWifiAction : Action()
object MinimizeMaximizeAction : Action()
object FetchGUiAction: Action()

data class RotateUIAction(val rotation: Int): Action()

data class LaunchAppAction(val appLaunchIconName: String, val launchActivityDelay: Long) : Action()

data class SimulationAdbClearPackageAction(val packageName: String) : Action()

data class MultiAction(val actions: List<Action>): Action()