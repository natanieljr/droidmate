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

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/** ASSUMPTION:
 * job.join is called between on each information poll
 * alternatively an actor could be implemented*/
class ActionCounterMF : ModelFeature() {
	// we don't want to wait for other modelFeatures (or having them wait for us), therefore create our own (child) job
	override val coroutineContext: CoroutineContext = CoroutineName("ActionCounter")+Job()

	override suspend fun onNewInteracted(traceId: UUID, targetWidgets: List<Widget>, prevState: State, newState: State): Unit =
			targetWidgets.forEach { target ->
				prevState.uid.let { sId ->
					target.let { w -> pCnt.compute(w.packageName) { _, c -> c?.inc() ?: 1 } }
					sCnt.incCnt(sId)   // the state the very last action acted on
					// record the respective widget the exploration interacted
					target.let { wCnt.compute(it.uid) { _, m -> m?.incCnt(sId) ?: mutableMapOf(sId to 1) } }
				}
			}


	private val sCnt = ConcurrentHashMap<UUID, Int>() // counts how often any state was explored
	// records how often a specific widget was selected and from which state-eContext (widget.uid -> Map<state.uid -> numActions>)
	private val wCnt = ConcurrentHashMap<UUID, MutableMap<UUID, Int>>()

	// to prioritize app widgets over reappearing external/keyboard elements we sum non-app interactions by their package name
	private val pCnt = ConcurrentHashMap<String, Int>()

	@Suppress("unused")
	suspend fun unexplored(s: State): Set<Widget> {
		this.join()
		return numExplored(s).filter { it.value == 0 }.keys  // collect all widgets which are not in our action counter => not interacted with
	}

	/** determine the number of unique states explored so far */
	@Suppress("unused")
	suspend fun numStates():Int{
		this.join()
		return sCnt.size
	}

	@Suppress("MemberVisibilityCanBePrivate")
			/**
	 * determine how often any widget was explored in the eContext of the given state [s]
	 *
	 * @return map of the widget.uid to the number of interactions from state-eContext [s]
	 */
	suspend fun numExplored(s: State): Map<Widget, Int> {
		this.join()
		return s.actionableWidgets.map {
			it to it.uid.cntForState(s.uid)//(wCnt[w.uid]?.get(s.uid)?:0)
		}.toMap()
	}

	/** determine how often any widget was explored in the eContext of the given state [s] for the given subset of widgets [selection]
	 * @return map of the widget.uid to the number of interactions from state-eContext [s]
	 */
	suspend fun numExplored(s: State, selection: Collection<Widget>): Map<Widget, Int> {
		this.join()
		return selection.map {
			it to it.uid.cntForState(s.uid)//(wCnt[w.uid]?.get(s.uid)?:0)
		}.toMap()
	}

	private fun UUID.cntForState(sId: UUID): Int = wCnt.getCounter(this, sId)
	/** @return how often widget.uid was triggered in the given state-eContext **/
	@Suppress("unused")
	suspend fun widgetCntForState(wId: UUID, sId: UUID): Int {
		this.join()
		return wId.cntForState(sId)
	}

	/** @return how often the widget.uid was triggered other all states **/
	suspend fun widgetCnt(wId: UUID): Int {
		this.join()
		return wCnt.sumCounter(wId)
	}

	fun pkgCount(pkgName: String): Int = pCnt[pkgName]?:0

}


