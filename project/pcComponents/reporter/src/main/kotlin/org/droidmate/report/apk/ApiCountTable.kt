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

import org.droidmate.device.logcat.ApiLogcatMessage
import org.droidmate.device.logcat.IApiLogcatMessage
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.report.misc.CountsPartitionedByTimeTable
import java.time.Duration
import java.util.*

class ApiCountTable : CountsPartitionedByTimeTable {

	constructor(data: ExplorationContext) : super(
			data.getExplorationTimeInMs(),
			listOf(
					headerTime,
					headerApisSeen,
					headerApiEventsSeen
			),
			listOf(
					data.uniqueApisCountByTime,
					data.uniqueEventApiPairsCountByTime
			)
	)

	companion object {

		const val headerTime = "Time_seconds"
		const val headerApisSeen = "Apis_seen"
		const val headerApiEventsSeen = "Api+Event_pairs_seen"

		/** the collection of Apis triggered , grouped based on the apis timestamp
		 * Map<time, List<(action,api)>> is for each timestamp the list of the triggered action with the observed api*/
		private val ExplorationContext.apisByTime
			get() =
				LinkedList<Pair<Interaction, IApiLogcatMessage>>().apply {
					// create a list of (widget.id,IApiLogcatMessage)
					explorationTrace.getActions().forEach { action ->
						// collect all apiLogs over the whole trace
						action.deviceLogs.forEach { add(Pair(action, ApiLogcatMessage.from(it))) }
					}
				}.groupBy { (_, api) -> Duration.between(explorationStartTime, api.time).toMillis() } // group them by their start time (i.e. how may milli seconds elapsed since exploration start)

		/** map of seconds elapsed during app exploration until the api was called To the set of api calls (their unique string) **/
		private val ExplorationContext.uniqueApisCountByTime: Map<Long, Iterable<String>>
			get() = apisByTime.mapValues { it.value.map { (_, api) -> api.uniqueString } }   // instead of the whole IApiLogcatMessage only keep the unique string for the Api


		/** map of seconds elapsed during app exploration until the api was triggered To  **/
		private val ExplorationContext.uniqueEventApiPairsCountByTime: Map<Long, Iterable<String>>
			get() = apisByTime.mapValues { it.value.map { (action, api) -> "${action.actionString()}_${api.uniqueString}" } }
	}
}