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

package org.droidmate.exploration.actions

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.debug.debugT
import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.deviceInterface.DeviceLogsHandler
import org.droidmate.device.deviceInterface.IRobustDevice
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.exploration.statemodel.Widget
import org.droidmate.uiautomator_daemon.guimodel.*

private var performT: Long = 0
private var performN: Int = 1

@Deprecated("click actions are always going to use coordinates in the future, and delay is already ignored right now",ReplaceWith("ClickExplorationAction(widget)"))
open class ClickExplorationAction @JvmOverloads constructor(widget: Widget,
															useCoordinates: Boolean = true,
															delay: Int = 100): AbstractClickExplorationAction(widget, useCoordinates, delay) {
	override val description: String
		get() = "click"

	override fun toShortString(): String = "CL ${widget.toShortString()}"// "SW? ${if (swipe) 1 else 0} LC? ${if (longClick) 1 else 0} " + widget.toShortString()

	override fun getClickAction(widget: Widget): Action {
		return ClickAction(widget.xpath, widget.resourceId)
	}

	override fun getCoordinateClickAction(x: Int, y: Int): Action {
		return CoordinateClickAction(x, y)
	}
}