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

import com.google.common.collect.Table
import org.droidmate.exploration.data_aggregators.AbstractContext
import org.droidmate.report.misc.buildTable
import java.util.*

//TODO check if this is even still used
class ClickFrequencyTable private constructor(val table: Table<Int, String, Int>) : Table<Int, String, Int> by table {

    constructor(data: AbstractContext) : this(build(data))

  companion object {
    const val headerNoOfClicks = "No_of_clicks"
    const val headerViewsCount = "Views_count"

      fun build(data: AbstractContext): Table<Int, String, Int> {

          val countOfViewsHavingNoOfClicks: Map<Int, Int> = data.countOfViewsHavingNoOfClicks

          return buildTable(
                  headers = listOf(headerNoOfClicks, headerViewsCount),
                  rowCount = countOfViewsHavingNoOfClicks.keys.size,
                  computeRow = { rowIndex ->
                      check(countOfViewsHavingNoOfClicks.containsKey(rowIndex))
                      val noOfClicks = rowIndex
                      listOf(
                              noOfClicks,
                              countOfViewsHavingNoOfClicks[noOfClicks]!!
                      )
                  }
          )
      }

    /** computing how many new widgets become visible after each action (#actions -> #newWidgets) **/
    private val AbstractContext.countOfViewsHavingNoOfClicks: Map<Int, Int>
      get() = mutableMapOf<Int,Int>().also{ res ->
        with(mutableSetOf<UUID>()){ // temporary set of widgets seen over the action trace
          actionTrace.getActions().forEachIndexed { idx,action ->
            size.let { nSeenBefore -> // the size of the set of widgets seen until step idx
              getState(action.resState)?.widgets?.map { it.uid }?.let { addAll(it) }  // add the unique ids of widgets seen in the new state
              res.put(idx, size - nSeenBefore)  // this is the number of newly explored widgets
            }
          }
        }
      }
  }
}