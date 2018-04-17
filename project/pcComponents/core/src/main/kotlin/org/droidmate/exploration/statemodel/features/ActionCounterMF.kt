// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Jenny Hotzkow
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
package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.statemodel.StateData
import org.droidmate.exploration.statemodel.Widget
import java.util.UUID
import kotlin.coroutines.experimental.CoroutineContext

/** ASSUMPTION:
 * job.join is called between each action, such that the access to the counter lists does not have to by synchronized
 * if that ever changes we a receiver channel or actor has to be implemented to manage these list accesses*/
@Suppress("unused", "MemberVisibilityCanBePrivate")
class ActionCounterMF : ModelFeature() {

	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("ActionCounterMF"), parent = job)

	override suspend fun onNewInteracted(targetWidget: Widget?, prevState: StateData, newState: StateData): Unit =
			prevState.uid.let { sId ->
				sCnt.incCnt(sId)   // the state the very last action acted on
				// record the respective widget the exploration interacted
				targetWidget?.let { wCnt.compute(it.uid, { _, m -> m?.incCnt(sId) ?: mutableMapOf(sId to 1) }) }
			}


	private val sCnt = mutableMapOf<UUID, Int>() // counts how often the any state was explored
	// records how often a specific widget was selected and from which state-context (widget.uid -> Map<state.uid -> numActions>)
	private val wCnt = mutableMapOf<UUID, MutableMap<UUID, Int>>()

	fun getStateCnt(): Map<UUID, Int> = sCnt
	fun getWidgetCnt(): Map<UUID, Map<UUID, Int>> = wCnt

	fun unexplored(s: StateData): Set<Widget> = numExplored(s).filter { it.value == 0 }.keys  // collect all widgets which are not in our action counter => not interacted with

	/**
	 * determine how often any widget was explored in the context of the given state [s]
	 *
	 * @return map of the widget.uid to the number of interactions from state-context [s]
	 */
	fun numExplored(s: StateData): Map<Widget, Int> = s.actionableWidgets.map {
		it to it.uid.cntForState(s.uid)//(wCnt[w.uid]?.get(s.uid)?:0)
	}.toMap()

	/** determine how often any widget was explored in the context of the given state [s] for the given subset of widgets [selection]
	 * @return map of the widget.uid to the number of interactions from state-context [s]
	 */
	fun numExplored(s: StateData, selection: Collection<Widget>): Map<Widget, Int> = selection.map {
		it to it.uid.cntForState(s.uid)//(wCnt[w.uid]?.get(s.uid)?:0)
	}.toMap()

	private fun UUID.cntForState(sId: UUID): Int = wCnt.getCounter(this, sId)
	/** @return how often widget.uid was triggered in the given state-context **/
	fun widgetCntForState(wId: UUID, sId: UUID): Int = wId.cntForState(sId)

	/** @return how often the widget.uid was triggered other all states **/
	fun widgetCnt(wId: UUID): Int = wCnt.sumCounter(wId)

}


