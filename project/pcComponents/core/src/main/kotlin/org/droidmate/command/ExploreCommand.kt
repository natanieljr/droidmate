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

package org.droidmate.command

import kotlinx.coroutines.*
import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigProperties.Deploy.shuffleApks
import org.droidmate.configuration.ConfigProperties.Exploration.apkNames
import org.droidmate.configuration.ConfigProperties.Exploration.apksLimit
import org.droidmate.configuration.ConfigProperties.Exploration.deviceIndex
import org.droidmate.configuration.ConfigProperties.Exploration.deviceSerialNumber
import org.droidmate.configuration.ConfigProperties.Exploration.runOnNotInlined
import org.droidmate.configuration.ConfigProperties.Output.reportDir
import org.droidmate.configuration.ConfigProperties.Report.includePlots
import org.droidmate.configuration.ConfigProperties.Selectors.actionLimit
import org.droidmate.configuration.ConfigProperties.Selectors
import org.droidmate.configuration.ConfigProperties.Selectors.playbackModelDir
import org.droidmate.configuration.ConfigProperties.Selectors.pressBackProbability
import org.droidmate.configuration.ConfigProperties.Selectors.resetEvery
import org.droidmate.configuration.ConfigProperties.Selectors.stopOnExhaustion
import org.droidmate.configuration.ConfigProperties.Selectors.timeLimit
import org.droidmate.configuration.ConfigProperties.Selectors.widgetIndexes
import org.droidmate.configuration.ConfigProperties.Strategies.allowRuntimeDialog
import org.droidmate.configuration.ConfigProperties.Strategies
import org.droidmate.configuration.ConfigProperties.Strategies.Parameters.uiRotation
import org.droidmate.configuration.ConfigProperties.Strategies.denyRuntimeDialog
import org.droidmate.configuration.ConfigProperties.Strategies.explore
import org.droidmate.configuration.ConfigProperties.Strategies.minimizeMaximize
import org.droidmate.configuration.ConfigProperties.Strategies.playback
import org.droidmate.configuration.ConfigProperties.Strategies.rotateUI
import org.droidmate.device.android_sdk.*
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.IExplorableAndroidDevice
import org.droidmate.exploration.ExplorationContext
import org.droidmate.device.deviceInterface.IRobustDevice
import org.droidmate.device.error.DeviceException
import org.droidmate.device.error.DeviceExceptionMissing
import org.droidmate.device.exception
import org.droidmate.device.logcat.ApiLogcatMessage
import org.droidmate.device.logcat.ApiLogcatMessageListExtensions
import org.droidmate.device.execute
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.StrategySelector
import org.droidmate.exploration.actions.resetApp
import org.droidmate.exploration.modelFeatures.ModelFeature
import org.droidmate.exploration.modelFeatures.reporter.*
import org.droidmate.explorationModel.interaction.ActionResult
import org.droidmate.explorationModel.Model
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.exploration.strategy.*
import org.droidmate.exploration.strategy.others.RotateUI
import org.droidmate.exploration.strategy.playback.Playback
import org.droidmate.exploration.strategy.widget.*
import org.droidmate.explorationModel.ModelFeatureI
import org.droidmate.logging.Markers
import org.droidmate.misc.*
import org.droidmate.tools.*
import org.droidmate.explorationModel.interaction.EmptyActionResult
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.path.cleanDirs
import org.droidmate.explorationModel.debugT
import org.droidmate.exploration.modelFeatures.reporter.AggregateStats
import org.droidmate.exploration.modelFeatures.reporter.Summary
import org.droidmate.exploration.strategy.others.MinimizeMaximize
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.*

open class ExploreCommand constructor(private val cfg: ConfigurationWrapper,
                                      private val apksProvider: IApksProvider,
                                      private val deviceDeployer: IAndroidDeviceDeployer,
                                      private val apkDeployer: IApkDeployer,
                                      private val strategyProvider: (ExplorationContext) -> IExplorationStrategy,
                                      private var modelProvider: (String) -> Model) {
	companion object {
		@JvmStatic
		protected val log: Logger by lazy { LoggerFactory.getLogger(ExploreCommand::class.java) }

		@Suppress("MemberVisibilityCanBePrivate")
		@JvmStatic
		fun getDefaultSelectors(cfg: ConfigurationWrapper): List<StrategySelector>{
			val res : MutableList<StrategySelector> = mutableListOf()

			var priority = 0

			if (cfg[playback])
				res.add(StrategySelector(++priority, "playback", StrategySelector.playback))

			res.add(StrategySelector(++priority, "startExplorationReset", StrategySelector.startExplorationReset))

			// ExplorationAction based terminate
			if ((cfg[widgetIndexes].first() >= 0) || cfg[actionLimit] > 0) {
				val actionLimit = if (cfg[widgetIndexes].first() >= 0)
					cfg[widgetIndexes].size // TODO what is this widgetIndexes for?
				else
					cfg[actionLimit]

				res.add(StrategySelector(++priority, "actionBasedTerminate", StrategySelector.actionBasedTerminate, actionLimit))
			}

			// Time based terminate
			if (cfg[timeLimit] > 0)
				res.add(StrategySelector(++priority, "timeBasedTerminate", StrategySelector.timeBasedTerminate, cfg[timeLimit]))

			res.add(StrategySelector(++priority, "appCrashedReset", StrategySelector.appCrashedReset))

			if (cfg[allowRuntimeDialog])
				res.add(StrategySelector(++priority, "allowPermission", StrategySelector.allowPermission))

			if (cfg[denyRuntimeDialog])
				res.add(StrategySelector(++priority, "denyPermission", StrategySelector.denyPermission))

			res.add(StrategySelector(++priority, "cannotExplore", StrategySelector.cannotExplore))

			// Interval reset
			if (cfg[resetEvery] > 0)
				res.add(StrategySelector(++priority, "intervalReset", StrategySelector.intervalReset, cfg[resetEvery]))

			// Random back
			if (cfg[pressBackProbability] > 0.0)
				res.add(StrategySelector(++priority, "randomBack", StrategySelector.randomBack, null, cfg[pressBackProbability], Random(cfg.randomSeed)))

			if (cfg[Selectors.dfs])
				res.add(StrategySelector(++priority, "dfs", StrategySelector.dfs))

			// Exploration exhausted
			if (cfg[stopOnExhaustion])
				res.add(StrategySelector(++priority, "explorationExhausted", StrategySelector.explorationExhausted))

			// Random exploration
			if (cfg[explore])
				res.add(StrategySelector(++priority, "randomWidget", StrategySelector.randomWidget))

			return res
		}

		@Suppress("MemberVisibilityCanBePrivate")
		@JvmStatic
		fun getDefaultStrategies(cfg: ConfigurationWrapper): List<ISelectableExplorationStrategy>{
			val strategies = LinkedList<ISelectableExplorationStrategy>()

			strategies.add(Back)
			strategies.add(Reset())
			strategies.add(Terminate)

			if (cfg[playback])
				strategies.add(Playback(cfg.getPath(cfg[playbackModelDir]).toAbsolutePath()))

			if (cfg[explore])
				strategies.add(RandomWidget(cfg.randomSeed, cfg[Strategies.Parameters.biasedRandom],
						cfg[Strategies.Parameters.randomScroll], delay = cfg[ConfigProperties.Exploration.widgetActionDelay] ))

			if (cfg[allowRuntimeDialog])
				strategies.add(AllowRuntimePermission())

			if (cfg[denyRuntimeDialog])
				strategies.add(DenyRuntimePermission())

			if (cfg[Strategies.dfs])
				strategies.add(DFS())

			if (cfg[rotateUI])
				strategies.add(RotateUI(cfg[uiRotation]))

			if (cfg[minimizeMaximize])
				strategies.add(MinimizeMaximize())

			return strategies
		}

		private val watcher: LinkedList<ModelFeatureI> = LinkedList()

		@JvmStatic
		@JvmOverloads
		fun build(cfg: ConfigurationWrapper,
		          deviceTools: IDeviceTools = DeviceTools(cfg),
		          strategies: List<ISelectableExplorationStrategy> = getDefaultStrategies(cfg),
		          selectors: List<StrategySelector> = getDefaultSelectors(cfg),
		          strategyProvider: (ExplorationContext) -> IExplorationStrategy = { ExplorationStrategyPool(strategies, selectors, it) }, //FIXME is it really still useful to overwrite the eContext instead of the model?
		          watcher: List<ModelFeatureI> = defaultReportWatcher(cfg),
		          modelProvider: (String) -> Model = { appName -> Model.emptyModel(ModelConfig(appName, cfg = cfg))} ): ExploreCommand {
			val apksProvider = ApksProvider(deviceTools.aapt)

			val command = ExploreCommand(cfg, apksProvider, deviceTools.deviceDeployer, deviceTools.apkDeployer,
					strategyProvider, modelProvider)
			this.watcher.addAll(watcher)

			return command
		}
		@Suppress("MemberVisibilityCanBePrivate")
		@JvmStatic
		fun defaultReportWatcher(cfg: ConfigurationWrapper): List<ReporterMF> {
			val reportDir = cfg.droidmateOutputReportDirPath.toAbsolutePath()
			val resourceDir = cfg.resourceDir.toAbsolutePath()
			return listOf(AggregateStats(reportDir, resourceDir),
					Summary(reportDir, resourceDir),
					ApkViewsFileMF(reportDir, resourceDir),
					ApiCountMF(reportDir, resourceDir, includePlots = cfg[includePlots]),
					ClickFrequencyMF(reportDir, resourceDir, includePlots = cfg[includePlots]),
//						TODO WidgetSeenClickedCount(cfg.reportIncludePlots),
					ApiActionTraceMF(reportDir, resourceDir),
					ActivitySeenSummaryMF(reportDir, resourceDir),
					ActionTraceMF(reportDir, resourceDir),
					WidgetApiTraceMF(reportDir, resourceDir),
					VisualizationGraphMF(reportDir, resourceDir))
		}

	}

	suspend fun execute(cfg: ConfigurationWrapper): Map<Apk, FailableExploration> = supervisorScope {
		if (cfg[cleanDirs]) cleanOutputDir(cfg)
		val reportDir = cfg.droidmateOutputReportDirPath
		if (!Files.exists(reportDir))
			withContext(Dispatchers.IO){ Files.createDirectories(reportDir)}
		assert(Files.exists(reportDir)) { "Unable to create report directory ($reportDir)" }

		val apks = apksProvider.getApks(cfg.apksDirPath, cfg[apksLimit], cfg[apkNames], cfg[shuffleApks])
		if (!validateApks(apks, cfg[runOnNotInlined]))
			return@supervisorScope emptyMap<Apk, FailableExploration>()

		val explorationData = execute(cfg, apks)

		onFinalFinished()
		log.warn("Writing reports finished.")

		return@supervisorScope explorationData
	}

	private suspend fun onFinalFinished() = coroutineScope {
		// we use coroutineScope here to ensure that this function waits for all coroutines spawned within this method
		watcher.forEach { feature ->
			(feature as? ModelFeature)?.let {
				// this is meant to be in the current coroutineScope and not in feature, such this scope waits for its completion
				launch(CoroutineName("eContext-finish")) {
					it.onFinalFinished()
				}
			}
		}
	}

	private fun validateApks(apks: List<Apk>, runOnNotInlined: Boolean): Boolean {
		if (apks.isEmpty()) {
			log.warn("No input apks found. Terminating.")
			return false
		}
		if (apks.any { !it.inlined }) {
			if (runOnNotInlined) {
				log.info("Not inlined input apks have been detected, but DroidMate was instructed to run anyway. Continuing with execution.")
			} else {
				log.warn("At least one input apk is not inlined. DroidMate will not be able to monitor any calls to Android SDK methods done by such apps.")
				log.warn("If you want to inline apks, run DroidMate with ${ConfigProperties.ExecutionMode.inline.name}")
				log.warn("If you want to run DroidMate on non-inlined apks, run it with ${ConfigProperties.Exploration.runOnNotInlined.name}")
				log.warn("DroidMate will now abort due to the not-inlined apk.")
				return false
			}
		}
		return true
	}

	private fun cleanOutputDir(cfg: ConfigurationWrapper) {
		val outputDir = cfg.droidmateOutputDirPath

		if (!Files.isDirectory(outputDir))
			return

		arrayListOf(cfg[reportDir]).forEach {

			val dirToDelete = outputDir.resolve(it)
			if (Files.isDirectory(dirToDelete))
				dirToDelete.deleteDir()
		}

		Files.walk(outputDir)
				.filter { it.parent.fileName.toString() != EnvironmentConstants.dir_name_temp_extracted_resources }
				.filter { it.parent.fileName.toString() != ConfigurationWrapper.log_dir_name }
				.filter { Files.isRegularFile(it) }
				.forEach { Files.delete(it) }

		Files.walk(outputDir)
				.filter { it.parent.fileName.toString() != EnvironmentConstants.dir_name_temp_extracted_resources }
				.filter { it.parent.fileName.toString() != ConfigurationWrapper.log_dir_name }
				.forEach { assert(Files.isDirectory(it)) {"Unable to clean the output directory. File remaining ${it.toAbsolutePath()}"} }
	}

	protected open suspend fun execute(cfg: ConfigurationWrapper, apks: List<Apk>): Map<Apk,FailableExploration> {

		return deviceDeployer.setupAndExecute(cfg[deviceSerialNumber], cfg[deviceIndex], apkDeployer, apks) { app, device -> runApp(app,device)}
	}

	private suspend fun runApp(app: IApk, device: IRobustDevice): FailableExploration {
		log.info("execute(${app.packageName}, device)")

		device.resetTimeSync()

		try {
			tryDeviceHasPackageInstalled(device, app.packageName)
			tryWarnDeviceDisplaysHomeScreen(device, app.fileName)
		} catch (e: DeviceException) {
			return FailableExploration(null, listOf(e))
		}

		return explorationLoop(app, device)
	}

	private suspend fun ExplorationContext.verify() {
		try {
			assert(this.explorationTrace.size > 0)
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
			throw RuntimeException(e)
		}
	}

	private fun ExplorationContext.assertLogsAreSortedByTime() {
		val apiLogs = explorationTrace.getActions()
				.mapQueueToSingleElement()
				.flatMap { deviceLog -> deviceLog.deviceLogs.map { ApiLogcatMessage.from(it) } }

		assert(explorationStartTime <= explorationEndTime)

		val ret = ApiLogcatMessageListExtensions.sortedByTimePerPID(apiLogs)
		assert(ret)
	}

	private suspend fun pullScreenShot(actionId: Int, targetDir: Path, device: IRobustDevice, eContext: ExplorationContext) = withTimeoutOrNull(10000){
		debugT("image transfer should take no time on main thread", {
			eContext.imgTransfer.launch {
				// pull the image from device, store it in the image directory defined in ModelConfig and remove it on device
				val fileName = "$actionId.jpg"
				val dstFile = targetDir.resolve(fileName)
				var c = 0
				do {          // try for up to 3 times to pull a screenshot image
					delay(2000)// the device is going to need some time to compress the image, if the image is time critical you should disable delayed fetch
					device.pullFile(fileName, dstFile)
				} while (isActive && c++ < 3 && !File(dstFile.toString()).exists())
			}
		}, inMillis = true)

	}
	private suspend fun explorationLoop(app: IApk, device: IRobustDevice): FailableExploration {
		log.debug("explorationLoop(app=${app.fileName}, device)")

		// Use the received exploration eContext (if any) otherwise construct the object that
		// will hold the exploration output and that will be returned from this method.
		// Note that a different eContext is created for each exploration if none it provider
		val explorationContext = ExplorationContext(cfg, app, { device.readStatements() }, LocalDateTime.now(), watcher = watcher, _model = modelProvider(app.packageName))

		log.debug("Exploration start time: " + explorationContext.explorationStartTime)

		// Construct initial action and execute it on the device to obtain initial result.
		var action: ExplorationAction = EmptyAction
		var result: ActionResult = EmptyActionResult

		var isFirst = true

		var strategy: IExplorationStrategy? = null
		try {
			strategy = strategyProvider.invoke(explorationContext)
			// Execute the exploration loop proper, starting with the values of initial reset action and its result.
			while (isFirst || (result.successful && !action.isTerminate())) {
				try {
					// decide for an action
					action = strategy.decide(result) // check if we need to initialize timeProvider.getNow() here
					// execute action
					result = action.execute(app, device)

					if (cfg[ConfigProperties.UiAutomatorServer.delayedImgFetch]) {
						if (action is ActionQueue) {
							action.actions.forEachIndexed { i, a ->
								if (i < action.actions.size - 1 &&
										((a is TextInsert && action.actions[i + 1] is Click)
												|| a is Swipe))
									pullScreenShot(a.id, explorationContext.getModel().config.imgDst, device, explorationContext)
							}
						}
						pullScreenShot(action.id, explorationContext.getModel().config.imgDst, device, explorationContext)
					}

					explorationContext.update(action, result)

					if (isFirst) {
						log.info("Initial action: $action")
						isFirst = false
					}

					// Propagate exception if there was any exception on device
					if (!result.successful && exception !is DeviceExceptionMissing){
						explorationContext.exceptions.add(exception)
					}

					assert(!explorationContext.apk.launchableMainActivityName.isBlank()) { "launchedMainActivityName was Blank" }
				} catch (e: Throwable) {  // the decide call of a strategy may issue an exception e.g. when trying to interact on non-actable elements
					log.error("Exception during exploration\n" +
							" ${e.localizedMessage}")
					explorationContext.exceptions.add(e)
					explorationContext.resetApp().execute(app,device)
				}
			} // end loop

			explorationContext.verify() // some result validation do this in the end of exploration for this app
			// but within the catch block to NOT terminate other explorations and to NOT loose the derived context

		} catch (e: Throwable){ // the loop handles internal error if possible, however if the resetApp after exception fails we end in this catch
			// this means likely the uiAutomator is dead or we lost device connection
			log.error("unhandled device exception \n ${e.localizedMessage}", e)
			explorationContext.exceptions.add(e)
			strategy?.close()
		}
		finally {
			explorationContext.close()
		}
		explorationContext.explorationEndTime = LocalDateTime.now()

		return FailableExploration(explorationContext, explorationContext.exceptions)
	}

	@Throws(DeviceException::class)
	private fun tryDeviceHasPackageInstalled(device: IExplorableAndroidDevice, packageName: String) {
		log.trace("tryDeviceHasPackageInstalled(device, $packageName)")

		if (!device.hasPackageInstalled(packageName))
			throw DeviceException("Package $packageName not installed.")
	}

	@Throws(DeviceException::class)
	private fun tryWarnDeviceDisplaysHomeScreen(device: IExplorableAndroidDevice, fileName: String) {
		log.trace("tryWarnDeviceDisplaysHomeScreen(device, $fileName)")

		val initialGuiSnapshot = device.perform(GlobalAction(ActionType.FetchGUI))

		if (!initialGuiSnapshot.isHomeScreen)
			log.warn(Markers.appHealth,
					"An exploration process for $fileName is about to start but the device doesn't display home screen. " +
							"Instead, its GUI state is: $initialGuiSnapshot.guiStatus. " +
							"Continuing the exploration nevertheless, hoping that the first \"reset app\" " +
							"exploration action will force the device into the home screen.")
	}
}
