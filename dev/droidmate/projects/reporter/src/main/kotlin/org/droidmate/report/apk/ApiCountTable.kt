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

import org.droidmate.apis.IApiLogcatMessage
import org.droidmate.device.datatypes.statemodel.ActionData
import org.droidmate.exploration.data_aggregators.AbstractContext
import org.droidmate.report.misc.CountsPartitionedByTimeTable
import java.time.Duration
import java.util.*

class ApiCountTable : CountsPartitionedByTimeTable {

    constructor(data: AbstractContext) : super(
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
        private val AbstractContext.apisByTime
			get() =
				LinkedList<Pair<ActionData, IApiLogcatMessage>>().apply {
					// create a list of (widget.id,IApiLogcatMessage)
					actionTrace.getActions().forEach { action ->
						// collect all apiLogs over the whole trace
						action.deviceLogs.apiLogs.forEach { add(Pair(action, it)) }
					}
				}.groupBy { (_, api) -> Duration.between(explorationStartTime, api.time).toMillis() } // group them by their start time (i.e. how may milli seconds elapsed since exploration start)

		/** map of seconds elapsed during app exploration until the api was called To the set of api calls (their unique string) **/
        private val AbstractContext.uniqueApisCountByTime: Map<Long, Iterable<String>>
			get() = apisByTime.mapValues { it.value.map { (_, api) -> api.uniqueString } }   // instead of the whole IApiLogcatMessage only keep the unique string for the Api


		/** map of seconds elapsed during app exploration until the api was triggered To  **/
        private val AbstractContext.uniqueEventApiPairsCountByTime: Map<Long, Iterable<String>>
			get() = apisByTime.mapValues { it.value.map { (action, api) -> "${action.actionString()}_${api.uniqueString}" } }
	}
}