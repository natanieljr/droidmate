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
package org.droidmate.report.apk

import org.droidmate.device.datatypes.statemodel.ActionData
import org.droidmate.device.datatypes.statemodel.StateData
import org.droidmate.device.datatypes.statemodel.Widget
import org.droidmate.exploration.actions.WidgetExplorationAction
import org.droidmate.exploration.data_aggregators.AbstractContext
import java.nio.file.Files
import java.nio.file.Path

class WidgetApiTrace(private val fileName: String = "widget_api_trace.txt") : ApkReport() {
	override fun safeWriteApkReport(data: AbstractContext, apkReportDir: Path) {
		val sb = StringBuilder()
		val header = "actionNr\ttext\tapi\tuniqueStr\taction\n"
		sb.append(header)

		data.actionTrace.getActions().forEachIndexed { actionNr, record ->
			if (record.actionType == WidgetExplorationAction::class.simpleName) {
				val text = data.getState(record.resState)?.let { getActionWidget(record, it) }
				val logs = record.deviceLogs.apiLogs
				val widget = record.targetWidget

				logs.forEach { log ->
					sb.appendln("$actionNr\t$text\t${log.objectClass}->${log.methodName}\t$widget\t${log.uniqueString}")
				}
			}
		}

		val reportFile = apkReportDir.resolve(fileName)
		Files.write(reportFile, sb.toString().toByteArray())
	}

	private fun getActionWidget(actionResult: ActionData, state: StateData): Widget? {
		return if (actionResult.actionType == WidgetExplorationAction::class.simpleName) {

			getWidgetWithTextFromAction(actionResult.targetWidget!!, state)
		} else
			null
	}

	private fun getWidgetWithTextFromAction(widget: Widget, state: StateData): Widget {
		// If has Text
		if (widget.text.isNotEmpty())
			return widget

		val children = state.widgets
				.filter { p -> p.parentId == widget.id }

		// If doesn't have any children
		if (children.isEmpty()) {
			return widget
		}

		val childrenWithText = children.filter { p -> p.text.isNotEmpty() }

		return when {
		// If a single children have text
			childrenWithText.size == 1 -> childrenWithText.first()

		// Single child, drill down
			children.size == 1 -> getWidgetWithTextFromAction(children.first(), state)

		// Multiple children, skip
			else -> widget
		}
	}
}