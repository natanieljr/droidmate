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
import org.droidmate.errors.DroidmateError
import org.droidmate.exploration.actions.*
import org.droidmate.storage.IStorage2
import java.time.Duration
import java.time.LocalDateTime

class ApkExplorationOutput2 @JvmOverloads constructor(override val apk: IApk,
                                                      override val actRes: MutableList<RunnableExplorationActionWithResult> = ArrayList(),
                                                      override var explorationStartTime: LocalDateTime = LocalDateTime.MIN,
                                                      override var explorationEndTime: LocalDateTime = LocalDateTime.MIN) : IApkExplorationOutput2 {
    companion object {
        private const val serialVersionUID: Long = 1
    }

    override var exception: DeviceException = DeviceExceptionMissing()

    init {
        if (explorationStartTime > LocalDateTime.MIN)
            this.verify()
    }

    override val packageName: String
        get() = this.apk.packageName

    override fun add(action: IRunnableExplorationAction, result: IExplorationActionRunResult) {
        actRes.add(RunnableExplorationActionWithResult(action, result))
    }

    override fun verify() {
        try {
            assert(this.actRes.isNotEmpty())
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
        val apiLogs = this.actRes.flatMap { it.getResult().deviceLogs.apiLogs }
        //TODO Nataniel Review later
        //val apiLogsSortedTimes = apiLogs.map { it.time }.sorted()

        assert(explorationStartTime <= explorationEndTime)

        val ret = ApiLogcatMessageListExtensions.sortedByTimePerPID(apiLogs)
        assert(ret)
    }

    private fun assertDeviceExceptionIsMissingOnSuccessAndPresentOnFailureNeverNull() {
        val lastResultSuccessful = actRes.last().getResult().successful
        assert(lastResultSuccessful == (exception is DeviceExceptionMissing) || !lastResultSuccessful)
    }

    private fun assertOnlyLastActionMightHaveDeviceException() {
        assert(this.actRes.dropLast(1).all { pair -> pair.getResult().successful })
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
        this.actRes.forEach {
            if (!it.getResult().deviceLogs.apiLogs.isEmpty()) {
                val actionTime = it.getAction().timestamp
                val firstLogTime = it.getResult().deviceLogs.apiLogs.first().time
                diff.warnIfBeyond(actionTime, firstLogTime, "action time", "first log time for action", apkFileName)
            }
        }
    }

    override fun getExplorationTimeInMs(): Int =
            Duration.between(explorationStartTime, explorationEndTime).toMillis().toInt()

    override fun getExplorationDuration(): Duration = Duration.between(explorationStartTime, explorationEndTime)

    override fun getContainsExplorationStartTime(): Boolean = this.explorationStartTime > LocalDateTime.MIN

    override fun getContainsExplorationEndTime(): Boolean = this.explorationEndTime > LocalDateTime.MIN


    override val exceptionIsPresent: Boolean
        get() = exception !is DeviceExceptionMissing

    override val apiLogs: List<List<IApiLogcatMessage>>
        get() = this.actRes.map { it.getResult().deviceLogs.apiLogs }

    override val actions: List<IRunnableExplorationAction>
        get() = this.actRes.map { it.getAction() }


    override val guiSnapshots: List<IDeviceGuiSnapshot>
        get() = this.actRes.map { it.getResult().guiSnapshot }

    private fun assertFirstActionIsReset() {
        assert(actRes.first().getAction() is RunnableResetAppExplorationAction)
    }

    private fun assertLastActionIsTerminateOrResultIsFailure() {
        val lastActionPair = actRes.last()
        assert(!lastActionPair.getResult().successful || lastActionPair.getAction() is RunnableTerminateExplorationAction)
    }

    private fun assertLastGuiSnapshotIsHomeOrResultIsFailure() {
        val lastActionPair = actRes.last()
        assert(!lastActionPair.getResult().successful || lastActionPair.getResult().guiSnapshot.guiState.isHomeScreen)
    }

    override fun serialize(storage2: IStorage2) {
        storage2.serialize(this, packageName)
    }
}