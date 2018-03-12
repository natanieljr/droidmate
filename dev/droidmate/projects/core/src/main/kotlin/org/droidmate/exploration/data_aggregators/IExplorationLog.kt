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
package org.droidmate.exploration.data_aggregators

import org.droidmate.android_sdk.DeviceException
import org.droidmate.android_sdk.IApk
import org.droidmate.apis.IApiLogcatMessage
import org.droidmate.device.datatypes.IDeviceGuiSnapshot
import org.droidmate.device.datatypes.IGuiState
import org.droidmate.errors.DroidmateError
import org.droidmate.exploration.actions.ExplorationRecord
import org.droidmate.exploration.actions.IRunnableExplorationAction
import org.droidmate.exploration.strategy.*
import org.droidmate.storage.IStorage2
import java.io.Serializable
import java.time.Duration
import java.time.LocalDateTime

/**
 * Exploration memory containing executed actions, log (memory records), all explored widget contexts and
 * last explored widget
 *
 * @author Nataniel P. Borges Jr.
 */
interface IExplorationLog : Serializable {

    fun add(action: IRunnableExplorationAction, result: IMemoryRecord)

    var explorationStartTime: LocalDateTime

    var explorationEndTime: LocalDateTime

    /**
     * List of [GUI states and actions][IMemoryRecord] which were sent to the device
     */
    val logRecords: MutableList<ExplorationRecord>

    val exceptionIsPresent: Boolean

    var exception: DeviceException

    val apk: IApk

    val packageName: String

    val apiLogs: List<List<IApiLogcatMessage>>

    val actions: List<IRunnableExplorationAction>

    val guiSnapshots: List<IDeviceGuiSnapshot>

    /**
     * Get the last widget the exploration has interacted with
     *
     * @returns Last widget interacted with or instance of [EmptyWidgetInfo] when none
     */
    var lastWidgetInfo: WidgetInfo

    /**
     * Returns the information of the last action performed
     *
     * @return Information of the last action performed or instance of [EmptyMemoryRecord]
     */
    fun getLastAction(): IMemoryRecord

    @Throws(DroidmateError::class)
    fun verify()

    /**
     * Get the exploration duration in miliseconds
     */
    fun getExplorationTimeInMs(): Int

    /**
     * Get the exploration duration
     */
    fun getExplorationDuration(): Duration

    /**
     * Get the number of actions which exist in the log
     */
    fun getSize(): Int

    /**
     * Checks if any action has been performed
     *
     * @return If the memory is empty
     */
    fun isEmpty(): Boolean

    /**
     * Get the widget context referring to the [current UI][guiState] and to the
     * [top level package element on UIAutomator dump][exploredAppPackageName].
     *
     * Creates a new unique context when it doesn't exist.
     *
     * @return Unique widget context which refers to the current screen
     */
    fun getWidgetContext(guiState: IGuiState): WidgetContext

    /**
     * Check if all widgets that have been found up to now have been already explored
     */
    fun areAllWidgetsExplored(): Boolean

    /**
     * Get data stored during this information
     *
     * @return List of memory records (or empty list when empty)
     */
    fun getRecords(): List<IMemoryRecord>

    fun serialize(storage2: IStorage2)
}