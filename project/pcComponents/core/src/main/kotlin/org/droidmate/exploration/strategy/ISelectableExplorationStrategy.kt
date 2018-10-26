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
package org.droidmate.exploration.strategy

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.explorationModel.ActionResult
import org.droidmate.exploration.ExplorationContext

/**
 * Base class for exploration strategies that can be selected from within an IStrategyPool
 *
 * @author Nataniel P. Borges Jr.
 */
interface ISelectableExplorationStrategy {
	val uniqueStrategyName: String
	val listeners: ArrayList<IControlObserver>
	val noContext: Boolean

	fun initialize(memory: ExplorationContext)

	/**
	 * Add a new [listener] to receive execution flow back, as well as to receive notification about
	 * found targets and blacklisted widgets
	 */
	fun registerListener(listener: IControlObserver)

	/**
	 * Update the []number of explored actionTrace][actionNr]
	 */
	fun updateState(actionNr: Int, record: ActionResult)

	/**
	 * Selects an exploration action based on the [current GUI](currentState).
	 *
	 * When using an exploration pool, this method is only invoked if the current strategy
	 * had the highest fitness
	 *
	 * @return Exploration action to be sent to the device (has to be supported by DroidMate)
	 */
	fun decide(): ExplorationAction

	/**
	 * Update state after receiving notification that a new target has been found.
	 *
	 * @param strategy Exploration strategy that interacted with the target
	 * @param satisfiedWidget Exploration's target that has been found
	 * @param result ExplorationAction performed on the target, alongside its results
	 */
	fun onTargetFound(strategy: ISelectableExplorationStrategy, satisfiedWidget: ITargetWidget,
	                  result: ActionResult)

	override fun equals(other: Any?): Boolean
	override fun hashCode(): Int

}
