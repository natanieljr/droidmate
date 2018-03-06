// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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
package org.droidmate.report

import org.droidmate.apis.IApiLogcatMessage
import org.droidmate.device.datatypes.IWidget
import org.droidmate.device.datatypes.WaitA
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.exploration.actions.*
import java.time.LocalDateTime

class EventApiPair(actRes: ExplorationRecord, apiLog: IApiLogcatMessage) {

    val pair: Pair<String, IApiLogcatMessage>

    operator fun component1() = pair.first
    operator fun component2() = pair.second

    val time: LocalDateTime get() = pair.second.time

    init {
        pair = build(actRes, apiLog)
    }

    private fun build(actRes: ExplorationRecord, apiLog: IApiLogcatMessage): Pair<String, IApiLogcatMessage> {

        fun extractEvent(action: ExplorationAction, thread: Int): String {

            fun extractWidgetEventString(action: ExplorationAction): String {
                require(action is WidgetExplorationAction || action is EnterTextExplorationAction)
                val w: IWidget
                val prefix: String
                when (action) {
                    is WidgetExplorationAction -> {
                        w = action.widget
                        prefix = (if (action.longClick) "l-click:" else "click") + ":"
                    }
                    is EnterTextExplorationAction -> {
                        w = action.widget
                        prefix = "enterText:"
                    }
                    else -> throw UnexpectedIfElseFallthroughError()
                }
                checkNotNull(w)
                val widgetString =
                            "xPath:${w.xpath}:" +
                        if (w.resourceId.isNotEmpty())
                            "[res:${w.getStrippedResourceId()}]"
                        else if (w.contentDesc.isNotEmpty())
                            "[dsc:${w.contentDesc}]"
                        else if (w.text.isNotEmpty())
                            "[txt:${w.text}]"
                        else ""

                if (widgetString.isEmpty())
                    return "unlabeled"
                else
                    return prefix + widgetString
            }

            return when (action) {
                is ResetAppExplorationAction, is TerminateExplorationAction -> "<reset>"
                is WidgetExplorationAction, is EnterTextExplorationAction ->
                    if (thread == 1)
                        extractWidgetEventString(action)
                    else
                        "background"
                is WaitA -> "<wait ${action.toShortString()}>"
                is PressBackExplorationAction -> "<press back>"
                else -> throw UnexpectedIfElseFallthroughError("Error on report creation: the $action is not implemented in ${this.javaClass.name}")
            }
        }

        return Pair(
                extractEvent(action = actRes.getAction().base, thread = apiLog.threadId.toInt()),
                apiLog)
    }

    val uniqueString: String get() = pair.first + pair.second.uniqueString
}

