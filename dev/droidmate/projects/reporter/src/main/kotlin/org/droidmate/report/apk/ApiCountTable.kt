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

import org.droidmate.exploration.actions.ExplorationRecord
import org.droidmate.exploration.data_aggregators.IExplorationLog
import org.droidmate.report.EventApiPair
import org.droidmate.report.misc.CountsPartitionedByTimeTable
import org.droidmate.report.misc.extractEventApiPairs
import org.droidmate.report.misc.itemsAtTimes

class ApiCountTable : CountsPartitionedByTimeTable {

  constructor(data: IExplorationLog) : super(
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

    val headerTime = "Time_seconds"
    val headerApisSeen = "Apis_seen"
    val headerApiEventsSeen = "Api+Event_pairs_seen"

    private val IExplorationLog.uniqueApisCountByTime: Map<Long, Iterable<String>>
      get() {
          return this.logRecords.itemsAtTimes(
                extractItems = { it.getResult().deviceLogs.apiLogs },
        startTime = this.explorationStartTime,
        extractTime = { it.time }
      ).mapValues {
        val apis = it.value
        apis.map { it.uniqueString }
      }
    }

    private val IExplorationLog.uniqueEventApiPairsCountByTime: Map<Long, Iterable<String>>
      get() {

          return this.logRecords.itemsAtTimes(
                  extractItems = ExplorationRecord::extractEventApiPairs,
        startTime = this.explorationStartTime,
        extractTime = EventApiPair::time
      ).mapValues {
        val eventApiPairs = it.value
        eventApiPairs.map { it.uniqueString }
      }
    }
  }
}