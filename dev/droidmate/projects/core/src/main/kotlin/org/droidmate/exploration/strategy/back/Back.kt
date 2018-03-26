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

import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.ExplorationAction.Companion.newPressBackExplorationAction
import org.droidmate.exploration.strategy.AbstractStrategy
import org.droidmate.exploration.strategy.WidgetContext

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
abstract class Back : AbstractStrategy() {
    override fun mustPerformMoreActions(widgetContext: WidgetContext): Boolean {
        return false
    }

    override fun internalDecide(widgetContext: WidgetContext): ExplorationAction {
        return newPressBackExplorationAction()
    }

    override fun toString(): String {
        return this.javaClass.toString()
    }

    override fun start() {
        // Nothing to do here.
    }
}
