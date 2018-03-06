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
import org.droidmate.device.datatypes.IDeviceGuiSnapshot
import org.droidmate.device.datatypes.IGuiState
import org.droidmate.errors.DroidmateError
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.strategy.*
import org.droidmate.storage.IStorage2
import java.time.Duration
import java.time.LocalDateTime

class ExplorationLog @JvmOverloads constructor(override val apk: IApk,
                                               override val logRecords: MutableList<ExplorationRecord> = ArrayList(),
                                               override var explorationStartTime: LocalDateTime = LocalDateTime.MIN,
                                               override var explorationEndTime: LocalDateTime = LocalDateTime.MIN) : IExplorationLog {
    companion object {
        private const val serialVersionUID: Long = 1
    }


    /**
     * List of distinct [UI contexts][WidgetContext] which have been found during the exploration
     */
    private var foundWidgetContexts: MutableList<WidgetContext> = ArrayList()

    override var exception: DeviceException = DeviceExceptionMissing()

    override var lastWidgetInfo: WidgetInfo = EmptyWidgetInfo()

    override val packageName: String
        get() = this.apk.packageName

    override val exceptionIsPresent: Boolean
        get() = exception !is DeviceExceptionMissing

    override val apiLogs: List<List<IApiLogcatMessage>>
        get() = this.logRecords.map { it.getResult().deviceLogs.apiLogs }

    override val actions: List<IRunnableExplorationAction>
        get() = this.logRecords.map { it.getAction() }


    override val guiSnapshots: List<IDeviceGuiSnapshot>
        get() = this.logRecords.map { it.getResult().guiSnapshot }

    init {
        if (explorationStartTime > LocalDateTime.MIN)
            this.verify()
    }

    private fun assertLogsAreSortedByTime() {
        val apiLogs = this.logRecords.flatMap { it.getResult().deviceLogs.apiLogs }

        assert(explorationStartTime <= explorationEndTime)

        val ret = ApiLogcatMessageListExtensions.sortedByTimePerPID(apiLogs)
        assert(ret)
    }

    private fun assertDeviceExceptionIsMissingOnSuccessAndPresentOnFailureNeverNull() {
        val lastResultSuccessful = logRecords.last().getResult().successful
        assert(lastResultSuccessful == (exception is DeviceExceptionMissing) || !lastResultSuccessful)
    }

    private fun assertOnlyLastActionMightHaveDeviceException() {
        assert(this.logRecords.dropLast(1).all { pair -> pair.getResult().successful })
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
        this.logRecords.forEach {
            if (!it.getResult().deviceLogs.apiLogs.isEmpty()) {
                val actionTime = it.getAction().timestamp
                val firstLogTime = it.getResult().deviceLogs.apiLogs.first().time
                diff.warnIfBeyond(actionTime, firstLogTime, "action time", "first log time for action", apkFileName)
            }
        }
    }

    private fun assertFirstActionIsReset() {
        assert(logRecords.first().getAction() is RunnableResetAppExplorationAction)
    }

    private fun assertLastActionIsTerminateOrResultIsFailure() {
        val lastActionPair = logRecords.last()
        assert(!lastActionPair.getResult().successful || lastActionPair.getAction() is RunnableTerminateExplorationAction)
    }

    private fun assertLastGuiSnapshotIsHomeOrResultIsFailure() {
        val lastActionPair = logRecords.last()
        assert(!lastActionPair.getResult().successful || lastActionPair.getResult().guiSnapshot.guiState.isHomeScreen)
    }

    override fun add(action: IRunnableExplorationAction, result: IMemoryRecord) {
        logRecords.add(ExplorationRecord(action, result))
    }

    override fun verify() {
        try {
            assert(this.logRecords.isNotEmpty())
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

    override fun getExplorationTimeInMs(): Int =
            Duration.between(explorationStartTime, explorationEndTime).toMillis().toInt()

    override fun getExplorationDuration(): Duration = Duration.between(explorationStartTime, explorationEndTime)

    override fun getWidgetContext(guiState: IGuiState): WidgetContext {
        val widgetInfo = guiState.widgets
                //.filter { it.canBeActedUpon() }
                .map { widget -> WidgetInfo.from(widget) }

        val newContext = WidgetContext(widgetInfo, guiState, this.apk.packageName)
        var context = this.foundWidgetContexts
                .firstOrNull { p -> p.uniqueString == newContext.uniqueString }

        if (context == null) {
            context = newContext
            this.foundWidgetContexts.add(context)
        }

        return context
    }

    override fun areAllWidgetsExplored(): Boolean {
        return (!this.isEmpty()) &&
                this.foundWidgetContexts.isNotEmpty() &&
                this.foundWidgetContexts.all { context ->
                    context.actionableWidgetsInfo.all { it.actedUponCount > 0 }
                }
    }

    override fun getLastAction(): IMemoryRecord {
        if (this.logRecords.isEmpty())
            return EmptyMemoryRecord()
        else
            return this.logRecords.last().getResult()
    }

    override fun getSize(): Int {
        return this.logRecords.size
    }

    override fun isEmpty(): Boolean {
        return this.logRecords.isEmpty()
    }

    override fun getRecords(): List<IMemoryRecord> {
        return this.logRecords.map { it.getResult() }
    }

    override fun serialize(storage2: IStorage2) {
        storage2.serialize(this, packageName)
    }
}