// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Konrad Jamrozik
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
package org.droidmate.report.apk

import org.droidmate.device.datatypes.IGuiStatus
import org.droidmate.device.datatypes.Widget
import org.droidmate.exploration.actions.WidgetExplorationAction
import org.droidmate.exploration.data_aggregators.IExplorationLog
import org.droidmate.exploration.strategy.ActionResult
import java.nio.file.Files
import java.nio.file.Path

class WidgetApiTrace(private val fileName: String = "widget_api_trace.txt") : ApkReport() {
    override fun writeApkReport(data: IExplorationLog, apkReportDir: Path) {
        val sb = StringBuilder()
        val header = "actionNr\ttext\tapi\tuniqueStr\taction\n"
        sb.append(header)

        data.getRecords().forEachIndexed { actionNr, record ->
            if (record.action is WidgetExplorationAction) {
                val text = getActionWidget(record)
                val logs = record.deviceLogs.apiLogs
                val widget = (record.action as WidgetExplorationAction).widget

                logs.forEach { log ->
                    sb.appendln("$actionNr\t$text\t${log.objectClass}->${log.methodName}\t$widget\t${log.uniqueString}")
                }
            }
        }

        val reportFile = apkReportDir.resolve(fileName)
        Files.write(reportFile, sb.toString().toByteArray())
    }

    private fun getActionWidget(actionResult: ActionResult): Widget? {
        return if (actionResult.action is WidgetExplorationAction) {
            val explAction = (actionResult.action as WidgetExplorationAction)

            getWidgetWithTextFromAction(explAction.widget, actionResult.widgetContext.guiStatus)
        } else
            null
    }

    private fun getWidgetWithTextFromAction(widget: Widget, guiStatus: IGuiStatus): Widget {
        // If has Text
        if (widget.text.isNotEmpty())
            return widget

        val children = guiStatus.widgets
                .filter { p -> p.parent == widget }

        // If doesn't have any children
        if (children.isEmpty()) {
            return widget
        }

        val childrenWithText = children.filter { p -> p.text.isNotEmpty() }

        return when {
        // If a single children have text
            childrenWithText.size == 1 -> childrenWithText.first()

        // Single child, drill down
            children.size == 1 -> getWidgetWithTextFromAction(children.first(), guiStatus)

        // Multiple children, skip
            else -> widget
        }
    }
}