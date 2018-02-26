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

import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newTerminateExplorationAction
import org.droidmate.exploration.strategy.*
import org.droidmate.logging.Markers

/**
 * Determines if exploration shall be terminated. Obviously, exploration shall be terminated when the terminationCriterion is
 * met. However, two more cases justify termination:
 *
 * - If exploration cannot move forward after reset. Resetting is supposed to unstuck exploration, and so if it doesn't help,
 * exploration cannot proceed forward at all.
 *
 * - A special case of the above, if exploration cannot move at the first time exploration strategy makes a decision. This
 * is a special case because first time exploration strategy makes a decision is immediately after the initial app launch,
 * which is technically also a kind of reset.
 *
 * @constructor Creates a new class instance using a [predetermined termination criterion][terminationCriterion]
 *
 * @author Nataniel P. Borges Jr.
 */
class Terminate private constructor(private val terminationCriterion: ITerminationCriterion) : AbstractStrategy() {

    override val type: ExplorationType
        get() = ExplorationType.Terminate

    private fun getSecondLastActionType(): ExplorationType {
        if (this.memory.getSize() < 2)
            return ExplorationType.None

        return this.memory.getRecords().dropLast(1).last().type
    }

    override fun getFitness(widgetContext: WidgetContext): StrategyPriority {
        if (this.terminationCriterion.met())
            return StrategyPriority.TERMINATE

        // If the exploration cannot move forward after reset or during initial attempt (just after first launch,
        // which is also a reset) then it shall be terminated.
        if (!widgetContext.explorationCanMoveForwardOn() &&
                lastActionWasOfType(ExplorationType.Reset) &&
                this.getSecondLastActionType() == ExplorationType.Back)
            return StrategyPriority.TERMINATE

        // All widgets have been explored, no need to continue exploration
        if (memory.areAllWidgetsExplored())
            return StrategyPriority.TERMINATE

        return StrategyPriority.NONE
    }

    override fun mustPerformMoreActions(widgetContext: WidgetContext): Boolean {
        return false
    }

    override fun internalDecide(widgetContext: WidgetContext): ExplorationAction {
        // If the exploration cannot move forward after reset or during initial attempt (just after first launch,
        // which is also a reset) then it shall be terminated.
        if (!this.terminationCriterion.met() &&
                !widgetContext.explorationCanMoveForwardOn() &&
                (lastActionWasOfType(ExplorationType.Reset) || firstDecisionIsBeingMade())) {
            val guiStateMsgPart = if (firstDecisionIsBeingMade()) "Initial GUI state" else "GUI state after reset"

            // This case is observed when e.g. the app shows empty screen at startup.
            if (!widgetContext.belongsToApp())
                logger.info(Markers.appHealth, "Terminating exploration: $guiStateMsgPart doesn't belong to the app. The GUI state: ${widgetContext.guiState}")
            else if (!widgetContext.hasActionableWidgets()) {
                logger.info(Markers.appHealth, "Terminating exploration: $guiStateMsgPart doesn't contain actionable widgets. The GUI state: ${widgetContext.guiState}")
                // log.info(guiState.debugWidgets())
            } else
                throw UnexpectedIfElseFallthroughError()// This case is observed when e.g. the app has nonstandard GUI, e.g. game native interface.
            // Also when all widgets have been blacklisted because they e.g. crash the app.
        }

        return newTerminateExplorationAction()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Terminate)
            return false

        return this.terminationCriterion == other.terminationCriterion
    }

    override fun toString(): String {
        return "${this.javaClass}\t${this.terminationCriterion}"
    }

    override fun hashCode(): Int {
        return this.terminationCriterion.hashCode()
    }

    override fun start() {
        this.terminationCriterion.initDecideCall(true)
    }

    override fun updateState(actionNr: Int) {
        super.updateState(actionNr)

        if (!this.memory.isEmpty()) {
            val selectedAction = this.memory.getLastAction()?.action!!
            this.terminationCriterion.updateState()
            this.terminationCriterion.assertPostDecide(selectedAction)
        }
    }

    companion object {
        /**
         * Creates a new exploration strategy instance
         */
        fun build(terminationCriterion: ITerminationCriterion): ISelectableExplorationStrategy {
            return Terminate(terminationCriterion)
        }
    }

}
