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
import org.droidmate.device.datatypes.statemodel.StateData
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newPressBackExplorationAction
import org.droidmate.exploration.actions.PressBackExplorationAction
import org.droidmate.exploration.actions.ResetAppExplorationAction
import java.util.*

/**
 * Exploration strategy that presses the back button on the device.
 *
 * Usually has a small probability of being triggered. When triggered has a high priority.
 *
 * @constructor Creates a new class instance with [probability of triggering the event][probability]
 * and with a random seed to allow test reproducibility.
 *
 * @author Nataniel P. Borges Jr.
 */
class PressBack private constructor(private val probability: Double,
                                    randomSeed: Long) : AbstractStrategy() {

    /**
     * Random number generator which uses pre specified seed to attempt to trigger the action
     */
    private val random = Random(randomSeed)

    override fun getFitness(currentState: StateData): StrategyPriority {

        // If it can' move forward and last action was not to reset
        // On the first action the reset will have a priority of 1.0, so this is not a problem
        if (!memory.explorationCanMoveOn() &&
                this.lastAction().actionType != PressBackExplorationAction::class.simpleName)
            return StrategyPriority.BACK

        // If it can' move forward, is the second action and last action was not to reset
        // Try to press back because sometimes an account selection dialog pops up
        if (!memory.explorationCanMoveOn() &&
                this.lastAction().actionType == ResetAppExplorationAction::class.simpleName &&
                this.memory.getSize() == 1)
            return StrategyPriority.BACK_BEFORE_TERMINATE

        // We now press back randomly if the last action was not a reset (otherwise it would close the app)
        // this can allow the exploration to unstuck before the reset timeout
        val value = this.random.nextDouble()

        return if ((this.lastAction().actionType == ResetAppExplorationAction::class.simpleName) || (value > probability))
            StrategyPriority.NONE
        else
            StrategyPriority.BACK
    }

    override fun mustPerformMoreActions(currentState: StateData): Boolean {
        return false
    }

    override fun internalDecide(currentState: StateData): ExplorationAction {
        return newPressBackExplorationAction()
    }

    override fun equals(other: Any?): Boolean {
        return other is PressBack
    }

    override fun hashCode(): Int {
        return 0
    }

    override fun toString(): String {
        return "${this.javaClass}\tPriority: ${this.probability}"
    }

    override fun start() {
        // Nothing to do here.
    }

    companion object {
        /**
         * Creates a new exploration strategy instance
         */
        fun build(probability: Double, cfg: Configuration): ISelectableExplorationStrategy {
            return PressBack(probability, cfg.randomSeed.toLong())
        }
    }
}
