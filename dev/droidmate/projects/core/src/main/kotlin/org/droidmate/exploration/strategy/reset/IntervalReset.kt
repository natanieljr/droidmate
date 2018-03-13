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
package org.droidmate.exploration.strategy.reset

import org.droidmate.exploration.actions.ResetAppExplorationAction
import org.droidmate.exploration.strategy.IMemoryRecord
import org.droidmate.exploration.strategy.StrategyPriority
import org.droidmate.exploration.strategy.WidgetContext

/**
 * Reset the app on timed intervals to avoid getting stuck
 *
 * It doesn't reset if the last action was a reset
 *
 * @constructor Creates a new class instance with a [predefined reset interval][resetEveryNthExplorationForward]
 *
 * @author Nataniel P. Borges Jr.
 */
class IntervalReset constructor(private val resetEveryNthExplorationForward: Int) : Reset() {
    /**
     * Number of actions that have been performed since the last reset
     */
    private var nrActionsWithoutReset: Int = 0

    override fun getFitness(widgetContext: WidgetContext): StrategyPriority {
        // First action or following a reset
        if (this.lastAction() is ResetAppExplorationAction)
            return StrategyPriority.NONE

        // Reset due to predefined interval,
        // however, reset is never as good as a specific exploration
        if (this.nrActionsWithoutReset >= (this.resetEveryNthExplorationForward - 1))
            return StrategyPriority.RESET

        // Any other action
        return StrategyPriority.NONE
    }

    override fun updateState(actionNr: Int, record: IMemoryRecord) {
        super.updateState(actionNr, record)

        val lastAction = record.action

        if (lastAction !is ResetAppExplorationAction)
            this.nrActionsWithoutReset++
        else
            this.nrActionsWithoutReset = 0
    }

    override fun equals(other: Any?): Boolean {
        if (other !is IntervalReset)
            return false

        return other.resetEveryNthExplorationForward == this.resetEveryNthExplorationForward
    }

    override fun toString(): String {
        return "${this.javaClass}\tInterval: ${this.resetEveryNthExplorationForward}"
    }

    override fun hashCode(): Int {
        return Integer.valueOf(this.resetEveryNthExplorationForward)!!.hashCode()
    }
}