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
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.IExplorableAndroidDevice
import org.droidmate.device.android_sdk.Apk
import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.deviceInterface.IRobustDevice
import org.droidmate.device.error.DeviceException
import org.droidmate.device.error.DeviceExceptionMissing
import org.droidmate.device.exception
import org.droidmate.device.execute
import org.droidmate.device.logcat.ApiLogcatMessage
import org.droidmate.device.logcat.ApiLogcatMessageListExtensions
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.resetApp
import org.droidmate.exploration.modelFeatures.ModelFeature
import org.droidmate.exploration.strategy.IExplorationStrategy
import org.droidmate.explorationModel.ModelFeatureI
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.path.cleanDirs
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.debugT
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.factory.ModelProvider
import org.droidmate.explorationModel.interaction.ActionResult
import org.droidmate.explorationModel.interaction.EmptyActionResult
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.logging.Markers
import org.droidmate.misc.EnvironmentConstants
import org.droidmate.misc.FailableExploration
import org.droidmate.misc.deleteDir
import org.droidmate.tools.IAndroidDeviceDeployer
import org.droidmate.tools.IApkDeployer
import org.droidmate.tools.IApksProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

open class ExploreCommand<M,S,W> constructor(private val cfg: ConfigurationWrapper,
                                      private val apksProvider: IApksProvider,
                                      private val deviceDeployer: IAndroidDeviceDeployer,
                                      private val apkDeployer: IApkDeployer,
                                      private val strategyProvider: (ExplorationContext<M,S,W>) -> IExplorationStrategy,
                                      private var modelProvider: ModelProvider<M>,
									  val watcher: MutableList<ModelFeatureI> = mutableListOf()) where M: AbstractModel<S, W>, S: State<W>, W: Widget {
	companion object {
		@JvmStatic
		protected val log: Logger by lazy { LoggerFactory.getLogger(ExploreCommand::class.java) }
	}

	suspend fun execute(cfg: ConfigurationWrapper): Map<Apk, FailableExploration> = supervisorScope {
		if (cfg[cleanDirs]) {
			cleanOutputDir(cfg)
		}

		val reportDir = cfg.droidmateOutputReportDirPath

		if (!Files.exists(reportDir)) {
			withContext(Dispatchers.IO) { Files.createDirectories(reportDir) }
		}

		assert(Files.exists(reportDir)) { "Unable to create report directory ($reportDir)" }

		val apks = apksProvider.getApks(cfg.apksDirPath, cfg[apksLimit], cfg[apkNames], cfg[shuffleApks])

		if (!validateApks(apks, cfg[runOnNotInlined])) {
			emptyMap()
		} else {
			val explorationData = execute(cfg, apks)

			onFinalFinished()
			log.info("Writing reports finished.")

			explorationData
		}
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

	private suspend fun ExplorationContext<M,S,W>.verify() {
		try {
			assert(this.explorationTrace.size > 0) { "Exploration trace should not be empty" }
			assert(this.explorationStartTime > LocalDateTime.MIN) { "Start date/time not set for exploration" }
			assert(this.explorationEndTime > LocalDateTime.MIN) { "End date/time not set for exploration" }

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

	private fun ExplorationContext<M,S,W>.assertLogsAreSortedByTime() {
		val apiLogs = explorationTrace.getActions()
				.mapQueueToSingleElement()
				.flatMap { deviceLog -> deviceLog.deviceLogs.map { ApiLogcatMessage.from(it) } }

		assert(explorationStartTime <= explorationEndTime)

		val ret = ApiLogcatMessageListExtensions.sortedByTimePerPID(apiLogs)
		assert(ret)
	}

	private suspend fun pullScreenShot(actionId: Int, targetDir: Path, device: IRobustDevice, eContext: ExplorationContext<M,S,W>) = withTimeoutOrNull(10000){
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

                if(!File(dstFile.toString()).exists())
                    log.warn("unable to fetch state image for action $actionId")
			}
		}, inMillis = true)

	}
	private suspend fun explorationLoop(app: IApk, device: IRobustDevice): FailableExploration {
		log.debug("explorationLoop(app=${app.fileName}, device)")

		// initialize the config and clear the 'currentModel' from the provider if any
		modelProvider.initConfig(ModelConfig(appName = app.packageName, cfg = cfg))
		// Use the received exploration eContext (if any) otherwise construct the object that
		// will hold the exploration output and that will be returned from this method.
		// Note that a different eContext is created for each exploration if none it provider
		val explorationContext = ExplorationContext(
			cfg,
			app, { device.readStatements() },
			LocalDateTime.now(),
			watcher = watcher,
			modelProvider = modelProvider
		)

		log.debug("Exploration start time: " + explorationContext.explorationStartTime)

		// Construct initial action and execute it on the device to obtain initial result.
		var action: ExplorationAction = EmptyAction
		var result: ActionResult = EmptyActionResult
		var capturedPreviously = false

		var isFirst = true

		var strategy: IExplorationStrategy? = null
		try {
			strategy = strategyProvider.invoke(explorationContext)
			// Execute the exploration loop proper, starting with the values of initial reset action and its result.
			while (isFirst || !action.isTerminate()) {
				try {
					// decide for an action
					action = strategy.decide(result) // check if we need to initialize timeProvider.getNow() here
					// execute action
					result = action.execute(app, device)

					if (cfg[ConfigProperties.UiAutomatorServer.delayedImgFetch]) {
						if (capturedPreviously && action is ActionQueue) {
							action.actions.forEachIndexed { i, a ->
								log.debug("action queue element {} should have screenshot for ExploreCommand {}",i,a)
								if (i < action.actions.size - 1 &&
										((a is TextInsert && action.actions[i + 1] is Click)
												|| a is Swipe))
									pullScreenShot(a.id, explorationContext.model.config.imgDst, device, explorationContext)
							}
						}
						if(result.guiSnapshot.capturedScreen){
							val id = if(action.isTerminate()) action.id +1 else action.id // terminate is not send to the device instead we terminate the app process and issue Fetch which will have a higher id value
							log.debug("action {} should have screenshot for ExploreCommand {}",id,action)
							pullScreenShot(id, explorationContext.model.config.imgDst, device, explorationContext)
						}
					}
					capturedPreviously = result.guiSnapshot.capturedScreen

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
							" ${e.localizedMessage}",e)
					explorationContext.exceptions.add(e)
					explorationContext.resetApp().execute(app,device)
				}
			} // end loop

			explorationContext.explorationEndTime = LocalDateTime.now()
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

		return FailableExploration(explorationContext, explorationContext.exceptions)
	}

	@Throws(DeviceException::class)
	private suspend fun tryDeviceHasPackageInstalled(device: IExplorableAndroidDevice, packageName: String) {
		log.trace("tryDeviceHasPackageInstalled(device, $packageName)")

		if (!device.hasPackageInstalled(packageName))
			throw DeviceException("Package $packageName not installed.")
	}

	@Throws(DeviceException::class)
	private suspend fun tryWarnDeviceDisplaysHomeScreen(device: IExplorableAndroidDevice, fileName: String) {
		log.trace("tryWarnDeviceDisplaysHomeScreen(device, $fileName)")
		try {
			val initialGuiSnapshot = device.perform(GlobalAction(ActionType.FetchGUI))

			if (!initialGuiSnapshot.isHomeScreen)
				log.warn(
					Markers.appHealth,
					"An exploration process for $fileName is about to start but the device doesn't display home screen. " +
							"Instead, its GUI state is: $initialGuiSnapshot.guiStatus. " +
							"Continuing the exploration nevertheless, hoping that the first \"reset app\" " +
							"exploration action will force the device into the home screen."
				)
		}catch(e: Throwable){
			log.warn("initial fetch (warnIfNotHomeScreen) failed",e)
		}
	}
}
