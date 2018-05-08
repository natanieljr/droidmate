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
package org.droidmate.exploration

import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.misc.TimeDiffWithTolerance
import org.droidmate.device.android_sdk.DeviceException
import org.droidmate.device.android_sdk.IApk
import org.droidmate.apis.ApiLogcatMessageListExtensions
import org.droidmate.exploration.statemodel.*
import org.droidmate.exploration.statemodel.features.ModelFeature
import org.droidmate.errors.DroidmateError
import org.droidmate.exploration.actions.DeviceExceptionMissing
import org.droidmate.exploration.actions.IRunnableExplorationAction
import org.droidmate.exploration.actions.ResetAppExplorationAction
import org.droidmate.exploration.actions.TerminateExplorationAction
import org.droidmate.exploration.statemodel.ConcreteId
import org.droidmate.exploration.statemodel.features.CrashListMF
import org.droidmate.exploration.strategy.EmptyActionResult
import org.droidmate.exploration.strategy.playback.PlaybackResetAction
import java.awt.Rectangle
import java.io.Serializable
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * Exploration context containing executed actionTrace, log (context records), all explored widget contexts and
 * last explored widget
 *
 * @author Nataniel P. Borges Jr.
 */ //TODO cleanup code between ExplorationContext and IExplorationLog
abstract class AbstractContext : Serializable {
	protected abstract val _model: Model
	abstract val watcher: LinkedList<ModelFeature>

	abstract val crashlist:CrashListMF

	inline fun<reified T:ModelFeature> getOrCreateWatcher(): T
		= (watcher.find { it is T } ?: T::class.java.newInstance().also { watcher.add(it) }) as T

	suspend fun getState(sId: ConcreteId) = _model.getState(sId)

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

	abstract val actionTrace: Trace

	/** filters out all crashing marked widgets from the actionable widgets of the current state **/
	suspend fun nonCrashingWidgets() = getCurrentState().let{ s-> s.actionableWidgets.filterNot { crashlist.isBlacklistedInState(it.uid,s.uid) } }

	fun explorationCanMoveOn() = isEmpty() || // we are starting the app -> no terminate yet
			(!getCurrentState().isHomeScreen && getCurrentState().topNodePackageName == apk.packageName && getCurrentState().actionableWidgets.isNotEmpty()) ||
			getCurrentState().isRequestRuntimePermissionDialogBox

	/**
	 * Get the last widget the exploration has interacted with
	 * REMARK: currently the executed ExplorationStrategy is responsible to write to this value
	 *
	 * @returns Last widget interacted with or null when none
	 */
	var lastTarget: Widget? = null

	/**
	 * Returns the information of the last action performed
	 *
	 * @return Information of the last action performed or instance of [EmptyActionResult]
	 */
	fun getLastAction(): ActionData = runBlocking { actionTrace.last() } ?: ActionData.empty
	/** @returns the name of the last executed action.
	 * This method should be prefered to [getLastAction] as it does not have to wait for any other coroutines. */
	fun getLastActionType(): String = actionTrace.lastActionType

	/**
	 * Get the exploration duration in milliseconds
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
	 * @return If the context is empty
	 */
	fun isEmpty(): Boolean = actionTrace.size == 0

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
	 * REMARK could probably be better handled by ModelFeature with custom criteria instead
	 */
	abstract suspend fun areAllWidgetsExplored(): Boolean

	fun getModel(): Model {
		return _model
	}
	//    fun serialize(storage2: IStorage2)
	abstract fun dump()

	@Throws(DroidmateError::class)
	fun verify() {
		try {
			assert(this.actionTrace.size > 0)
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
		if (!this.isEmpty()) {
			val firstActionWithLog = this.actionTrace.getActions().firstOrNull { it.deviceLogs.apiLogs.isNotEmpty() }
			val firstLog = firstActionWithLog?.deviceLogs?.apiLogs?.firstOrNull()
			if (firstLog != null)
				diff.warnIfBeyond(this.explorationStartTime, firstLog.time, "exploration start time", "first API log", apkFileName)
		}
	}

	private fun warnIfLastLogTimeIsNotBeforeExplorationEndTime(diff: TimeDiffWithTolerance, apkFileName: String) {
		if (!this.isEmpty()) {
			val lastActionWithLog = this.actionTrace.getActions().lastOrNull { it.deviceLogs.apiLogs.isNotEmpty() }
			val lastLog = lastActionWithLog?.deviceLogs?.apiLogs?.lastOrNull()
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
		assert(actionTrace.first().actionType == ResetAppExplorationAction::class.simpleName || actionTrace.first().actionType == PlaybackResetAction::class.simpleName)
	}

	private fun assertLastActionIsTerminateOrResultIsFailure() = runBlocking {
		actionTrace.last()?.let {
			assert(!it.successful || it.actionType == TerminateExplorationAction::class.simpleName)
		}
	}

	abstract fun assertLastGuiSnapshotIsHomeOrResultIsFailure()
}