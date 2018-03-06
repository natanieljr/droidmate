// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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

package org.droidmate.tests.exploration.strategy.stubs

import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.strategy.*

/**
 * Test exploration strategy that does three actions before handling control
 * back to main exploration
 */
class TripleActionExploration : AbstractStrategy() {

    override fun start() {
        // Nothing to do here.
    }

    override fun getFitness(widgetContext: WidgetContext): StrategyPriority {
        if (this.actionNr == 3)
            return StrategyPriority.SPECIFIC_WIDGET

        return StrategyPriority.NONE
    }

    override fun mustPerformMoreActions(widgetContext: WidgetContext): Boolean {
        return this.actionNr >= 3 && this.actionNr < 5
    }

    override fun internalDecide(widgetContext: WidgetContext): ExplorationAction {
        return DummyExplorationAction()
    }

    override fun equals(other: Any?): Boolean {
        return other is TripleActionExploration
    }

    override fun hashCode(): Int {
        return 0
    }

    companion object {
        fun build(): ISelectableExplorationStrategy {
            return TripleActionExploration()
        }
    }
}
