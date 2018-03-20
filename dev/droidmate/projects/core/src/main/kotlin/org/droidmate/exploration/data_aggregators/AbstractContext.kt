// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2017 Konrad Jamrozik
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
package org.droidmate.exploration.data_aggregators

import org.droidmate.TimeDiffWithTolerance
import org.droidmate.android_sdk.DeviceException
import org.droidmate.android_sdk.IApk
import org.droidmate.apis.ApiLogcatMessageListExtensions
import org.droidmate.apis.IApiLogcatMessage
import org.droidmate.device.datatypes.Widget
import org.droidmate.device.datatypes.statemodel.*
import org.droidmate.device.datatypes.statemodel.features.IModelFeature
import org.droidmate.errors.DroidmateError
import org.droidmate.exploration.actions.DeviceExceptionMissing
import org.droidmate.exploration.actions.IRunnableExplorationAction
import org.droidmate.exploration.actions.ResetAppExplorationAction
import org.droidmate.exploration.actions.TerminateExplorationAction
import org.droidmate.exploration.strategy.EmptyActionResult
import java.awt.Rectangle
import java.io.Serializable
import java.time.Duration
import java.time.LocalDateTime

/**
 * Exploration memory containing executed actionTrace, log (memory records), all explored widget contexts and
 * last explored widget
 *
 * @author Nataniel P. Borges Jr.
 */ //TODO cleanup code between ExplorationContext and IExplorationLog
abstract class AbstractContext : Serializable {
    abstract val model: Model
    abstract val watcher: List<IModelFeature>

    fun getState(sId: ConcreteId) = model.getState(sId)

    abstract fun add(action: IRunnableExplorationAction, result: ActionResult)

    abstract val explorationStartTime: LocalDateTime

    abstract var explorationEndTime: LocalDateTime

    /**
     * A rectangle representing visible device display. This is the same visible display from whose
     * GUI structure this widget was parsed.
     *
     * The field is necessary to determine if at least one pixel of the widget is within the visible display and so, can be clicked.
     *
     * Later on DroidMate might add the ability to scroll first to make invisible widgets visible.
     */
    abstract var deviceDisplayBounds: Rectangle?

    val exceptionIsPresent: Boolean
        get() = exception !is DeviceExceptionMissing

    var exception: DeviceException = DeviceExceptionMissing()

    /** for debugging purpose only contains the last UiAutomator dump */
    var lastDump: String = ""

    abstract val apk: IApk

    val apiLogs: List<List<IApiLogcatMessage>>  // TODO it may be more useful to have a map WidgetId->ApiLog, and why the heck is this List of List??
        get() = actionTrace.getActions().map { it.deviceLogs.apiLogs }

    abstract val actionTrace: Trace

    fun explorationCanMoveOn() = isEmpty() ||  // we are starting the app -> no terminate yet
            getCurrentState().topNodePackageName == apk.packageName && getCurrentState().actionableWidgets.isNotEmpty() ||
            getCurrentState().isRequestRuntimePermissionDialogBox

    /**
     * Get the last widget the exploration has interacted with
     *
     * @returns Last widget interacted with or instance of [EmptyWidgetInfo] when none
     */
    var lastTarget: Widget? = null

    /**
     * Returns the information of the last action performed
     *
     * @return Information of the last action performed or instance of [EmptyActionResult]
     */
    fun getLastAction(): ActionData = actionTrace.last() ?: ActionData.empty()

    /**
     * Get the exploration duration in miliseconds
     */
    fun getExplorationTimeInMs(): Int = Duration.between(explorationStartTime, explorationEndTime).toMillis().toInt()

    /**
     * Get the exploration duration
     */
    fun getExplorationDuration(): Duration = Duration.between(explorationStartTime, explorationEndTime)

    /**
     * Get the number of actionTrace which exist in the log
     */
    fun getSize(): Int = actionTrace.size

    /**
     * Checks if any action has been performed
     *
     * @return If the memory is empty
     */
    fun isEmpty(): Boolean = actionTrace.isEmpty()

    //	/**
//	 * Get the widget context referring to the [current UI][StateData] and to the
//	 * [top level package element on UIAutomator dump] exploredAppPackageName.
//	 *
//	 * Creates a new unique context when it doesn't exist.
//	 *
//	 * @return Unique widget context which refers to the current screen
//	 */
//    @Deprecated("use the Model or StateData instead to retrieve the required information")
//    fun getState(guiStatus: IGuiStatus): WidgetContext
    abstract fun getCurrentState(): StateData

    /**
     * Check if a state belongs to the app to which the context refers to
     *
     * @param state State to be checked
     */
    abstract fun belongsToApp(state: StateData): Boolean

    /**
     * Check if all widgets that have been found up to now have been already explored
     */
    @Deprecated("should be handled by ModelFeature with custom criteria instead")
    abstract fun areAllWidgetsExplored(): Boolean

    /**
     * Get data stored during this information
     *
     * @return List of memory records (or empty list when empty)
     */
    fun getRecords(): Model = model

    //    fun serialize(storage2: IStorage2)
    abstract fun dump()

    @Throws(DroidmateError::class)
    fun verify() {
        try {
            assert(this.actionTrace.isNotEmpty())
            assert(this.explorationStartTime > LocalDateTime.MIN)
            assert(this.explorationEndTime > LocalDateTime.MIN)

            assertFirstActionIsReset()
            assertLastActionIsTerminateOrResultIsFailure()
            assertLastGuiSnapshotIsHomeOrResultIsFailure()
            assertOnlyLastActionMightHaveDeviceException()
            assertDeviceExceptionIsMissingOnSuccessAndPresentOnFailureNeverNull()

            assertLogsAreSortedByTime()
            warnIfTimestampsAreIncorrectWithGivenTolerance()

        } catch (e: AssertionError) {
            throw DroidmateError(e)
        }
    }

    private fun assertLogsAreSortedByTime() {
        val apiLogs = actionTrace.getActions().flatMap { it.deviceLogs.apiLogs }

        assert(explorationStartTime <= explorationEndTime)

        val ret = ApiLogcatMessageListExtensions.sortedByTimePerPID(apiLogs)
        assert(ret)
    }

    private fun assertDeviceExceptionIsMissingOnSuccessAndPresentOnFailureNeverNull() {
        val lastResultSuccessful = getLastAction().successful
        assert(lastResultSuccessful == (exception is DeviceExceptionMissing) || !lastResultSuccessful)
    }

    private fun assertOnlyLastActionMightHaveDeviceException() {
        assert(actionTrace.getActions().dropLast(1).all { a -> a.successful })
    }

    private fun warnIfTimestampsAreIncorrectWithGivenTolerance() {
        /**
         * <p>
         * Used for time comparisons allowing for some imprecision.
         *
         * </p><p>
         * Some time comparisons in DroidMate happen between time obtained from an Android device and a time obtained from the machine
         * on which DroidMate runs. Because these two computers most likely won't have clocks synchronized with millisecond precision,
         * this variable is incorporated in such time comparisons.
         *
         * </p>
         */
        // KNOWN BUG I observed that sometimes exploration start time is more than 10 second later than first log time...
        // ...I was unable to identify the reason for that. Two reasons come to mind:
        // - the exploration log comes from previous exploration. This should not be possible because first logs are read at the end
        // of first reset exploration action, and logcat is cleared at the beginning of such reset exploration action.
        // Possible reason is that some logs from previous app exploration were pending to be output to logcat and have outputted
        // moments after logcat was cleared.
        // - the time diff on the device was different when the logcat messages were output, than the time diff measured by DroidMate.
        // This should not be of concern as manual inspection shows that the device time diff changes only a little bit over time,
        // far less than to justify sudden 10 second difference.
        val diff = TimeDiffWithTolerance(Duration.ofSeconds(5))
        warnIfExplorationStartTimeIsNotBeforeEndTime(diff, apk.fileName)
        warnIfExplorationStartTimeIsNotBeforeFirstLogTime(diff, apk.fileName)
        warnIfLastLogTimeIsNotBeforeExplorationEndTime(diff, apk.fileName)
        warnIfLogsAreNotAfterAction(diff, apk.fileName)
    }

    private fun warnIfExplorationStartTimeIsNotBeforeEndTime(diff: TimeDiffWithTolerance, apkFileName: String) {
        diff.warnIfBeyond(this.explorationStartTime, this.explorationEndTime, "exploration start time", "exploration end time", apkFileName)
    }

    private fun warnIfExplorationStartTimeIsNotBeforeFirstLogTime(diff: TimeDiffWithTolerance, apkFileName: String) {
        if (this.apiLogs.isNotEmpty()) {
            val firstLog = this.apiLogs.firstOrNull { it.isNotEmpty() }
            if (firstLog != null)
                diff.warnIfBeyond(this.explorationStartTime, firstLog.first().time, "exploration start time", "first API log", apkFileName)
        }
    }

    private fun warnIfLastLogTimeIsNotBeforeExplorationEndTime(diff: TimeDiffWithTolerance, apkFileName: String) {
        if (this.apiLogs.isNotEmpty()) {
            val lastLog = this.apiLogs.find { !it.isEmpty() }?.last()
            if (lastLog != null)
                diff.warnIfBeyond(lastLog.time, this.explorationEndTime, "last API log", "exploration end time", apkFileName)
        }
    }

    private fun warnIfLogsAreNotAfterAction(diff: TimeDiffWithTolerance, apkFileName: String) {
        actionTrace.getActions().forEach {
            if (!it.deviceLogs.apiLogs.isEmpty()) {
                val actionTime = it.startTimestamp
                val firstLogTime = it.deviceLogs.apiLogs.first().time
                diff.warnIfBeyond(actionTime, firstLogTime, "action time", "first log time for action", apkFileName)
            }
        }
    }

    private fun assertFirstActionIsReset() {
        assert(actionTrace.first().actionType == ResetAppExplorationAction::class.simpleName)
    }

    private fun assertLastActionIsTerminateOrResultIsFailure() {
        actionTrace.last()?.let {
            assert(!it.successful || it.actionType == TerminateExplorationAction::class.simpleName)
        }
    }

    abstract fun assertLastGuiSnapshotIsHomeOrResultIsFailure()
}