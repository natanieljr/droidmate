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
package org.droidmate.device.datatypes.statemodel.features

import org.droidmate.device.datatypes.Widget
import org.droidmate.device.datatypes.statemodel.StateData
import org.droidmate.exploration.data_aggregators.ExplorationContext
import java.util.*

@Suppress("unused", "MemberVisibilityCanBePrivate")
class ActionCounterMF:IModelFeature {

	private val sCnt = mutableMapOf<UUID,Int>() // counts how often the any state was explored
	// records how often a specific widget was selected and from which state-context (widget.uid -> Map<state.uid -> numActions>)
	private val wCnt = mutableMapOf<UUID,MutableMap<UUID,Int>>()
	private fun<K> MutableMap<K,Int>.incCnt(id:K):MutableMap<K,Int> = this.apply { compute( id, { _,c -> c?.inc()?:1 }) }

	override fun dump(context: ExplorationContext) { /* do nothing */	}

	override fun update(context: ExplorationContext) {
		context.getPreviousState().uid.let{ sId -> sCnt.incCnt(sId)   // the state the very last action acted on
			// record the respective widget the exploration interacted
			context.lastTarget?.let{ wCnt.compute(it.uid, { _,m -> m?.incCnt(sId)?: mutableMapOf(sId to 1)} ) }
		}
	}

	fun getStateCnt():Map<UUID,Int> = sCnt
	fun getWidgetCnt():Map<UUID,Map<UUID,Int>> = wCnt

	fun unexplored(s:StateData):Set<Widget> = numExplored(s).filter { it.value == 0 }.keys  // collect all widgets which are not in our action counter => not interacted with

	/**
	 * determine how often any widget was explored in the context of the given state [s]
	 *
	 * @return map of the widget.uid to the number of interactions from state-context [s]
	 */
	fun numExplored(s:StateData):Map<Widget,Int> = s.actionableWidgets.map {it to it.uid.cntForState(s.uid)//(wCnt[w.uid]?.get(s.uid)?:0)
	}.toMap()

	private fun UUID.cntForState(sId:UUID):Int = wCnt[this]?.get(sId)?:0
	/** @return how often widget.uid was triggered in the given state-context **/
	fun widgetCntForState(wId:UUID,sId:UUID):Int = wId.cntForState(sId)
	/** @return how often the widget.uid was triggered other all states **/
	fun widgetCnt(wId: UUID):Int = wCnt[wId]?.values?.sum()?:0

}
/** use this function on a list, grouped by it's counter, to retrieve all entries which have the smallest counter value
 * e.g. numExplored(state).entries.groupBy { it.value }.listOfSmallest */
fun<K> Map<Int, List<K>>.listOfSmallest():List<K>? = this[this.keys.fold(Int.MAX_VALUE,{ res, c -> if(c<res) c else res})]
