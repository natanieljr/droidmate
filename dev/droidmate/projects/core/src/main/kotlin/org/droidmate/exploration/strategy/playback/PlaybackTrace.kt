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
package org.droidmate.exploration.strategy.playback

import org.droidmate.device.datatypes.IWidget
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.WidgetExplorationAction
import org.droidmate.exploration.strategy.WidgetContext
import org.droidmate.misc.uniqueString
import java.io.Serializable

class PlaybackTrace : Serializable {
    @Suppress("unused")
    data class PlaybackTraceData(val action: ExplorationAction,
                                 val widgetContext: WidgetContext,
                                 var requested: Boolean = false,
                                 var explored: Boolean = false) : Serializable {
        override fun toString(): String {
            return "R: ${if (requested) 1 else 0} E: ${if (explored) 1 else 0} $action"
        }

        companion object {
            @JvmStatic private val serialVersionUID = 1
        }
    }

    private val trace: MutableList<PlaybackTraceData> = ArrayList()

    fun add(action: ExplorationAction, widgetContext: WidgetContext) {
        trace.add(PlaybackTraceData(action, widgetContext))
    }

    fun explore(action: ExplorationAction) {
        val traceData = trace.first { p -> p.action == action && !p.explored }
        traceData.explored = true
    }

    fun isComplete(): Boolean {
        return trace.all { it.requested }
    }

    fun requestNext(): PlaybackTraceData {
        val data = trace.first { !it.requested }
        data.requested = true

        return data
    }

    fun peekNextWidgetAction(): PlaybackTraceData? {
        return trace.firstOrNull { !it.requested && it.action is WidgetExplorationAction }
    }

    fun getExploredRatio(widget: IWidget? = null): Double {
        val traces = if (widget != null) {
            val idx = indexOf(widget)

            if (idx >= 0)
                trace.subList(idx, trace.size)
            else
                trace
        } else
            trace

        val explored = traces.count { it.explored }

        return explored / traces.size.toDouble()
    }

    fun contains(widget: IWidget): Boolean {
        return trace.any { p ->
            (p.action is WidgetExplorationAction) &&
                    (p.action.widget.uniqueString == widget.uniqueString)
        }
    }

    fun reset() {
        trace.forEach { p ->
            p.explored = false
            p.requested = false
        }
    }

    private fun indexOf(widget: IWidget): Int {
        return trace.indexOfFirst { p ->
            (p.action is WidgetExplorationAction) &&
                    (p.action.widget.uniqueString == widget.uniqueString)
        }
    }

    override fun toString(): String {
        return trace.joinToString("\n") { it.toString() }
    }

    @Suppress("unused")
    fun getSize(widget: IWidget? = null): Int {
        val traces = if (widget != null) {
            val idx = indexOf(widget)

            if (idx >= 0)
                trace.subList(idx, trace.size)
            else
                trace
        } else
            trace

        return traces.size
    }

    @Suppress("unused")
    fun getTraceCopy(): List<PlaybackTraceData> {
        return this.trace.map { it.copy() }
    }

    companion object {
        @JvmStatic private val serialVersionUID = 1
    }
}