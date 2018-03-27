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

import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.strategy.ISelectableExplorationStrategy
import org.droidmate.exploration.strategy.StrategyPriority

/**
 * Exploration strategy that always selects the first widget on the screen.
 *
 * When instantiated has higher priority than normal widget strategies.
 *
 * @author Nataniel P. Borges Jr.
 */
class AlwaysFirstWidget private constructor() : Explore() {
	override fun chooseAction(): ExplorationAction {
		//TODO
//        val selectedWidgetInfo = currentState.actionableWidgetsInfo.first()
//        this.context.lastTarget = selectedWidgetInfo

//        return ExplorationAction.newWidgetExplorationAction(selectedWidgetInfo.widget)
		return ExplorationAction.newWidgetExplorationAction(context.getCurrentState().widgets.first())
	}

	override fun getFitness(): StrategyPriority {
		// If this strategy is active it should always have preference
		// unless competing against termination, first reset or runtime permission dialog
		return StrategyPriority.SPECIFIC_WIDGET
	}

	override fun equals(other: Any?): Boolean {
		return other is AlwaysFirstWidget
	}

	override fun hashCode(): Int {
		return 0
	}

	override fun toString(): String {
		return "${this.javaClass}"
	}

	companion object {
		/**
		 * Creates a new exploration strategy instance
		 */
		fun build(): ISelectableExplorationStrategy {
			return AlwaysFirstWidget()
		}
	}
}