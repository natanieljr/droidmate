// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2017 Konrad Jamrozik
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
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org

package org.droidmate.exploration.actions

import org.droidmate.device.datatypes.IWidget
import org.droidmate.device.datatypes.Widget
import java.io.Serializable

abstract class ExplorationAction(val type: ActionType) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1

        @JvmStatic
        @JvmOverloads
        fun newResetAppExplorationAction(isFirst: Boolean = false, actionType: ActionType = ActionType.Reset): ResetAppExplorationAction = ResetAppExplorationAction(isFirst, actionType)

        @JvmStatic
        @JvmOverloads
        fun newTerminateExplorationAction(actionType: ActionType = ActionType.Terminate): TerminateExplorationAction = TerminateExplorationAction(actionType)

        @JvmStatic
        @JvmOverloads
        fun newWidgetExplorationAction(widget: IWidget, delay: Int, useCoordinates: Boolean = true, actionType: ActionType = ActionType.Explore): WidgetExplorationAction = WidgetExplorationAction(widget, false, useCoordinates, delay, actionType = actionType).apply { runtimePermission = false }

        @JvmStatic
        @JvmOverloads
        fun newWidgetExplorationAction(widget: IWidget, useCoordinates: Boolean = true, longClick: Boolean = false, actionType: ActionType = ActionType.Explore): WidgetExplorationAction = WidgetExplorationAction(widget, longClick, useCoordinates, actionType = actionType)

        @JvmStatic
        @JvmOverloads
        fun newIgnoreActionForTerminationWidgetExplorationAction(widget: IWidget, useCoordinates: Boolean = true, longClick: Boolean = false, actionType: ActionType = ActionType.Explore): WidgetExplorationAction = WidgetExplorationAction(widget, useCoordinates, longClick, actionType = actionType).apply { runtimePermission = true }

        @JvmStatic
        @JvmOverloads
        fun newEnterTextExplorationAction(textToEnter: String, resId: String, xPath: String = "", actionType: ActionType = ActionType.EnterText): EnterTextExplorationAction = EnterTextExplorationAction(textToEnter, Widget().apply { resourceId = resId; xpath = xPath }, actionType = actionType)

        @JvmStatic
        @JvmOverloads
        fun newEnterTextExplorationAction(textToEnter: String, widget: IWidget, actionType: ActionType = ActionType.EnterText): EnterTextExplorationAction = EnterTextExplorationAction(textToEnter, widget, actionType = actionType)

        @JvmStatic
        @JvmOverloads
        fun newPressBackExplorationAction(actionType: ActionType = ActionType.Back): PressBackExplorationAction = PressBackExplorationAction(actionType)

        @JvmStatic
        @JvmOverloads
        fun newWidgetSwipeExplorationAction(widget: IWidget, useCoordinates: Boolean = true, direction: Direction, actionType: ActionType = ActionType.Explore): WidgetExplorationAction {
            return WidgetExplorationAction(widget, false, useCoordinates, 0, true, direction, actionType)
        }
    }

    protected var runtimePermission: Boolean = false

    override fun toString(): String = "<ExplAct ${toShortString()}>"

    open fun isEndorseRuntimePermission(): Boolean
            = runtimePermission

    abstract fun toShortString(): String

    open fun toTabulatedString(): String
            = toShortString()

    override fun equals(other: Any?): Boolean = this.toString() == other?.toString() ?: ""

    override fun hashCode(): Int {
        return this.runtimePermission.hashCode()
    }
}
