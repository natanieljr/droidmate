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

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.joinChildren
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.apis.ApiLogcatMessageListExtensions
import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.DeviceException
import org.droidmate.device.android_sdk.IAdbWrapper
import org.droidmate.device.android_sdk.IApk
import org.droidmate.deviceInterface.guimodel.ActionType
import org.droidmate.deviceInterface.guimodel.ExplorationAction
import org.droidmate.deviceInterface.guimodel.isLaunchApp
import org.droidmate.errors.DroidmateError
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.statemodel.*
import org.droidmate.exploration.statemodel.features.StatementCoverageMF
import org.droidmate.exploration.statemodel.features.ModelFeature
import org.droidmate.exploration.statemodel.features.CrashListMF
import org.droidmate.exploration.statemodel.features.ImgTraceMF
import org.droidmate.misc.TimeDiffWithTolerance
import java.awt.Rectangle
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
class ExplorationContext @JvmOverloads constructor(cfg: ConfigurationWrapper,
                                                   val apk: IApk,
                                                   adbWrapper: IAdbWrapper,
                                                   var explorationStartTime: LocalDateTime = LocalDateTime.MIN,
                                                   var explorationEndTime: LocalDateTime = LocalDateTime.MIN,
                                                   private val watcher: LinkedList<ModelFeature> = LinkedList(),
                                                   val _model: Model = Model.emptyModel(ModelConfig(appName = apk.packageName)),
                                                   val actionTrace: Trace = _model.initNewTrace(watcher)) {

	inline fun<reified T:ModelFeature> getOrCreateWatcher(): T
			= ( findWatcher{ it is T } ?: T::class.java.newInstance().also { addWatcher(it) } ) as T

	fun findWatcher(c: (ModelFeature)->Boolean) = watcher.find(c)

	fun<T:ModelFeature> addWatcher(w: T){ watcher.add(w); actionTrace.addWatcher(w) }

	val crashlist: CrashListMF = getOrCreateWatcher()
	val exceptionIsPresent: Boolean
		get() = exception !is DeviceExceptionMissing

	var exception: DeviceException = DeviceExceptionMissing()
	/**
	 * A rectangle representing visible device display. This is the same visible display from whose
	 * GUI structure this widget was parsed.
	 *
	 * The field is necessary to determine if at least one pixel of the widget is within the visible display and so, can be clicked.
	 *
	 * Later on DroidMate might add the ability to scroll first to make invisible widgets visible.
	 */
	var deviceDisplayBounds: Rectangle? = null
	/** for debugging purpose only contains the last UiAutomator dump */
	var lastDump: String = ""



	init {
		if (explorationEndTime > LocalDateTime.MIN)
			this.verify()
		if (_model.config[ConfigProperties.Core.debugMode]) watcher.add(ImgTraceMF(_model.config))
		if (_model.config[ConfigProperties.ModelProperties.Features.statementCoverage]) watcher.add(StatementCoverageMF(cfg, _model.config, adbWrapper))
	}

	fun getCurrentState(): StateData = actionTrace.currentState
	suspend fun getState(sId: ConcreteId) = _model.getState(sId)

	/** filters out all crashing marked widgets from the actionable widgets of the current state **/
	suspend fun nonCrashingWidgets() = getCurrentState().let{ s-> s.distinctTargets.filterNot { crashlist.isBlacklistedInState(it.uid,s.uid) } }

	fun belongsToApp(state: StateData): Boolean {
		return state.topNodePackageName == apk.packageName
	}

	fun add(action: ExplorationAction, result: ActionResult) {
		lastTarget = widgetTargets.last // this may be used by some strategies or ModelFeatures
		deviceDisplayBounds = Rectangle(result.guiSnapshot.deviceDisplayWidth, result.guiSnapshot.deviceDisplayHeight)
		lastDump = result.guiSnapshot.windowHierarchyDump

		assert(action.toString() == result.action.toString()) { "ERROR on ACTION-RESULT construction the wrong action was instantiated ${result.action} instead of $action"}
		_model.S_updateModel(result, actionTrace)
		this.also { context -> watcher.forEach { launch(it.context, parent = it.job) { it.onContextUpdate(context) } } }
	}

	fun dump() {
		_model.P_dumpModel(_model.config)
		this.also { context -> watcher.forEach { launch(CoroutineName("eContext-dump"), parent = ModelFeature.dumpJob) { it.dump(context) } } }

		// wait until all dump's completed
		runBlocking {
			println("dump models and watcher") //TODO Logger.info
			ModelFeature.dumpJob.joinChildren()
			_model.modelDumpJob.joinChildren()
			println("DONE - dump models and watcher")
		}
	}

	//TODO it may be more performing to have a list of all unexplored widgets and remove the ones chosen as target -> best done as ModelFeature
	// this could be nicely combined with the highlighting feature of the (numbered) img trace
	suspend fun areAllWidgetsExplored(): Boolean { // only consider widgets which belong to the app because there are insanely many keyboard/icon widgets available
		return actionTrace.size>0 && actionTrace.unexplored( _model.getWidgets().filter { it.packageName == apk.packageName && it.canBeActedUpon }).isEmpty()
	}

	/**
	 * Checks if any action has been performed
	 *
	 * @return If the eContext is empty
	 */
	fun isEmpty(): Boolean = actionTrace.size == 0
	fun explorationCanMoveOn() = isEmpty() || // we are starting the app -> no terminate yet
			(!getCurrentState().isHomeScreen && belongsToApp(getCurrentState()) && getCurrentState().actionableWidgets.isNotEmpty()) ||
			getCurrentState().isRequestRuntimePermissionDialogBox


	private fun assertLastGuiSnapshotIsHomeOrResultIsFailure() { runBlocking {
		actionTrace.last()?.let {
			assert(!it.successful || getCurrentState().isHomeScreen)
		}
	}}

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
	 * This method should be preferred to [getLastAction] as it does not have to wait for any other co-routines. */
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

	fun getModel(): Model {
		return _model
	}

	@Throws(DroidmateError::class)
	fun verify() {
		try {
			assert(this.actionTrace.size > 0)
			assert(this.explorationStartTime > LocalDateTime.MIN)
			assert(this.explorationEndTime > LocalDateTime.MIN)

			assertFirstActionIsLaunchApp()
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
		//TODO improve or remove if redundant
//		val lastResultSuccessful = FindReplaceUtility.getLastAction().successful
//		assert(lastResultSuccessful == (exception is DeviceExceptionMissing) || !lastResultSuccessful)
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

	private fun assertFirstActionIsLaunchApp() {
		assert(actionTrace.first().actionType.isLaunchApp()// || actionTrace.first().actionType == PlaybackResetAction::class.simpleName
		 )
	}

	private fun assertLastActionIsTerminateOrResultIsFailure() = runBlocking {
		actionTrace.last()?.let {
			assert(!it.successful || it.actionType == ActionType.Terminate.name)
		}
	}

}