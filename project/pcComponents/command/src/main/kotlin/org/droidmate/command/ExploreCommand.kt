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
import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigProperties.Deploy.shuffleApks
import org.droidmate.configuration.ConfigProperties.Exploration.apkNames
import org.droidmate.configuration.ConfigProperties.Exploration.apksLimit
import org.droidmate.configuration.ConfigProperties.Exploration.deviceIndex
import org.droidmate.configuration.ConfigProperties.Exploration.deviceSerialNumber
import org.droidmate.configuration.ConfigProperties.Exploration.runOnNotInlined
import org.droidmate.configuration.ConfigProperties.Output.reportDir
import org.droidmate.configuration.ConfigProperties.Output.screenshotDir
import org.droidmate.configuration.ConfigProperties.Report.includePlots
import org.droidmate.configuration.ConfigProperties.Selectors.actionLimit
import org.droidmate.configuration.ConfigProperties.Selectors.playbackModelDir
import org.droidmate.configuration.ConfigProperties.Selectors.pressBackProbability
import org.droidmate.configuration.ConfigProperties.Selectors.resetEvery
import org.droidmate.configuration.ConfigProperties.Selectors.timeLimit
import org.droidmate.configuration.ConfigProperties.Selectors.widgetIndexes
import org.droidmate.configuration.ConfigProperties.Strategies.allowRuntimeDialog
import org.droidmate.configuration.ConfigProperties.Strategies.explore
import org.droidmate.configuration.ConfigProperties.Strategies.fitnessProportionate
import org.droidmate.configuration.ConfigProperties.Strategies.modelBased
import org.droidmate.configuration.ConfigProperties.Strategies.playback
import org.droidmate.device.android_sdk.*
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.debug.debugT
import org.droidmate.device.IExplorableAndroidDevice
import org.droidmate.exploration.AbstractContext
import org.droidmate.exploration.data_aggregators.ExplorationOutput2
import org.droidmate.device.deviceInterface.IRobustDevice
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.StrategySelector
import org.droidmate.exploration.actions.IRunnableExplorationAction
import org.droidmate.exploration.actions.RunnableExplorationAction
import org.droidmate.exploration.actions.RunnableTerminateExplorationAction
import org.droidmate.exploration.statemodel.ActionResult
import org.droidmate.exploration.statemodel.Model
import org.droidmate.exploration.statemodel.ModelLoader
import org.droidmate.exploration.statemodel.config.ModelConfig
import org.droidmate.exploration.strategy.*
import org.droidmate.exploration.strategy.playback.MemoryPlayback
import org.droidmate.exploration.strategy.widget.AllowRuntimePermission
import org.droidmate.exploration.strategy.widget.FitnessProportionateSelection
import org.droidmate.exploration.strategy.widget.ModelBased
import org.droidmate.exploration.strategy.widget.RandomWidget
import org.droidmate.logging.Markers
import org.droidmate.misc.*
import org.droidmate.report.AggregateStats
import org.droidmate.report.Reporter
import org.droidmate.report.Summary
import org.droidmate.report.apk.*
import org.droidmate.storage.Storage2
import org.droidmate.tools.*
import org.droidmate.uiautomator_daemon.guimodel.FetchGUI
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.system.measureTimeMillis

open class ExploreCommand constructor(private val apksProvider: IApksProvider,
                                      private val deviceDeployer: IAndroidDeviceDeployer,
                                      private val apkDeployer: IApkDeployer,
									  private val timeProvider: ITimeProvider,
									  private val strategyProvider: (AbstractContext) -> IExplorationStrategy,
									  private var modelProvider: (String) -> Model) : DroidmateCommand() {
	companion object {
		@JvmStatic
		protected val log: Logger = LoggerFactory.getLogger(ExploreCommand::class.java)

		@JvmStatic
		fun getDefaultSelectors(cfg: ConfigurationWrapper): List<StrategySelector>{
			val res : MutableList<StrategySelector> = mutableListOf()

			var priority = 0

			if (cfg[playback])
				res.add(StrategySelector(++priority, StrategySelector.playback))

			res.add(StrategySelector(++priority, StrategySelector.startExplorationReset))
			res.add(StrategySelector(++priority, StrategySelector.appCrashedReset))

			if (cfg[allowRuntimeDialog])
				res.add(StrategySelector(++priority, StrategySelector.allowPermission))

			res.add(StrategySelector(++priority, StrategySelector.cannotExplore))

			// Action based terminate
			if ((cfg[widgetIndexes].first() >= 0) || cfg[actionLimit] > 0) {
				val actionLimit = if (cfg[widgetIndexes].first() >= 0)
					cfg[widgetIndexes].size
				else
					cfg[actionLimit]

				res.add(StrategySelector(++priority, StrategySelector.actionBasedTerminate, actionLimit))
			}

			// Time based terminate
			if (cfg[timeLimit] > 0)
				res.add(StrategySelector(++priority, StrategySelector.timeBasedTerminate, cfg[timeLimit]))

			// Interval reset
			if (cfg[resetEvery] > 0)
				res.add(StrategySelector(++priority, StrategySelector.intervalReset, cfg[resetEvery]))

			// Random back
			if (cfg[pressBackProbability] > 0.0)
				res.add(StrategySelector(++priority, StrategySelector.randomBack, cfg[pressBackProbability], Random(cfg.randomSeed.toLong())))

			// Fitness Proportionate Selection
			if (cfg[fitnessProportionate])
				res.add(StrategySelector(++priority, StrategySelector.randomBiased))

			// ExplorationContext based
			if (cfg[modelBased])
				res.add(StrategySelector(++priority, StrategySelector.randomWithModel))

			// Random exploration
			if (cfg[explore])
				res.add(StrategySelector(++priority, StrategySelector.randomWidget))

			return res
		}

		@JvmStatic
		fun getDefaultStrategies(cfg: ConfigurationWrapper): List<ISelectableExplorationStrategy>{
			val strategies = LinkedList<ISelectableExplorationStrategy>()

			strategies.add(Back())
			strategies.add(Reset())
			strategies.add(Terminate())

			if (cfg[playback])
				strategies.add(MemoryPlayback(cfg.getPath(cfg[playbackModelDir]).toAbsolutePath().toString()))

			if (cfg[explore])
				strategies.add(RandomWidget(cfg))

			if (cfg[fitnessProportionate])
				strategies.add(FitnessProportionateSelection(cfg))

			if (cfg[modelBased])
				strategies.add(ModelBased(cfg))

			if (cfg[allowRuntimeDialog])
				strategies.add(AllowRuntimePermission())

			return strategies
		}

		@JvmStatic
		@JvmOverloads
		fun build(cfg: ConfigurationWrapper,
		          deviceTools: IDeviceTools = DeviceTools(cfg),
				  timeProvider: ITimeProvider = TimeProvider(),
				  strategies: List<ISelectableExplorationStrategy> = getDefaultStrategies(cfg),
				  selectors: List<StrategySelector> = getDefaultSelectors(cfg),
				  strategyProvider: (AbstractContext) -> IExplorationStrategy = { ExplorationStrategyPool(strategies, selectors, it) },
		          reportCreators: List<Reporter> = defaultReportWatcher(cfg),
				  modelProvider: (String) -> Model = { appName -> Model.emptyModel(ModelConfig(appName))}): ExploreCommand {
			val apksProvider = ApksProvider(deviceTools.aapt)

			val command = ExploreCommand(apksProvider, deviceTools.deviceDeployer, deviceTools.apkDeployer,
					timeProvider, strategyProvider, modelProvider)

			reportCreators.forEach { r -> command.registerReporter(r) }

			return command
		}

		@JvmStatic
		protected fun defaultReportWatcher(cfg: ConfigurationWrapper): List<Reporter> =
				listOf(AggregateStats(), Summary(), ApkViewsFile(), ApiCount(cfg[includePlots]), ClickFrequency(cfg[includePlots]),
						//TODO WidgetSeenClickedCount(cfg.reportIncludePlots),
						ApiActionTrace(), ActivitySeenSummary(), ActionTrace(), WidgetApiTrace())
	}

	private val reporters: MutableList<Reporter> = mutableListOf()
	private var actionT: Long = 0
	private var nActions = 0

	override fun execute(cfg: ConfigurationWrapper) {
		cleanOutputDir(cfg)

		val apks = this.apksProvider.getApks(cfg.apksDirPath, cfg[apksLimit], cfg[apkNames], cfg[shuffleApks])
		if (!validateApks(apks, cfg[runOnNotInlined])) return

		val explorationExceptions = execute(cfg, apks)
		if (!explorationExceptions.isEmpty())
			throw ThrowablesCollection(explorationExceptions)
	}

	private fun writeReports(reportDir: Path, rawData: List<AbstractContext>) {
		if (!Files.exists(reportDir))
			Files.createDirectories(reportDir)

		assert(Files.exists(reportDir), { "Unable to create report directory ($reportDir)" })

		log.info("Writing reports")
		reporters.forEach { it.write(reportDir.toAbsolutePath(), rawData) }
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
				.filter { it.parent.fileName.toString() != BuildConstants.dir_name_temp_extracted_resources }
				.filter { it.isRegularFile }
				.forEach { Files.delete(it) }

		Files.walk(outputDir)
				.filter { it.parent.fileName.toString() != BuildConstants.dir_name_temp_extracted_resources }
				.forEach { assert(Files.isDirectory(it), {"Unable to clean the output directory. File remaining ${it.toAbsolutePath()}"}) }
	}

	protected open fun execute(cfg: ConfigurationWrapper, apks: List<Apk>): List<ExplorationException> {
		val out = ExplorationOutput2()


		val explorationExceptions: MutableList<ExplorationException> = mutableListOf()
		try {
			explorationExceptions += deployExploreSerialize(cfg, apks, out)
		} catch (deployExploreSerializeThrowable: Throwable) {
			log.error("!!! Caught ${deployExploreSerializeThrowable.javaClass.simpleName} " +
					"in execute(configuration, apks)->deployExploreSerialize(${cfg[deviceIndex]}, apks, out). " +
					"This means ${ExplorationException::class.java.simpleName}s have been lost, if any! " +
					"Skipping summary output analysis persisting. " +
					"Rethrowing.")
			throw deployExploreSerializeThrowable
		}

		writeReports(cfg.droidmateOutputReportDirPath, out)

		return explorationExceptions
	}

	private fun deployExploreSerialize(cfg: ConfigurationWrapper,
	                                   apks: List<Apk>,
	                                   out: ExplorationOutput2): List<ExplorationException> {
		return this.deviceDeployer.withSetupDevice(cfg[deviceSerialNumber], cfg[deviceIndex]) { device ->

			val allApksExplorationExceptions: MutableList<ApkExplorationException> = mutableListOf()

			var encounteredApkExplorationsStoppingException = false

			apks.forEachIndexed { i, apk ->
				if (!encounteredApkExplorationsStoppingException) {
					// Start measuring Method Coverage
					val covMonitor = CoverageMonitor(apk.fileName, cfg)
					val covMonitorThread = Thread(covMonitor, "Logcat thread")
					covMonitorThread.start()

					log.info(Markers.appHealth, "Processing ${i + 1} out of ${apks.size} apks: ${apk.fileName}")

					allApksExplorationExceptions +=
							this.apkDeployer.withDeployedApk(device, apk) { deployedApk ->
								tryExploreOnDeviceAndSerialize(deployedApk, device, out)
							}

					// Stop monitoring coverage
					covMonitor.stop()

					if (allApksExplorationExceptions.any { it.shouldStopFurtherApkExplorations() }) {
						log.warn("Encountered an exception that stops further apk explorations. Skipping exploring the remaining apks.")
						encounteredApkExplorationsStoppingException = true
					}

					// Just preventative measures for ensuring healthiness of the device connection.
					device.restartUiaDaemon(false)
				}
			}

			allApksExplorationExceptions
		}
	}

	@Throws(DeviceException::class)
	private fun tryExploreOnDeviceAndSerialize(
			deployedApk: IApk, device: IRobustDevice, out: ExplorationOutput2) {
		val fallibleApkOut2 = this.run(deployedApk, device)

		if (fallibleApkOut2.result != null) {
//      fallibleApkOut2.result!!.serialize(this.storage2) //TODO
			out.add(fallibleApkOut2.result!!)
		}

		if (fallibleApkOut2.exception != null)
			throw fallibleApkOut2.exception!!
	}

	fun run(app: IApk, device: IRobustDevice): Failable<AbstractContext, DeviceException> {
		log.info("run(${app.packageName}, device)")

		device.resetTimeSync()

		try {
			tryDeviceHasPackageInstalled(device, app.packageName)
			tryWarnDeviceDisplaysHomeScreen(device, app.fileName)
		} catch (e: DeviceException) {
			return Failable<AbstractContext, DeviceException>(null, e)
		}

		val output = explorationLoop(app, device)

		output.verify()

		if (output.exceptionIsPresent)
			log.warn(Markers.appHealth, "! Encountered ${output.exception.javaClass.simpleName} during the exploration of ${app.packageName} " +
					"after already obtaining some exploration output.")

		return Failable(output, if (output.exceptionIsPresent) output.exception else null)
	}

	private fun explorationLoop(app: IApk, device: IRobustDevice): AbstractContext {
		log.debug("explorationLoop(app=${app.fileName}, device)")

		// Use the received exploration context (if any) otherwise construct the object that
		// will hold the exploration output and that will be returned from this method.
		// Note that a different context is created for each exploration if none it provieder
		val output = ExplorationContext(app, timeProvider.getNow(), _model = modelProvider(app.packageName))

		log.debug("Exploration start time: " + output.explorationStartTime)

		// Construct initial action and run it on the device to obtain initial result.
		var action: IRunnableExplorationAction? = null
		var result: ActionResult = EmptyActionResult

		var isFirst = true
		val strategy: IExplorationStrategy = strategyProvider.invoke(output)

		// Execute the exploration loop proper, starting with the values of initial reset action and its result.
		while (isFirst || (result.successful && action !is RunnableTerminateExplorationAction)) {
			// decide for an action
			action = debugT("strategy decision time", { RunnableExplorationAction.from(strategy.decide(result), timeProvider.getNow()) }, inMillis = true)
			// execute action
			measureTimeMillis { result = action.run(app, device) }.let {
				actionT += it
				nActions += 1
				println("$it millis elapsed until result was available on average ${actionT / nActions}")
			}
			output.add(action, result)
			// update strategy
			strategy.update(result)

			if (isFirst) {
				log.info("Initial action: ${action.base}")
				isFirst = false
			}
		}

		assert(!result.successful || action is RunnableTerminateExplorationAction)

		// Propagate exception if there was any
		if (!result.successful)
			output.exception = result.exception

		output.explorationEndTime = timeProvider.getNow()

		return output
	}

	@Throws(DeviceException::class)
	private fun tryDeviceHasPackageInstalled(device: IExplorableAndroidDevice, packageName: String) {
		log.trace("tryDeviceHasPackageInstalled(device, $packageName)")

		if (!device.hasPackageInstalled(packageName))
			throw DeviceException()
	}

	@Throws(DeviceException::class)
	private fun tryWarnDeviceDisplaysHomeScreen(device: IExplorableAndroidDevice, fileName: String) {
		log.trace("tryWarnDeviceDisplaysHomeScreen(device, $fileName)")

		val initialGuiSnapshot = device.perform(FetchGUI())

		if (!initialGuiSnapshot.isHomeScreen)
			log.warn(Markers.appHealth,
					"An exploration process for $fileName is about to start but the device doesn't display home screen. " +
							"Instead, its GUI state is: $initialGuiSnapshot.guiStatus. " +
							"Continuing the exploration nevertheless, hoping that the first \"reset app\" " +
							"exploration action will force the device into the home screen.")
	}
}