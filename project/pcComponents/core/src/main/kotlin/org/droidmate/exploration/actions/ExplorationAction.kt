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
import org.droidmate.uiautomator_daemon.WidgetData
import java.io.Serializable

@Deprecated("this class will be removed in the next version, implement `AbstractExplorationAction` and direct accesses to the implementing classes instead")
abstract class ExplorationAction : Serializable {
	open val widget: Widget? = null

	companion object {
		private const val serialVersionUID: Long = 1

		@JvmStatic
		@JvmOverloads
		@Deprecated("unecessary delegation will be removed in next version",replaceWith = ReplaceWith("ResetAppExplorationAction()","org.droidmate.exploration.actions.ResetExplorationAction"))
		fun newResetAppExplorationAction(isFirst: Boolean = false): ResetAppExplorationAction = ResetAppExplorationAction(isFirst)

		@JvmStatic
		@Deprecated("unecessary delegation will be removed in next version",replaceWith = ReplaceWith("TerminateExplorationAction()","org.droidmate.exploration.actions.TerminateExplorationAction"))
		fun newTerminateExplorationAction(): TerminateExplorationAction = TerminateExplorationAction()

		@JvmStatic
		@JvmOverloads
		@Deprecated("unecessary delegation will be removed in next version",replaceWith = ReplaceWith("WidgetExplorationAction(widget, delay = delay)","org.droidmate.exploration.actions.WidgetExplorationAction"))
		fun newWidgetExplorationAction(widget: Widget, delay: Int, useCoordinates: Boolean = true): WidgetExplorationAction = WidgetExplorationAction(widget, false, useCoordinates, delay).apply { runtimePermission = false }

		@JvmStatic
		@JvmOverloads
		@Deprecated("unecessary delegation will be removed in next version",replaceWith = ReplaceWith("WidgetExplorationAction(widget)","org.droidmate.exploration.actions.WidgetExplorationAction"))
		fun newWidgetExplorationAction(widget: Widget, useCoordinates: Boolean = true, longClick: Boolean = false): WidgetExplorationAction = WidgetExplorationAction(widget, longClick, useCoordinates)

		@JvmStatic
		@JvmOverloads
		@Deprecated("unecessary delegation will be removed in next version",replaceWith = ReplaceWith("WidgetExplorationAction(widget).apply { runtimePermission = true }","org.droidmate.exploration.actions.WidgetExplorationAction"))
		fun newIgnoreActionForTerminationWidgetExplorationAction(widget: Widget, useCoordinates: Boolean = true, longClick: Boolean = false): WidgetExplorationAction = WidgetExplorationAction(widget, useCoordinates, longClick).apply { runtimePermission = true }

		@JvmStatic
		@JvmOverloads
		@Suppress("unused")
		@Deprecated("unecessary delegation will be removed in next version",replaceWith = ReplaceWith("EnterTextExplorationAction(textToEnter,resId)","org.droidmate.exploration.actions.EnterTextExplorationAction"))
		fun newEnterTextExplorationAction(textToEnter: String, resId: String, xPath: String = ""): EnterTextExplorationAction = EnterTextExplorationAction(textToEnter, Widget(WidgetData(resId = resId, xPath = xPath)))

		@JvmStatic
		@Deprecated("unecessary delegation will be removed in next version",replaceWith = ReplaceWith("EnterTextExplorationAction(textToEnter,widget)","org.droidmate.exploration.actions.EnterTextExplorationAction"))
		fun newEnterTextExplorationAction(textToEnter: String, widget: Widget): EnterTextExplorationAction = EnterTextExplorationAction(textToEnter, widget)

		@JvmStatic
		@Deprecated("unecessary delegation",replaceWith = ReplaceWith("PressBackExplorationAction()","org.droidmate.exploration.actions.PressBackExplorationAction"))
		fun newPressBackExplorationAction(): PressBackExplorationAction = PressBackExplorationAction()

		@JvmStatic
		@JvmOverloads
		@Suppress("unused")
		@Deprecated("unecessary delegation will be removed in next version",replaceWith = ReplaceWith("WidgetExplorationAction(widget, direction)","org.droidmate.exploration.actions.WidgetExplorationAction"))
		fun newWidgetSwipeExplorationAction(widget: Widget, useCoordinates: Boolean = true, direction: Direction): WidgetExplorationAction {
			return WidgetExplorationAction(widget, false, useCoordinates, 0, true, direction)
		}
	}

	var runtimePermission: Boolean = false

	override fun toString(): String = "<ExplAct ${toShortString()}>"

	open fun isEndorseRuntimePermission(): Boolean = runtimePermission

	abstract fun toShortString(): String

	open fun toTabulatedString(): String = toShortString()

	override fun equals(other: Any?): Boolean = this.toString() == other?.toString() ?: ""

	override fun hashCode(): Int {
		return this.runtimePermission.hashCode()
	}
}
