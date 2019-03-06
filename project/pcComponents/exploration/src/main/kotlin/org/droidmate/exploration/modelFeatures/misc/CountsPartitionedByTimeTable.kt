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

package org.droidmate.exploration.modelFeatures.misc

import com.google.common.collect.Table

open class CountsPartitionedByTimeTable private constructor(val table: Table<Int, String, Int>) : Table<Int, String, Int> by table {

	// Kotlin BUG: this constructor is actually used
	// Seems to be https://youtrack.jetbrains.com/issue/KT-12500
	constructor(
			maxTime: Int,
			headers: List<String>,
			columns: List<Map<Long, Iterable<String>>>
	) : this(build(maxTime, headers, columns))

	companion object {

		val partitionSize = 1000L

		fun build(
				maxTime: Int,
				headers: List<String>,
				columns: List<Map<Long, Iterable<String>>>
		): Table<Int, String, Int> {

			val timeRange: List<Long> = 0L.rangeTo(maxTime).step(partitionSize).toList()

			val columnCountsPartitionedByTime = columns.map {
				it.countsPartitionedByTime(
						partitionSize,
						maxTime
				)
			}

			return buildTable(
					headers,
					rowCount = timeRange.size,
					computeRow = { rowIndex ->
						val timePassed = timeRange[rowIndex]
						listOf(
								(timePassed / partitionSize).toInt()
						) + columnCountsPartitionedByTime.map { it[timePassed]!! }
					}
			)
		}

	}
}