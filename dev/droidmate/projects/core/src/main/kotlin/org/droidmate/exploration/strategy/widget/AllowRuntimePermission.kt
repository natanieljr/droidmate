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
package org.droidmate.exploration.strategy.widget

import org.droidmate.device.datatypes.RuntimePermissionDialogBoxGuiStatus
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.strategy.ISelectableExplorationStrategy
import org.droidmate.exploration.strategy.StrategyPriority
import org.droidmate.exploration.strategy.WidgetContext

/**
 * Exploration strategy that always clicks "Allow" on runtime permission dialogs.
 *
 * It has maximum priority (0.99) when it identifies a runtime permission dialog,
 * otherwise its priority is 0.
 */
class AllowRuntimePermission private constructor() : Explore() {
    override fun chooseAction(widgetContext: WidgetContext): ExplorationAction {
        val allowButton = memory.getCurrentState().widgets.let { widgets ->
            widgets.firstOrNull { it.resourceId == "com.android.packageinstaller:uid/permission_allow_button" }
                ?: widgets.first { it.text.toUpperCase() == "ALLOW" }
        }
//        val allowButton = (widgetContext.guiStatus as RuntimePermissionDialogBoxGuiStatus).allowWidget

        // Remove blacklist restriction from previous action since it will need to be executed again
//        this.memory.lastTarget.blackListed = false    //TODO

        return ExplorationAction.newIgnoreActionForTerminationWidgetExplorationAction(allowButton)
    }

    override fun getFitness(widgetContext: WidgetContext): StrategyPriority {
        // If the permission dialog appears this strategy should always have
        // preference unless competing against Terminate or First Reset
        return if (memory.getCurrentState().isRequestRuntimePermissionDialogBox)
            StrategyPriority.SPECIFIC_WIDGET
        else
            StrategyPriority.NONE
    }

    override fun equals(other: Any?): Boolean {
        return other is AllowRuntimePermission
    }

    override fun hashCode(): Int {
        return 0
    }

    override fun toString(): String {
        return "${this.javaClass}"
    }

    companion object {
        /**
         * Creates a new exploration strategy instance
         */
        fun build(): ISelectableExplorationStrategy {
            return AllowRuntimePermission()
        }
    }

}