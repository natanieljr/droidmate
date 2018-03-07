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
import org.droidmate.exploration.actions.PressBackExplorationAction
import org.droidmate.exploration.actions.ResetAppExplorationAction
import org.droidmate.exploration.strategy.WidgetContext

/**
 * Determines if exploration shall be terminated based on the based on:
 *
 * - If exploration cannot move forward after reset. Resetting is supposed to unstuck exploration, and so if it doesn't help,
 * exploration cannot proceed forward at all.
 *
 * - A special case of the above, if exploration cannot move at the first time exploration strategy makes a decision. This
 * is a special case because first time exploration strategy makes a decision is immediately after the initial app launch,
 * which is technically also a kind of reset.
 *

 * @author Nataniel P. Borges Jr.
 */
class CannotExploreTerminate : Terminate() {
    override fun getLogMessage(): String = ""

    override fun met(widgetContext: WidgetContext): Boolean {
        // If the exploration cannot move forward after a reset + press back it should be terminated.

        return !widgetContext.explorationCanMoveForwardOn() &&
                this.lastAction() is ResetAppExplorationAction &&
                (this.getSecondLastAction() is PressBackExplorationAction ||
                        (widgetContext.guiState.isAppHasStoppedDialogBox))
        // or during initial attempt (just after first launch, which is also a reset) then it shall be terminated.
    }

    override fun start() {
        // Do nothing
    }

    override fun metReason(widgetContext: WidgetContext): String {
        val guiStateMsgPart = if (memory.isEmpty()) "Initial GUI state" else "GUI state after reset"

        // This case is observed when e.g. the app shows empty screen at startup.
        return if (!widgetContext.belongsToApp())
            "$guiStateMsgPart doesn't belong to the app. The GUI state: ${widgetContext.guiState}"
        // This case is observed when e.g. the app has nonstandard GUI, e.g. game native interface.
        // Also when all widgets have been blacklisted because they e.g. crash the app.
        else if (!widgetContext.hasActionableWidgets()) {
            "$guiStateMsgPart doesn't contain actionable widgets. The GUI state: ${widgetContext.guiState}"
        } else
            throw UnexpectedIfElseFallthroughError()
    }

    override fun equals(other: Any?): Boolean {
        return other is CannotExploreTerminate
    }

    override fun hashCode(): Int {
        return this.javaClass.hashCode()
    }
}