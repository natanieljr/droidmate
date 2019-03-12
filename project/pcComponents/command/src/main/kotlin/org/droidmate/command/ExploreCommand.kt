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

import com.konradjamrozik.isRegularFile
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
import org.droidmate.configuration.ConfigProperties.Strategies.fitnessProportionate
import org.droidmate.configuration.ConfigProperties.Strategies.minimizeMaximize
import org.droidmate.configuration.ConfigProperties.Strategies.modelBased
import org.droidmate.configuration.ConfigProperties.Strategies.playback
import org.droidmate.configuration.ConfigProperties.Strategies.rotateUI
import org.droidmate.device.android_sdk.*
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.IExplorableAndroidDevice
import org.droidmate.exploration.ExplorationContext
import org.droidmate.device.deviceInterface.IRobustDevice
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.StrategySelector
import org.droidmate.exploration.actions.*
import org.droidmate.explorationModel.interaction.ActionResult
import org.droidmate.explorationModel.Model
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.exploration.strategy.*
import org.droidmate.exploration.strategy.others.MinimizeMaximize
import org.droidmate.exploration.strategy.others.RotateUI
import org.droidmate.exploration.strategy.playback.Playback
import org.droidmate.exploration.strategy.widget.*
import org.droidmate.logging.Markers
import org.droidmate.misc.*
import org.droidmate.report.AggregateStats
import org.droidmate.report.Reporter
import org.droidmate.report.Summary
import org.droidmate.report.apk.*
import org.droidmate.tools.*
import org.droidmate.explorationModel.interaction.EmptyActionResult
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.path.cleanDirs
import org.droidmate.explorationModel.debugT
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.HashMap

open class ExploreCommand constructor(private val cfg: ConfigurationWrapper,
                                      private val apksProvider: IApksProvider,
                                      private val deviceDeployer: IAndroidDeviceDeployer,
                                      private val apkDeployer: IApkDeployer,
                                      private val strategyProvider: (ExplorationContext) -> IExplorationStrategy,
                                      private var modelProvider: (String) -> Model) : DroidmateCommand() {
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

			// Fitness Proportionate Selection
			if (cfg[fitnessProportionate])
				res.add(StrategySelector(++priority, "randomBiased", StrategySelector.randomBiased))

			// ExplorationContext based
			if (cfg[modelBased])
				res.add(StrategySelector(++priority, "randomWithModel", StrategySelector.randomWithModel))

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
			strategies.add(Reset)
			strategies.add(Terminate)

			if (cfg[playback])
				strategies.add(Playback(cfg.getPath(cfg[playbackModelDir]).toAbsolutePath()))

			if (cfg[explore])
				strategies.add(RandomWidget(cfg))

			if (cfg[fitnessProportionate])
				strategies.add(FitnessProportionateSelection(cfg))

			if (cfg[modelBased])
				strategies.add(ModelBased(cfg))

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

		@JvmStatic
		@JvmOverloads
		fun build(cfg: ConfigurationWrapper,
		          deviceTools: IDeviceTools = DeviceTools(cfg),
		          strategies: List<ISelectableExplorationStrategy> = getDefaultStrategies(cfg),
		          selectors: List<StrategySelector> = getDefaultSelectors(cfg),
		          strategyProvider: (ExplorationContext) -> IExplorationStrategy = { ExplorationStrategyPool(strategies, selectors, it) }, //FIXME is it really still useful to overwrite the eContext instead of the model?
		          reportCreators: List<Reporter> = defaultReportWatcher(cfg),
		          modelProvider: (String) -> Model = { appName -> Model.emptyModel(ModelConfig(appName, cfg = cfg))} ): ExploreCommand {
			val apksProvider = ApksProvider(deviceTools.aapt)

			val command = ExploreCommand(cfg, apksProvider, deviceTools.deviceDeployer, deviceTools.apkDeployer,
										 strategyProvider, modelProvider)

			reportCreators.forEach { r -> command.registerReporter(r) }

			return command
		}

		@Suppress("MemberVisibilityCanBePrivate")
		@JvmStatic
		fun defaultReportWatcher(cfg: ConfigurationWrapper): List<Reporter> =
				listOf(AggregateStats(), Summary(), ApkViewsFile(), ApiCount(cfg[includePlots]), ClickFrequency(cfg[includePlots]),
						//TODO WidgetSeenClickedCount(cfg.reportIncludePlots),
						ApiActionTrace(), ActivitySeenSummary(), ActionTrace(), WidgetApiTrace(), VisualizationGraph())
	}

	private val reporters: MutableList<Reporter> = mutableListOf()

	override fun execute(cfg: ConfigurationWrapper): List<ExplorationContext> {
		if(cfg[cleanDirs]) cleanOutputDir(cfg)

		val apks = this.apksProvider.getApks(cfg.apksDirPath, cfg[apksLimit], cfg[apkNames], cfg[shuffleApks])
		if (!validateApks(apks, cfg[runOnNotInlined]))
			return emptyList()

		val explorationData = execute(cfg, apks)
		explorationData.second.let { explorationExceptions ->
			if (explorationExceptions.values.any { it.isNotEmpty() }) {
				explorationExceptions.toList().forEach { (apk,e) ->
					e.firstOrNull()?.let{ error ->
						log.error("${apk?.packageName}${error.message}"); error.printStackTrace() }
					}
				throw ExecuteException(explorationExceptions.filterValues { it.isNotEmpty() }, explorationData.first)
			}
		}

		return explorationData.first
	}

	private fun writeReports(reportDir: Path, resourceDir: Path, rawData: List<ExplorationContext>) {
		if (!Files.exists(reportDir))
			Files.createDirectories(reportDir)

		assert(Files.exists(reportDir)) { "Unable to create report directory ($reportDir)" }

		log.info("Writing reports")
		reporters.forEach { it.write(reportDir.toAbsolutePath(), resourceDir.toAbsolutePath(), rawData) }
	}

	fun registerReporter(report: Reporter) {
		reporters.add(report)
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
				.filter { it.isRegularFile }
				.forEach { Files.delete(it) }

		Files.walk(outputDir)
				.filter { it.parent.fileName.toString() != EnvironmentConstants.dir_name_temp_extracted_resources }
				.filter { it.parent.fileName.toString() != ConfigurationWrapper.log_dir_name }
				.forEach { assert(Files.isDirectory(it)) {"Unable to clean the output directory. File remaining ${it.toAbsolutePath()}"} }
	}

	protected open fun execute(cfg: ConfigurationWrapper, apks: List<Apk>): Pair<List<ExplorationContext>, HashMap<Apk?,List<ExplorationException>>> {
		val out : MutableList<ExplorationContext> = mutableListOf()


		val explorationExceptions: HashMap<Apk?,List<ExplorationException>> = HashMap()
		try {
			explorationExceptions += deployExploreSerialize(cfg, apks, out)
		} catch (deployExploreSerializeThrowable: Throwable) {
			log.error("!!! Caught ${deployExploreSerializeThrowable.javaClass.simpleName} " +
					"in execute(configuration, apks)->deployExploreSerialize(${cfg[deviceIndex]}, apks, out). " +
					"This means ${ExplorationException::class.java.simpleName}s have been lost, if any! " +
					"Skipping summary output analysis persisting. " +
					"Rethrowing.")
			explorationExceptions.addException(deployExploreSerializeThrowable)
		}

		writeReports(cfg.droidmateOutputReportDirPath, cfg.resourceDir, out)

		return Pair(out, explorationExceptions)
	}

	private fun deployExploreSerialize(cfg: ConfigurationWrapper,
	                                   apks: List<Apk>,
	                                   out: MutableList<ExplorationContext>): HashMap<Apk?,List<ExplorationException>>{
		return deviceDeployer.withSetupDevice(cfg[deviceSerialNumber], cfg[deviceIndex]) { device ->
			runBlocking{
				val allApksExplorationExceptions: HashMap<Apk?,List<ExplorationException>> = HashMap()

				var encounteredApkExplorationsStoppingException = false

				apks.forEachIndexed { i, apk ->
					if (!encounteredApkExplorationsStoppingException) {
						log.info(Markers.appHealth, "Processing ${i + 1} out of ${apks.size} apks: ${apk.fileName}")

						allApksExplorationExceptions[apk] =
								apkDeployer.withDeployedApk(device, apk) { deployedApk ->
									tryExploreOnDeviceAndSerialize(deployedApk, device, out)
								}

						if (allApksExplorationExceptions[apk]?.any { (it as? ApkExplorationException)?.shouldStopFurtherApkExplorations() == true } == true) {
							log.warn("Encountered an exception that stops further apk explorations. Skipping exploring the remaining apks.")
							encounteredApkExplorationsStoppingException = true
						}

						// Just preventative measures for ensuring healthiness of the device connection.
						device.restartUiaDaemon(false)
					}
				}
				if (cfg[ConfigProperties.UiAutomatorServer.delayedImgFetch]) // we cannot cancel the job in the explorationLoop as subsequent apk explorations wouldn't have a job to pull images
					imgTransfer.coroutineContext[Job]!!.cancelAndJoin()

				allApksExplorationExceptions
			}
		}  // END runBlocking , this will implicitly wait for all coroutines created in imgTransfer scope
	}


@Throws(DeviceException::class)
	private suspend fun tryExploreOnDeviceAndSerialize(
			deployedApk: IApk, device: IRobustDevice, out: MutableList<ExplorationContext>) {
		val fallibleApkOut2 = this.run(deployedApk, device)

		if (fallibleApkOut2.result != null) {
//      fallibleApkOut2.result!!.serialize(this.storage2) //TODO
			out.add(fallibleApkOut2.result!!)
		}

		if (fallibleApkOut2.exception != null)
			throw fallibleApkOut2.exception!!
	}

	private suspend fun run(app: IApk, device: IRobustDevice): Failable<ExplorationContext, DeviceException> {
		log.info("run(${app.packageName}, device)")

		device.resetTimeSync()

		try {
			tryDeviceHasPackageInstalled(device, app.packageName)
			tryWarnDeviceDisplaysHomeScreen(device, app.fileName)
		} catch (e: DeviceException) {
			return Failable<ExplorationContext, DeviceException>(null, e)
		}

		val output = explorationLoop(app, device)

		output.verify()

		if (output.exceptionIsPresent)
			log.warn(Markers.appHealth, "! Encountered ${output.exception.javaClass.simpleName} during the exploration of ${app.packageName} " +
					"after already obtaining some exploration output.")

		return Failable(output, if (output.exceptionIsPresent) output.exception else null)
	}

	private val imgTransfer = CoroutineScope(Dispatchers.Default+ SupervisorJob() + CoroutineName("device image pull"))

	private fun pullScreenShot(actionId: Int, targetDir: Path, device: IRobustDevice){
		debugT("image transfer should take no time on main thread", {
			imgTransfer.launch {
				// pull the image from device, store it in the image directory defined in ModelConfig and remove it on device
				val fileName = "$actionId.jpg"
				val dstFile = targetDir.resolve(fileName)
				var c = 0
				do {          // try for up to 3 times to pull a screenshot image
					delay(2000)// the device is going to need some time to compress the image, if the image is time critical you should disable delayed fetch
					device.pullFile(fileName, dstFile)
				} while (c++ < 3 && !File(dstFile.toString()).exists())
			}
		}, inMillis = true)

	}
	private suspend fun explorationLoop(app: IApk, device: IRobustDevice): ExplorationContext {
		log.debug("explorationLoop(app=${app.fileName}, device)")

		// Use the received exploration eContext (if any) otherwise construct the object that
		// will hold the exploration output and that will be returned from this method.
		// Note that a different eContext is created for each exploration if none it provider
		val explorationContext = ExplorationContext(cfg, app, device, LocalDateTime.now(), _model = modelProvider(app.packageName))

		log.debug("Exploration start time: " + explorationContext.explorationStartTime)

		// Construct initial action and run it on the device to obtain initial result.
		var action: ExplorationAction = EmptyAction
		var result: ActionResult = EmptyActionResult

		var isFirst = true
		val strategy: IExplorationStrategy = strategyProvider.invoke(explorationContext)

		try {
			// Execute the exploration loop proper, starting with the values of initial reset action and its result.
			while (isFirst || (result.successful && !action.isTerminate())) {
				// decide for an action
				action = strategy.decide(result) // check if we need to initialize timeProvider.getNow() here
				// execute action
				result = action.run(app, device)

				if (cfg[ConfigProperties.UiAutomatorServer.delayedImgFetch]) withContext(Dispatchers.IO) {
					if(action is ActionQueue){
						action.actions.forEachIndexed { i, a ->
							if(i<action.actions.size-1 &&
									((a is TextInsert && action.actions[i+1] is Click)
											|| a is Swipe))
								pullScreenShot(a.id, explorationContext.getModel().config.imgDst, device)
						}
					}
					// TODO if contains scroll additional screen capture + fetch
					// ? if text input additional screen capture after insert action?
					pullScreenShot(action.id, explorationContext.getModel().config.imgDst, device)
				}

				explorationContext.update(action, result)

				if (isFirst) {
					log.info("Initial action: $action")
					isFirst = false
				}
			}
			assert(!result.successful || action.isTerminate())
			assert(!explorationContext.apk.launchableMainActivityName.isBlank()) { "launchedMainActivityName was Blank" }		}
		catch(e: Throwable){  // the decide call of a strategy may issue an exception e.g. when trying to interact on non-actable elements
			log.error("Exception during exploration (probably caused by Strategy.decide) check explorationContext.exception for further details")
			explorationContext.exception = DeviceException(e)
		}
		finally {
//			if (explorationContext.cfg[ConfigProperties.UiAutomatorServer.delayedImgFetch]) {
				// having one pull in the end does not really seam to make a performance difference right now
//				val fileName = "${action.id}.jpg"
//				val dstFile = explorationContext.getModel().config.imgDst.resolve(fileName)
//				var c = 0
//				do {          // try for up to 3 times to pull a screenshot image
//					// instead of intermittent transfer pull the whole image directory in the end of this exploration
//					device.pullFile("", explorationContext.getModel().config.imgDst)
//				} while (c++ < 3 && !File(dstFile.toString()).exists())
//			}

			strategy.close()
			explorationContext.close()
		}

		// Propagate exception if there was any
		if (!result.successful)
			explorationContext.exception = exception

		explorationContext.explorationEndTime = LocalDateTime.now()

		return explorationContext
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
