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

import org.droidmate.device.datatypes.IGuiState
import org.droidmate.device.datatypes.IWidget
import org.droidmate.exploration.actions.WidgetExplorationAction
import org.droidmate.exploration.data_aggregators.IExplorationLog
import org.droidmate.exploration.strategy.IMemoryRecord
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

    private fun getActionWidget(memoryRecord: IMemoryRecord): IWidget? {
        return if (memoryRecord.action is WidgetExplorationAction) {
            val explAction = (memoryRecord.action as WidgetExplorationAction)

            getWidgetWithTextFromAction(explAction.widget, memoryRecord.widgetContext.guiState)
        } else
            null
    }

    private fun getWidgetWithTextFromAction(widget: IWidget, guiState: IGuiState): IWidget {
        // If has Text
        if (widget.text.isNotEmpty())
            return widget

        val children = guiState.widgets
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
            children.size == 1 -> getWidgetWithTextFromAction(children.first(), guiState)

        // Multiple children, skip
            else -> widget
        }
    }
}