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
package org.droidmate.exploration.strategy.termination

import org.droidmate.exploration.actions.EmptyAction
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newTerminateExplorationAction
import org.droidmate.exploration.strategy.AbstractStrategy
import org.droidmate.exploration.strategy.StrategyPriority
import org.droidmate.exploration.strategy.WidgetContext
import org.droidmate.logging.Markers

/**
 * Determines if exploration shall be terminated based on the terminate criteria
 *
 * @author Nataniel P. Borges Jr.
 */
abstract class Terminate : AbstractStrategy() {

    protected fun getSecondLastAction(): ExplorationAction {
        if (this.memory.getSize() < 2)
            return EmptyAction()

        return this.memory.getRecords().dropLast(1).last().action
    }

    override fun getFitness(widgetContext: WidgetContext): StrategyPriority {
        if (this.met(widgetContext))
            return StrategyPriority.TERMINATE

        return StrategyPriority.NONE
    }

    override fun mustPerformMoreActions(widgetContext: WidgetContext): Boolean {
        return false
    }

    override fun internalDecide(widgetContext: WidgetContext): ExplorationAction {
        logger.info(Markers.appHealth, "Terminating exploration: ${this.metReason(widgetContext)}")
        return newTerminateExplorationAction()
    }

    override fun toString(): String {
        return "${this.javaClass}\t${this.getLogMessage()}"
    }

    abstract fun getLogMessage(): String
    abstract fun met(widgetContext: WidgetContext): Boolean
    abstract fun metReason(widgetContext: WidgetContext): String
}
