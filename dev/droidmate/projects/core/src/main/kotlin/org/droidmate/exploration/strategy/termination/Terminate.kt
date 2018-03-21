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
package org.droidmate.exploration.strategy.termination

import org.droidmate.device.datatypes.statemodel.StateData
import org.droidmate.exploration.actions.EmptyAction
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newTerminateExplorationAction
import org.droidmate.exploration.strategy.AbstractStrategy
import org.droidmate.exploration.strategy.StrategyPriority
import org.droidmate.logging.Markers

/**
 * Determines if exploration shall be terminated based on the terminate criteria
 *
 * @author Nataniel P. Borges Jr.
 */
abstract class Terminate : AbstractStrategy() {

    protected fun getSecondLastAction(): String {
        if (this.memory.getSize() < 2)
            return EmptyAction()::class.simpleName?:""

        return this.memory.actionTrace.getActions().dropLast(1).last().actionType?:""
    }

    override fun getFitness(currentState: StateData): StrategyPriority {
        if (this.met(currentState))
            return StrategyPriority.TERMINATE

        return StrategyPriority.NONE
    }

    override fun mustPerformMoreActions(currentState: StateData): Boolean {
        return false
    }

    override fun internalDecide(currentState: StateData): ExplorationAction {
        logger.info(Markers.appHealth, "Terminating exploration: ${this.metReason(currentState)}")
        return newTerminateExplorationAction()
    }

    override fun toString(): String {
        return "${this.javaClass}\t${this.getLogMessage()}"
    }

    abstract fun getLogMessage(): String
    abstract fun met(currentState: StateData): Boolean
    abstract fun metReason(currentState: StateData): String

    /*companion object {
        /**
         * Creates a new exploration strategy instance
         */
        fun build(terminationCriterion: ITerminationCriterion): ISelectableExplorationStrategy {
            return Terminate(terminationCriterion)
        }
    }*/
}
