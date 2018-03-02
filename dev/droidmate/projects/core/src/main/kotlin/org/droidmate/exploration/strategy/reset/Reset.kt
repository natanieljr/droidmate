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

import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newResetAppExplorationAction
import org.droidmate.exploration.strategy.AbstractStrategy
import org.droidmate.exploration.strategy.ExplorationType
import org.droidmate.exploration.strategy.WidgetContext

/**
 * Base for exploration strategies that reset and exploration.
 *
 * @author Nataniel P. Borges Jr.
 */
abstract class Reset protected constructor() : AbstractStrategy() {
    override val type: ExplorationType
        get() = ExplorationType.Reset

    override fun internalDecide(widgetContext: WidgetContext): ExplorationAction {
        // There' no previous widget after a reset
        this.memory.lastWidgetInfo = null

        return newResetAppExplorationAction()
    }

    override fun mustPerformMoreActions(widgetContext: WidgetContext): Boolean {
        return false
    }

    override fun toString(): String {
        return this.javaClass.toString()
    }

    override fun hashCode(): Int {
        return this.javaClass.hashCode()
    }

    override fun start() {
        // Nothing to do here.
    }
}