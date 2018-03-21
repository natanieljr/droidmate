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

import org.droidmate.device.datatypes.Widget
import org.droidmate.device.datatypes.WidgetData
import java.io.Serializable

//TODO("this should be refactored into IRunnableAction")
abstract class ExplorationAction : Serializable {
	open val widget: Widget? = null

    companion object {
        private const val serialVersionUID: Long = 1

        @JvmStatic
        @JvmOverloads
        fun newResetAppExplorationAction(isFirst: Boolean = false): ResetAppExplorationAction = ResetAppExplorationAction(isFirst)

        @JvmStatic
        fun newTerminateExplorationAction(): TerminateExplorationAction = TerminateExplorationAction()

        @JvmStatic
        @JvmOverloads
        fun newWidgetExplorationAction(widget: Widget, delay: Int, useCoordinates: Boolean = false): WidgetExplorationAction = WidgetExplorationAction(widget, false, useCoordinates, delay).apply { runtimePermission = false }

        @JvmStatic
        @JvmOverloads
        fun newWidgetExplorationAction(widget: Widget, useCoordinates: Boolean = false, longClick: Boolean = false): WidgetExplorationAction = WidgetExplorationAction(widget, longClick, useCoordinates)

        @JvmStatic
        @JvmOverloads
        fun newIgnoreActionForTerminationWidgetExplorationAction(widget: Widget, useCoordinates: Boolean = false, longClick: Boolean = false): WidgetExplorationAction = WidgetExplorationAction(widget, useCoordinates, longClick).apply { runtimePermission = true }

        @JvmStatic
        @JvmOverloads
        @Suppress("unused")
        fun newEnterTextExplorationAction(textToEnter: String, resId: String, xPath: String = ""): EnterTextExplorationAction = EnterTextExplorationAction(textToEnter, Widget(WidgetData(resId = resId, xPath = xPath )))

        @JvmStatic
        fun newEnterTextExplorationAction(textToEnter: String, widget: Widget): EnterTextExplorationAction = EnterTextExplorationAction(textToEnter, widget)

        @JvmStatic
        fun newPressBackExplorationAction(): PressBackExplorationAction = PressBackExplorationAction()

        @JvmStatic
        @JvmOverloads
        @Suppress("unused")
        fun newWidgetSwipeExplorationAction(widget: Widget, useCoordinates: Boolean = true, direction: Direction): WidgetExplorationAction {
            return WidgetExplorationAction(widget, false, useCoordinates, 0, true, direction)
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
