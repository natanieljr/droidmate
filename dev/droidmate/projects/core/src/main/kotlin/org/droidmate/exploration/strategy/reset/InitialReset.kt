// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Konrad Jamrozik
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
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org
package org.droidmate.exploration.strategy.reset

import org.droidmate.exploration.strategy.StrategyPriority
import org.droidmate.exploration.strategy.WidgetContext

/**
 * Initial reset to start the exploration.
 *
 * @author Nataniel P. Borges Jr.
 */
class InitialReset : Reset() {
    override fun getFitness(widgetContext: WidgetContext): StrategyPriority {
        // First action is always reset
        if (this.firstDecisionIsBeingMade())
            return StrategyPriority.FIRST_RESET

        // Any other action
        return StrategyPriority.NONE
    }

    override fun equals(other: Any?): Boolean {
        return other is InitialReset
    }

    /*companion object {
        /**
         * Creates a new exploration strategy instance with reset interval provided by [DroidMate's configuration][cfg]
         */
        fun build(cfg: Configuration): ISelectableExplorationStrategy {
            return Reset(cfg.resetEveryNthExplorationForward)
        }
    }*/
}