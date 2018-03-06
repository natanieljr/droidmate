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
package org.droidmate.exploration.strategy

import org.droidmate.device.datatypes.IGuiState
import java.io.Serializable

/**
 * State identified during the exploration. While the state is determined by the [application name][packageName]
 * and by the [list of all widgets that exist in the GUI][widgetsInfo], a state is uniquely identified based
 * on the set of actionable widgets it contains
 *
 * @author Nataniel P. Borges Jr.
 */
class WidgetContext constructor(val widgetsInfo: List<WidgetInfo>,
                                val guiState: IGuiState,
                                val packageName: String) : Serializable {
    /**
     * Number of times this state has been seen
     */
    var seenCount = 0

    /**
     * Unique identifier of the state. Considers: app name and unique identifier of all actionable widgets
     */
    val uniqueString: String
        get() {
            return packageName + " " + this.actionableWidgetsInfo.joinToString(" ") { it.uniqueString }
        }

    /**
     * Get the information of all actionable widgets in this context
     */
    val actionableWidgetsInfo: List<WidgetInfo>
        get() = widgetsInfo.filter { it.widget.canBeActedUpon() }

    /**
     * Checks if all widgets in this state have been blacklisted
     *
     * @return if all widgets have been blacklisted
     */
    fun allWidgetsBlacklisted(): Boolean {
        return this.actionableWidgetsInfo.isNotEmpty() && this.actionableWidgetsInfo.all { it.blackListed }
    }

    /**
     * Checks if the GUI state possess actionable widgets.
     *
     * A widget can be acted upon if:
     * - It's enabled
     * AND
     * - It's visible on current display
     * AND
     * - It's clickable, checkable or long clickable
     * AND
     * - [All widgets on screen are not blacklisted][WidgetContext.allWidgetsBlacklisted]
     *
     * @return If any widgets on the screen can be acted upon
     */
    fun hasActionableWidgets(): Boolean {
        return this.guiState.widgets.any { it.canBeActedUpon() } &&
                !this.allWidgetsBlacklisted()
    }

    /**
     * Checks if the exploration can perform a widget action on the current GUI.
     *
     * An exploration can perform an action if:
     * - GUI state belongs to the app (has same top level package element on UIAutomator dump)
     * AND
     * - GUI has actionable widgets
     *
     * OR
     * - GUI state is a runtime permission dialog
     *
     * @return If the exploration can perform a widget action
     */
    fun explorationCanMoveForwardOn(): Boolean {
        return this.belongsToApp() && this.hasActionableWidgets() ||
                guiState.isRequestRuntimePermissionDialogBox
    }

    /**
     * Checks if the current widget context belongs to the application under test.
     */
    fun belongsToApp(): Boolean {
        return guiState.belongsToApp(this.packageName)
    }

    /**
     * Check if the current screen is the device's home screen
     */
    fun isHomeScreen(): Boolean {
        return this.guiState.isHomeScreen
    }

    override fun toString(): String {
        return "WC:[seenCount=$seenCount, package=$packageName\n" +
                this.actionableWidgetsInfo.joinToString("\n") + "]"
    }

    companion object {
        @JvmStatic
        private val serialVersionUID = 1
    }
}