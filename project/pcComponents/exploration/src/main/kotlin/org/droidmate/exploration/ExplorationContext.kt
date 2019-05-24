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

import com.natpryce.konfig.Configuration
import kotlinx.coroutines.*
import org.droidmate.configuration.ConfigProperties
import org.droidmate.device.android_sdk.IApk
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.explorationModel.*
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.exploration.modelFeatures.explorationWatchers.CrashListMF
import org.droidmate.exploration.modelFeatures.ModelFeature
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF.Companion.StatementCoverage.coverageDir
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF.Companion.StatementCoverage.enableCoverage
import org.droidmate.explorationModel.interaction.*
import org.droidmate.misc.TimeDiffWithTolerance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.math.min
import org.droidmate.explorationModel.config.ConfigProperties.Output.debugMode
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.misc.EnvironmentConstants
import java.nio.file.Paths

@Suppress("MemberVisibilityCanBePrivate")
class ExplorationContext<M,S,W> @JvmOverloads constructor(val cfg: Configuration,
                                                      val apk: IApk,
                                                      readDeviceStatements: suspend ()-> List<List<String>>,
                                                      val explorationStartTime: LocalDateTime = LocalDateTime.MIN,
                                                      var explorationEndTime: LocalDateTime = LocalDateTime.MIN,
                                                      private val watcher: MutableList<ModelFeatureI> = mutableListOf(),
                                                      val model: M
                                                      )
		where M: AbstractModel<S, W>, S: State<W>, W: Widget {

	val explorationTrace: ExplorationTrace<S,W> = model.initNewTrace(watcher)

	companion object {
		@JvmStatic
		val log: Logger by lazy { LoggerFactory.getLogger(ExplorationContext::class.qualifiedName) }
	}

	val retention: CoroutineScope = CoroutineScope(CoroutineName("MF-Dump")) + SupervisorJob()
	val imgTransfer = CoroutineScope(SupervisorJob() + CoroutineName("device image pull") + Dispatchers.IO)

	inline fun<reified T:ModelFeature> getOrCreateWatcher(): T
			= ( findWatcher{ it is T } ?: T::class.java.newInstance().also { addWatcher(it) } ) as T

	fun findWatcher(c: (ModelFeatureI)->Boolean) = watcher.find(c)

	fun<T:ModelFeature> addWatcher(w: T){
		explorationTrace.addWatcher(w)
		w.onAppExplorationStarted(this)
	}

	val crashlist: CrashListMF by lazy { getOrCreateWatcher<CrashListMF>() }

	var exceptions: MutableList<Throwable> = ArrayList(10)  // expected to be very small and according to google guidelines java arraylist is on average more efficient then LinkedList
	/** for debugging purpose only contains the last UiAutomator dump */
	var lastDump: String = ""



	init {
		debugOutput = model.config[debugMode] // disable debug outputs if not in debug mode
		measurePerformance = model.config[debugMode]
		if (model.config[enableCoverage]){
			val coverageDir = Paths.get(cfg[ConfigProperties.Output.outputDir].path).toAbsolutePath().resolve(cfg[coverageDir]).toAbsolutePath()
			val resourceDir = Paths.get(cfg[ConfigProperties.Output.outputDir].path).toAbsolutePath().resolve(EnvironmentConstants.dir_name_temp_extracted_resources).toAbsolutePath()
			addWatcher(StatementCoverageMF(coverageDir, readDeviceStatements, model.config.appName, resourceDir))
		}
	}

	fun getCurrentState(): S = explorationTrace.currentState
	suspend fun getState(sId: ConcreteId) = model.getState(sId)

	/** filters out all crashing marked widgets from the actionable widgets of the current state **/
	suspend fun Collection<Widget>.nonCrashingWidgets() = filterNot { crashlist.isBlacklistedInState(it.uid,getCurrentState().uid) }

	fun<S: State<*>> belongsToApp(state: S): Boolean {
		return state.widgets.any { it.packageName == apk.packageName
				|| it.packageName == "com.google.android.gms" }  // allow googles internal log-in screen
	}

	suspend fun update(action: ExplorationAction, result: ActionResult) {
		lastDump = result.guiSnapshot.windowHierarchyDump
		apk.updateLaunchableActivityName(result.guiSnapshot.launchedMainActivityName)

		assert(action.toString() == result.action.toString()) { "ERROR on ACTION-RESULT construction the wrong action was instantiated ${result.action} instead of $action"}
		model.updateModel(result, explorationTrace)
		this.also { context ->
			lastTarget = explorationTrace.getExploredWidgets().lastOrNull() // this may be used by some strategies or ModelFeatures
			watcher.forEach { feature ->
				(feature as? ModelFeature)?.let {
					it.launch { it.onContextUpdate(context) }
				}
			}
		}
	}

	@Suppress("ReplaceSingleLineLet")
	suspend fun close() = this.let { eContext ->
		coroutineScope {
			// we use coroutineScope here to ensure that this function waits for all coroutines spawned within this method
			log.info("finishing context updates, dumping data and restarting modelFeatures")
			dump()

			// can use the same auxiliary job as the dump function, as it's already free
			log.info("preparing modelFeatures for next app")
			watcher.forEach { feature ->
				(feature as? ModelFeature)?.let { // this is meant to be in the current coroutineScope and not in feature, such this scope waits for its completion
					launch(CoroutineName("eContext-finish")) {
						it.onAppExplorationFinished(eContext)
					}
				}
				launch { feature.cancelAndJoin() } // terminate/restart all model features
			}
			retention.coroutineContext.cancel() // terminate any unfinished ModelFeature-dump coroutines

			model.cancelAndJoin()  // ensure the model has persisted all required data and cancel  all (child)scopes
			log.debug("DONE - app finished notification")
		}
	}

	suspend fun dump() = this.let{ eContext ->
		log.info("dump models and watcher")
		model.dumpModel(model.config)

		watcher.forEach { feature ->
			retention.launch(CoroutineName("eContext-dump")) { // this called on "retention scope" to allow for easy feature task synchronization
				(feature as? ModelFeature)?.dump(eContext)
						?: feature.dump() // for features without exploration context (ModelFeatureI) instances
			}
		}
		log.debug("DONE - dump models and watcher")
	}


	//TODO it may be more performing to have a list of all unexplored widgets and remove the ones chosen as target -> best done as ModelFeature
	// this could be nicely combined with the highlighting feature of the (numbered) img trace
	suspend fun areAllWidgetsExplored(): Boolean { // only consider widgets which belong to the app because there are insanely many keyboard/icon widgets available
		return explorationTrace.size>0 && explorationTrace.unexplored( model.getWidgets().filter { it.packageName == apk.packageName && it.isInteractive }).isEmpty()
	}

	/**
	 * Checks if any action has been performed
	 *
	 * @return If the eContext is empty
	 */
	fun isEmpty(): Boolean = explorationTrace.size == 0
	fun explorationCanMoveOn() = isEmpty() || // we are starting the app -> no terminate yet
			getCurrentState().isRequestRuntimePermissionDialogBox ||  // FIXME what if we currently have isHomeScreen?
			(!getCurrentState().isHomeScreen && belongsToApp(getCurrentState()) && getCurrentState().actionableWidgets.isNotEmpty())



	suspend fun assertLastGuiSnapshotIsHomeOrResultIsFailure() {
		explorationTrace.last()?.let {
			assert(!it.successful || getCurrentState().isHomeScreen)
		}
	}

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
	fun getLastAction() = runBlocking { explorationTrace.last() } ?: Interaction.empty()
	val emptyAction by lazy{ Interaction.empty<W>() }
	/** @returns the name of the last executed action.
	 * This method should be preferred to [getLastAction] as it does not have to wait for any other co-routines. */
	fun getLastActionType(): String = explorationTrace.lastActionType

	/**
	 * Get the exploration duration in milliseconds
	 */
	fun getExplorationTimeInMs(): Int = getExplorationDuration().toMillis().toInt()

	/**
	 * Get the exploration duration.
	 *
	 * The default value for [explorationEndTime] is LocalDateTime.MIN. So if
	 * [explorationEndTime] hasn't been set yet, use the time until now,
	 * otherwise use [explorationEndTime].
	 */
	fun getExplorationDuration(): Duration {
		return if (explorationEndTime > LocalDateTime.MIN) {
			Duration.between(explorationStartTime, explorationEndTime)
		} else {
			Duration.between(explorationStartTime, LocalDateTime.now())
		}
	}

	/**
	 * Get the number of explorationTrace which exist in the logcat
	 */
	fun getSize(): Int = explorationTrace.size

	fun List<Interaction<W>>.mapQueueToSingleElement(): List<Interaction<W>>{
		var startQueue = 0
		var endQueue = 0

		val newList : MutableList<Interaction<W>> = mutableListOf()

		this.forEach {
			if (startQueue == endQueue)
				newList.add(it)

			if (it.actionType.isQueueStart())
				startQueue++

			if (it.actionType.isQueueEnd())
				endQueue++
		}

		return newList
	}


	fun assertDeviceExceptionIsMissingOnSuccessAndPresentOnFailureNeverNull() {
		//TODO improve or remove if redundant
//		val lastResultSuccessful = FindReplaceUtility.getLastAction().successful
//		assert(lastResultSuccessful == (exception is DeviceExceptionMissing) || !lastResultSuccessful)
	}

	fun assertOnlyLastActionMightHaveDeviceException() {
		// assert(explorationTrace.getActions().dropLast(1).all { a -> a.successful })

		val actions = explorationTrace.getActions().dropLast(1)

		/** Consider all elements within a ActionQueue as a single action for the assertion
		(-> consider only the ActionQueue end) */
		var inQueue = false
		for (action in actions) {

			if (action.actionType.isQueueStart()) {
				// ActionQueue start
				// -> ignore
				inQueue = true
				continue
			}

			if (inQueue && !action.actionType.isQueueEnd()) {
				// ActionQueue entry
				// -> ignore
				continue
			}

			if (action.actionType.isQueueEnd()) {
				// ActionQueue end
				inQueue = false
			}

			assert(action.successful) { "Not only the last action had a device exception" }
		}

	}

	fun warnIfTimestampsAreIncorrectWithGivenTolerance() {
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
		// KNOWN BUG I observed that sometimes exploration start time is more than 10 second later than first logcat time...
		// ...I was unable to identify the reason for that. Two reasons come to mind:
		// - the exploration logcat comes from previous exploration. This should not be possible because first logs are read at the end
		// of first reset exploration action, and logcat is cleared at the beginning of such reset exploration action.
		// Possible reason is that some logs from previous app exploration were pending to be output to logcat and have outputted
		// moments after logcat was cleared.
		// - the time diff on the device was different when the logcat messages were output, than the time diff measured by DroidMate.
		// This should not be of concern as manual inspection shows that the device time diff changes only a little bit over time,A
		// far less than to justify sudden 10 second difference.
		try {
			val diff = TimeDiffWithTolerance(Duration.ofSeconds(5))
			warnIfExplorationStartTimeIsNotBeforeEndTime(diff, apk.fileName)
			warnIfExplorationStartTimeIsNotBeforeFirstLogTime(diff, apk.fileName)
			warnIfLastLogTimeIsNotBeforeExplorationEndTime(diff, apk.fileName)
			warnIfLogsAreNotAfterAction(diff, apk.fileName)
		} catch (e: Throwable){
			log.error("Error during time computation", e)
		}
	}

	private fun warnIfExplorationStartTimeIsNotBeforeEndTime(diff: TimeDiffWithTolerance, apkFileName: String) {
		diff.warnIfBeyond(this.explorationStartTime, this.explorationEndTime, "exploration start time", "exploration end time", apkFileName)
	}

	private fun warnIfExplorationStartTimeIsNotBeforeFirstLogTime(diff: TimeDiffWithTolerance, apkFileName: String) {
		if (!this.isEmpty()) {
			val firstActionWithLog = this.explorationTrace.getActions().firstOrNull { it.deviceLogs.isNotEmpty() }
			val firstLog = firstActionWithLog?.deviceLogs?.firstOrNull()
			if (firstLog != null)
				diff.warnIfBeyond(this.explorationStartTime, firstLog.time, "exploration start time", "first API logcat", apkFileName)
		}
	}

	private fun warnIfLastLogTimeIsNotBeforeExplorationEndTime(diff: TimeDiffWithTolerance, apkFileName: String) {
		if (!this.isEmpty()) {
			val lastActionWithLog = this.explorationTrace.getActions().lastOrNull { it.deviceLogs.isNotEmpty() }
			val lastLog = lastActionWithLog?.deviceLogs?.lastOrNull()
			if (lastLog != null)
				diff.warnIfBeyond(lastLog.time, this.explorationEndTime, "last API logcat", "exploration end time", apkFileName)
		}
	}

	private fun warnIfLogsAreNotAfterAction(diff: TimeDiffWithTolerance, apkFileName: String) {
		explorationTrace.getActions().forEach {
			if (!it.deviceLogs.isEmpty()) {
				val actionTime = it.startTimestamp
				val firstLogTime = it.deviceLogs.first().time
				diff.warnIfBeyond(actionTime, firstLogTime, "action time", "first logcat time for action", apkFileName)
			}
		}
	}

	fun assertFirstActionIsLaunchApp() {
		assert(explorationTrace.getActions().let{ trace -> trace.isEmpty() || trace.subList(0,min(trace.size,4)).any { it.actionType.isLaunchApp() }}// || explorationTrace.first().actionType == PlaybackResetAction::class.simpleName
		)
	}

	fun assertLastActionIsTerminateOrResultIsFailure() = runBlocking {
		explorationTrace.last()?.let {
			assert(!it.successful || it.actionType.isTerminate()) {" last action was $it instead of Terminate/Failure"}
		}
	}

}
