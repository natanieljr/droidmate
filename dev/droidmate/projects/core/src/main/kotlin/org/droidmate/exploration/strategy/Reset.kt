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
package org.droidmate.exploration.strategy

import org.droidmate.configuration.Configuration
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newResetAppExplorationAction

/**
 * Exploration strategy that reset and exploration when:
 * - Exploration can't move forward (or)
 * - Reached configured reset interval (nr. of actions)
 *
 *
 * It doesn't reset if:
 * - If first action (or)
 * - Last action was a reset
 *
 * @constructor Creates a new class instance with a [predefined reset interval][resetEveryNthExplorationForward]
 *
 * @author Nataniel P. Borges Jr.
 */
class Reset private constructor(private val resetEveryNthExplorationForward: Int) : AbstractStrategy() {
    /**
     * Number of actions that have been performed since the last reset
     */
    private var nrActionsWithoutReset: Int = 0

    override fun getFitness(widgetContext: WidgetContext): StrategyPriority {
        // First action or following a reset
        if (this.lastActionWasOfType(org.droidmate.exploration.strategy.ExplorationType.Reset))
            return StrategyPriority.NONE

        // First action is always reset
        if (this.firstDecisionIsBeingMade())
            return StrategyPriority.FIRST_RESET

        // If can' move forward and have already tried to press back reset,
        // however, reset is never as good as a specific exploration
        if (!widgetContext.explorationCanMoveForwardOn() &&
                this.lastActionWasOfType(org.droidmate.exploration.strategy.ExplorationType.Back))
            return StrategyPriority.RESET

        // Reset due to predefined interval,
        // however, reset is never as good as a specific exploration
        if (this.nrActionsWithoutReset >= this.resetEveryNthExplorationForward)
            return StrategyPriority.RESET

        // Any other action
        return StrategyPriority.NONE
    }

    override val type: ExplorationType
        get() = ExplorationType.Reset

    override fun internalDecide(widgetContext: WidgetContext): ExplorationAction {
        // There' no previous widget after a reset
        this.memory.lastWidgetInfo = null

        return newResetAppExplorationAction()
    }

    override fun updateState(actionNr: Int) {
        super.updateState(actionNr)

        val lastAction = this.memory.getLastAction()
        val lastActionType = lastAction?.type

        if (lastActionType != ExplorationType.Reset)
            this.nrActionsWithoutReset++
        else
            this.nrActionsWithoutReset = 0
    }

    override fun mustPerformMoreActions(widgetContext: WidgetContext): Boolean {
        return false
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Reset)
            return false

        return other.resetEveryNthExplorationForward == this.resetEveryNthExplorationForward
    }

    override fun toString(): String {
        return "${this.javaClass}\tInterval: ${this.resetEveryNthExplorationForward}"
    }

    override fun hashCode(): Int {
        return Integer.valueOf(this.resetEveryNthExplorationForward)!!.hashCode()
    }

    override fun start() {
        // Nothing to do here.
    }

    companion object {
        /**
         * Creates a new exploration strategy instance with reset interval provided by [DroidMate's configuration][cfg]
         */
        fun build(cfg: Configuration): ISelectableExplorationStrategy {
            return Reset(cfg.resetEveryNthExplorationForward)
        }
    }
}
