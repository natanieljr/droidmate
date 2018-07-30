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

import org.droidmate.exploration.statemodel.Widget
import org.droidmate.uiautomator_daemon.guimodel.Action
import org.droidmate.uiautomator_daemon.guimodel.SwipeAction

@Deprecated("to be removed",replaceWith = ReplaceWith("widget.swipe()"))
open class SwipeExplorationAction
@Deprecated("click actions are always going to use coordinates in the future",ReplaceWith("widget.swipe()"))
@JvmOverloads constructor(widget: Widget,
						  private val start: Pair<Int, Int> = Pair(widget.bounds.x, widget.bounds.y + (widget.bounds.height / 2) ),
						  private val end: Pair<Int, Int> = Pair(widget.bounds.x + widget.bounds.width, widget.bounds.y + (widget.bounds.height / 2) )
): AbstractClickExplorationAction(widget){

	override val description: String
		get() = "swipe"

	override fun toShortString(): String = "SW ${widget.toShortString()}"

	override fun getClickAction(widget: Widget): Action {
		return SwipeAction(start, end)
	}

	override fun getCoordinateClickAction(x: Int, y: Int): Action {
		return SwipeAction(start, end)
	}
}