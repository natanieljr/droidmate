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
package org.droidmate.exploration.strategy.widget

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.strategy.AbstractStrategy

/**
 * Abstract class for implementing widget exploration strategies.
 *
 * @author Nataniel P. Borges Jr.
 */
abstract class ExplorationStrategy : AbstractStrategy() {

	// region overrides


	override suspend fun internalDecide(): ExplorationAction {
		assert(eContext.explorationCanMoveOn()) {"Selected and explore action, but exploration cannot proceed."}

		val allWidgetsBlackListed = eContext.getCurrentState().actionableWidgets.isEmpty() // || TODO Blacklist
		if (allWidgetsBlackListed)
			this.notifyAllWidgetsBlacklisted()

		return chooseAction()
	}

	protected fun updateState(): Boolean {
		if (!eContext.belongsToApp(eContext.getCurrentState())) {
			if (!eContext.isEmpty()) {
//                this.eContext.lastTarget.blackListed = true //TODO blacklist missing in current model
				logger.debug("Blacklisted ${this.eContext.lastTarget}")
			}
		}

		return false //currentState.allWidgetsBlacklisted()
	}

	abstract suspend fun chooseAction(): ExplorationAction
}