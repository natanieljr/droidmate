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

package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.joinChildren
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class WidgetCountingMF:  ModelFeature(){
	// records how often a specific widget was selected and from which state-eContext (widget.uid -> Map<state.uid -> numActions>)
	private val wCnt: ConcurrentHashMap<UUID, MutableMap<UUID, Int>> = ConcurrentHashMap()

	init{
		job = Job(parent = (this.job)) // we don't want to wait for other features (or having them wait for us), therefore create our own (child) job
	}

	/**
	 * this function is used to increase the counter of a specific widget with [wId] in the eContext of [stateId]
	 * if there is no entry yet for the given widget id, the counter value is initialized to 1
	 * @param wId the unique id of the target widget for the (new) action
	 * @param stateId the unique id of the state (the prevState) from which the widget was triggered
	 */
	fun incCnt(wId: UUID, stateId: UUID){
		wCnt.compute(wId,
				{ _, m -> m?.incCnt(stateId) ?: mutableMapOf(stateId to 1) })
	}

	/** decrease the counter for a given widget id [wId] and state eContext [stateId].
	 * The minimal possible value is 0 for any counter value.
	 */
	fun decCnt(wId: UUID, stateId: UUID){
		wCnt.compute(wId,
				{ _,m -> m?.decCnt(stateId) ?: mutableMapOf(stateId to 0)})
	}

	suspend fun isBlacklisted(wId: UUID, threshold: Int = 1): Boolean {
		job.joinChildren() // wait that all updates are applied before changing the counter value
		return wCnt.sumCounter(wId) >= threshold
	}

	suspend fun isBlacklistedInState(wId: UUID, sId: UUID, threshold: Int = 1): Boolean {
		job.joinChildren() // wait that all updates are applied before changing the counter value
		return wCnt.getCounter(wId, sId) >= threshold
	}

	/** dumping the current state of the widget counter
	 * job.joinChildren() before dumping to ensure that all updating coroutines completed
	 */
	suspend fun dump(file: File){
		job.joinChildren() // wait that all updates are applied before changing the counter value
		file.bufferedWriter().use { out ->
			out.write(header)
			wCnt.toSortedMap(compareBy { it.toString() }).forEach { wMap ->	wMap.value.entries.forEach { (sId, cnt) ->
				out.newLine()
				out.write("${wMap.key} ;\t$sId ;\t$cnt")
			}}
		}
	}
	companion object {
		val header = "WidgetId".padEnd(38)+"; State-Context".padEnd(38)+"; # listed"

	}
}