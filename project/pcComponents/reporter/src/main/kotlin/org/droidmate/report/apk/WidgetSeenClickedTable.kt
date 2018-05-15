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

import org.droidmate.exploration.statemodel.Widget
import org.droidmate.exploration.ExplorationContext
import org.droidmate.report.misc.CountsPartitionedByTimeTable

class WidgetSeenClickedTable(data: ExplorationContext) : CountsPartitionedByTimeTable(
		data.getExplorationTimeInMs(),
		listOf(
				headerTime,
				headerViewsSeen,
				headerViewsClicked
		),
		listOf(
				data.uniqueSeenActionableViewsCountByTime,
				data.uniqueClickedViewsCountByTime
		)
) {

	companion object {

		const val headerTime = "Time_seconds"
		const val headerViewsSeen = "Actionable_unique_views_seen"
		const val headerViewsClicked = "Actionable_unique_views_clicked"

		private val ExplorationContext.uniqueSeenActionableViewsCountByTime: Map<Long, Iterable<String>>
			get() {
				return this.uniqueViewCountByPartitionedTime(
						extractItems = { this.getCurrentState().actionableWidgets.filter{ it.packageName == this.apk.packageName} }
				)
			}

		private val ExplorationContext.uniqueClickedViewsCountByTime: Map<Long, Iterable<String>>
			get() {
				return this.uniqueViewCountByPartitionedTime(extractItems = { this.actionTrace.getExploredWidgets() })
			}

		private fun ExplorationContext.uniqueViewCountByPartitionedTime(
				extractItems: (Any) -> Iterable<Widget>): Map<Long, Iterable<String>> {
			TODO("what do we intent to compute here?")
//            return this.logRecords.itemsAtTime(
//                    startTime = this.explorationStartTime,
//                    extractTime = { it.getAction().timestamp },
//                    extractItems = extractItems
//            ).mapValues {
//                val widgets = it.value
//                widgets.map { it.uniqueString }
//            }
		}
	}
}

