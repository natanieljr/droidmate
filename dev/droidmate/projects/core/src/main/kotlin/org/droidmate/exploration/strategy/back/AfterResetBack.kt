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
package org.droidmate.exploration.strategy.back

import org.droidmate.exploration.actions.PressBackExplorationAction
import org.droidmate.exploration.actions.ResetAppExplorationAction
import org.droidmate.exploration.strategy.StrategyPriority
import org.droidmate.exploration.strategy.WidgetContext

/**
 * Presses back if it can' move forward and last action was to reset
 * try to press back because sometimes an account selection dialog pops up
 *
 * exception to rule: if before the reset it was a back action, allows to terminate,
 * otherwise the exploration would enter a loop of Back -> Reset -> Back -> Reset....
 */
class AfterResetBack : Back() {
    override fun getFitness(widgetContext: WidgetContext): StrategyPriority {
        return if (!widgetContext.explorationCanMoveForwardOn() &&
                this.lastAction() is ResetAppExplorationAction)
            if (widgetContext.guiState.isAppHasStoppedDialogBox ||
                    this.getSecondLastAction() is PressBackExplorationAction)
                StrategyPriority.BACK
            else
                StrategyPriority.BACK_BEFORE_TERMINATE
        else
            StrategyPriority.NONE
    }
}