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

abstract class ExplorationAction : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1

        @JvmStatic
        @JvmOverloads
        fun newResetAppExplorationAction(isFirst: Boolean = false): ResetAppExplorationAction
                = ResetAppExplorationAction(isFirst)

        @JvmStatic
        fun newTerminateExplorationAction(): TerminateExplorationAction
                = TerminateExplorationAction()

        @JvmStatic
        fun newWidgetExplorationAction(widget: IWidget, delay: Int): WidgetExplorationAction
                = WidgetExplorationAction(widget, false, delay).apply { runtimePermission = false }

        @JvmStatic
        fun newWidgetExplorationAction(widget: IWidget, longClick: Boolean = false): WidgetExplorationAction
                = WidgetExplorationAction(widget, longClick)

        @JvmStatic
        @JvmOverloads
        fun newIgnoreActionForTerminationWidgetExplorationAction(widget: IWidget, longClick: Boolean = false): WidgetExplorationAction
                = WidgetExplorationAction(widget, longClick).apply { runtimePermission = true }

        @JvmStatic
        fun newEnterTextExplorationAction(textToEnter: String, resId: String): EnterTextExplorationAction
                = EnterTextExplorationAction(textToEnter, Widget().apply { resourceId = resId })

        @JvmStatic
        fun newEnterTextExplorationAction(textToEnter: String, widget: IWidget): EnterTextExplorationAction
                = EnterTextExplorationAction(textToEnter, widget)

        @JvmStatic
        fun newPressBackExplorationAction(): PressBackExplorationAction
                = PressBackExplorationAction()

        @JvmStatic
        fun newWidgetSwipeExplorationAction(widget: IWidget, direction: Direction): WidgetExplorationAction {
            return WidgetExplorationAction(widget, false, 0, true, direction)
        }

    }

    protected var runtimePermission: Boolean = false
    private val observers: MutableList<IExplorationActionResultObserver> = ArrayList();

    override fun toString(): String = "<ExplAct ${toShortString()}>"

    open fun isEndorseRuntimePermission(): Boolean
            = runtimePermission

    abstract fun toShortString(): String

    open fun toTabulatedString(): String
            = toShortString()

    open fun notifyResult(result: IExplorationActionRunResult) {
        this.notifyObservers(result)
    }

    internal open fun notifyObservers(result: IExplorationActionRunResult) {
        val toRemove: MutableList<IExplorationActionResultObserver> = ArrayList()
        this.observers.forEach { p ->
            if (p.notifyActionExecuted(result))
                toRemove.add(p)
        }

        toRemove.forEach { p -> this.unregisterObserver(p) }
    }

    @Suppress("MemberVisibilityCanPrivate", "RedundantVisibilityModifier")
    public open fun unregisterObserver(observer: IExplorationActionResultObserver) {
        if (this.observers.contains(observer))
            this.observers.remove(observer)
    }

    @Suppress("MemberVisibilityCanPrivate", "RedundantVisibilityModifier")
    public open fun registerObserver(observer: IExplorationActionResultObserver) {
        if (!this.observers.contains(observer))
            this.observers.add(observer)
    }

    override fun equals(other: Any?): Boolean = this.toString() == other?.toString() ?: ""
    override fun hashCode(): Int {
        var result = runtimePermission.hashCode()
        result = 31 * result + observers.hashCode()
        return result
    }
}
