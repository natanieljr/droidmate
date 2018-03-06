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

import org.droidmate.exploration.actions.ActionType
import org.droidmate.exploration.strategy.StrategyPriority
import org.droidmate.exploration.strategy.WidgetContext

/**
 * Restart the app when there are no widgets to explore in the current state
 *
 * @author Nataniel P. Borges Jr.
 */
class CannotExploreReset : Reset() {
    override fun getFitness(widgetContext: WidgetContext): StrategyPriority {
        // If can' move forward and have already tried to press back reset,
        // however, reset is never as good as a specific exploration
        if (!widgetContext.explorationCanMoveForwardOn() &&
                this.lastActionWasOfType(ActionType.Back))
            return StrategyPriority.RESET

        // Any other action
        return StrategyPriority.NONE
    }

    override fun equals(other: Any?): Boolean {
        return other is CannotExploreReset
    }
}