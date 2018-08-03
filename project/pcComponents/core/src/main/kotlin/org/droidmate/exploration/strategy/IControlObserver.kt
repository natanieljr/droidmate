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

import org.droidmate.exploration.statemodel.ActionResult

/**
 * Interface to receive notifications when control flow should be
 * returned from the internal exploration strategy to the main code
 * or to notify when all widgets are blacklisted

 * @author Nataniel P. Borges Jr.
 */
interface IControlObserver {
	/**
	 * Takes execution control back from an exploration strategy. The exploration strategy fires
	 * this event when it does not require to do any other subsequent action.
	 *
	 * @param strategy Exploration strategy that previously possessed the execution priority
	 */
	fun takeControl(strategy: ISelectableExplorationStrategy)

	/**
	 * Receives notification from an exploration action that all widget on the current
	 * screen have been blacklisted.
	 */
	fun notifyAllWidgetsBlacklisted()

	/**
	 * Receives notification that one of the exploration targets have been found and interacted with.
	 *
	 * @param strategy Exploration strategy that interacted with the target
	 * @param targetWidget Exploration's target that has been found
	 * @param result ExplorationAction performed on the target, alongside its results
	 */
	fun onTargetFound(strategy: ISelectableExplorationStrategy, targetWidget: ITargetWidget,
	                  result: ActionResult)
}
