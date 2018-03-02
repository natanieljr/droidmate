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
package org.droidmate.exploration.strategy.termination.criterion

import org.droidmate.configuration.Configuration
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.TerminateExplorationAction
import org.droidmate.exploration.strategy.ITerminationCriterion

/**
 * Termination termination based on number of actions performed
 *
 * @author Nataniel P. Borges Jr.
 */
class ActionBasedCriterion internal constructor(config: Configuration) : ITerminationCriterion {
    private val startingActionsLeft: Int
    private var actionsLeft: Int = 0

    init {
        if (config.widgetIndexes.isNotEmpty())
            this.startingActionsLeft = config.widgetIndexes.size
        else
            this.startingActionsLeft = config.actionsLimit

        this.actionsLeft = this.startingActionsLeft
    }

    override fun getLogMessage(): String {
        return "${this.startingActionsLeft - this.actionsLeft}/${this.startingActionsLeft}"
    }

    override fun initDecideCall(firstCallToDecideFinished: Boolean) {
        assert(actionsLeft >= 0)
    }

    override fun assertPostDecide(outExplAction: ExplorationAction) {
        assert(actionsLeft >= 0 || actionsLeft == -1 && outExplAction is TerminateExplorationAction)
    }

    override fun met(): Boolean {
        return actionsLeft <= 0
    }

    override fun metReason(): String {
        return "No actions left."
    }

    override fun updateState() {
        assert(met() || actionsLeft > 0)
        actionsLeft--
    }

    override fun toString(): String {
        return "${this.javaClass}\t${this.getLogMessage()}"
    }
}
