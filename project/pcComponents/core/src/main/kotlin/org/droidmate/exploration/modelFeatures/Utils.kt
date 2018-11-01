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

package org.droidmate.exploration.modelFeatures

import java.lang.Integer.max

/** used to increase the counter value of a map **/
internal inline fun <reified K> MutableMap<K, Int>.incCnt(id: K): MutableMap<K, Int> = this.apply {
	compute(id) { _, c ->
		c?.inc() ?: 1
	}
}
/** used to decrease the counter value of a map **/
internal inline fun <reified K> MutableMap<K, Int>.decCnt(id: K): MutableMap<K, Int> = this.apply {
	compute(id) { _, c ->
		max(c?.dec() ?: 0, 0)
	}
}

/** use this function on a list, grouped by it's counter, to retrieve all entries which have the smallest counter value
 * e.g. numExplored(state).entries.groupBy { it.value }.listOfSmallest */
inline fun <reified K> Map<Int, List<K>>.listOfSmallest(): List<K>? = this[this.keys.fold(Int.MAX_VALUE) { res, c -> if (c < res) c else res }]

/** @return the sum of all counter over all eContext values [T] **/
inline fun <reified K, reified T> Map<K, Map<T, Int>>.sumCounter(id: K): Int = get(id)?.values?.sum() ?: 0
/** @return a specific counter value for eContext [T] */
inline fun <reified K, reified T> Map<K, Map<T, Int>>.getCounter(kId: K, vId: T): Int = get(kId)?.get(vId) ?: 0