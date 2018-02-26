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

import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.IExplorationActionRunResult
import java.time.LocalDateTime

/**
 * Interface for a memory record which stores the performed action, alongside the GUI state before the action
 *
 * @author Nataniel P. Borges Jr.
 */
interface IMemoryRecord {
    /**
     * Action which was sent to DroidMate
     */
    val action: ExplorationAction

    /**
     * Type of exploration strategy that created the action
     */
    val type: ExplorationType

    /**
     * GUI state before action execution
     */
    val widgetContext: WidgetContext

    /**
     * Time the strategy pool took to select a strategy and a create an action
     * (used to measure overhead for new exploration strategies)
     */
    val decisionTime: Long

    /**
     * Time the action selection started
     * (used to sync logcat)
     */
    val startTimestamp: LocalDateTime

    /**
     * Time the action selection started
     * (used to sync logcat)
     */
    val endTimestamp: LocalDateTime

    /**
     * Results of executing the exploration action on the device
     */
    var actionResult: IExplorationActionRunResult?
}
