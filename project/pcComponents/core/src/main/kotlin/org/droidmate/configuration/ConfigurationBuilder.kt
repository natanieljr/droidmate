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

package org.droidmate.configuration

import ch.qos.logback.classic.Level
import com.konradjamrozik.Resource
import com.konradjamrozik.ResourcePath
import com.konradjamrozik.createDirIfNotExists
import com.konradjamrozik.toList
import com.natpryce.konfig.*
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.apache.commons.lang3.builder.StandardToStringStyle
import org.droidmate.configuration.ConfigProperties.ApiMonitorServer.monitorSocketTimeout
import org.droidmate.configuration.ConfigProperties.ApiMonitorServer.monitorUseLogcat
import org.droidmate.configuration.ConfigProperties.Core.configPath
import org.droidmate.configuration.ConfigProperties.Core.logLevel
import org.droidmate.configuration.ConfigProperties.Deploy.deployRawApks
import org.droidmate.configuration.ConfigProperties.Deploy.installApk
import org.droidmate.configuration.ConfigProperties.Deploy.installAux
import org.droidmate.configuration.ConfigProperties.Deploy.replaceResources
import org.droidmate.configuration.ConfigProperties.Deploy.shuffleApks
import org.droidmate.configuration.ConfigProperties.Deploy.uninstallApk
import org.droidmate.configuration.ConfigProperties.Deploy.uninstallAux
import org.droidmate.configuration.ConfigProperties.Deploy.useApkFixturesDir
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.checkAppIsRunningRetryAttempts
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.checkAppIsRunningRetryDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.checkDeviceAvailableAfterRebootAttempts
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.checkDeviceAvailableAfterRebootFirstDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.checkDeviceAvailableAfterRebootLaterDelays
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.stopAppRetryAttempts
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.stopAppSuccessCheckDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.waitForCanRebootDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.deviceOperationAttempts
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.deviceOperationDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.waitForDevice
import org.droidmate.configuration.ConfigProperties.ExecutionMode.coverage
import org.droidmate.configuration.ConfigProperties.ExecutionMode.explore
import org.droidmate.configuration.ConfigProperties.ExecutionMode.inline
import org.droidmate.configuration.ConfigProperties.ExecutionMode.report
import org.droidmate.configuration.ConfigProperties.Exploration.apiVersion
import org.droidmate.configuration.ConfigProperties.Exploration.apkNames
import org.droidmate.configuration.ConfigProperties.Exploration.apksDir
import org.droidmate.configuration.ConfigProperties.Exploration.apksLimit
import org.droidmate.configuration.ConfigProperties.Exploration.deviceIndex
import org.droidmate.configuration.ConfigProperties.Exploration.deviceSerialNumber
import org.droidmate.configuration.ConfigProperties.Exploration.launchActivityDelay
import org.droidmate.configuration.ConfigProperties.Exploration.launchActivityTimeout
import org.droidmate.configuration.ConfigProperties.Exploration.runOnNotInlined
import org.droidmate.configuration.ConfigProperties.Output.coverageDir
import org.droidmate.configuration.ConfigProperties.Output.outputDir
import org.droidmate.configuration.ConfigProperties.Output.reportDir
import org.droidmate.configuration.ConfigProperties.Output.screenshotDir
import org.droidmate.configuration.ConfigProperties.Report.includePlots
import org.droidmate.configuration.ConfigProperties.Report.inputDir
import org.droidmate.configuration.ConfigProperties.Selectors.actionLimit
import org.droidmate.configuration.ConfigProperties.Selectors.playbackModelDir
import org.droidmate.configuration.ConfigProperties.Selectors.pressBackProbability
import org.droidmate.configuration.ConfigProperties.Selectors.randomSeed
import org.droidmate.configuration.ConfigProperties.Selectors.resetEvery
import org.droidmate.configuration.ConfigProperties.Selectors.stopOnExhaustion
import org.droidmate.configuration.ConfigProperties.Selectors.timeLimit
import org.droidmate.configuration.ConfigProperties.Selectors.widgetIndexes
import org.droidmate.configuration.ConfigProperties.Strategies.Parameters.uiRotation
import org.droidmate.configuration.ConfigProperties.Strategies.allowRuntimeDialog
import org.droidmate.configuration.ConfigProperties.Strategies.back
import org.droidmate.configuration.ConfigProperties.Strategies.denyRuntimeDialog
import org.droidmate.configuration.ConfigProperties.Strategies.fitnessProportionate
import org.droidmate.configuration.ConfigProperties.Strategies.minimizeMaximize
import org.droidmate.configuration.ConfigProperties.Strategies.modelBased
import org.droidmate.configuration.ConfigProperties.Strategies.playback
import org.droidmate.configuration.ConfigProperties.Strategies.reset
import org.droidmate.configuration.ConfigProperties.Strategies.rotateUI
import org.droidmate.configuration.ConfigProperties.Strategies.terminate
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.basePort
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.socketTimeout
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.startTimeout
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.waitForInteractableTimeout
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.waitForIdleTimeout
import org.droidmate.logging.Markers.Companion.runData
import org.droidmate.misc.BuildConstants
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.*

/**
 * @see IConfigurationBuilder#build(java.lang.String [ ], java.nio.file.FileSystem)
 */
class ConfigurationBuilder : IConfigurationBuilder {
	@Throws(ConfigurationException::class)
	override fun build(args: Array<String>): ConfigurationWrapper = build(args, FileSystems.getDefault())

	@Throws(ConfigurationException::class)
	override fun build(args: Array<String>, fs: FileSystem): ConfigurationWrapper = build(parseArgs(args,
			// Core
			CommandLineOption(logLevel, description = "Logging level of the entirety of application. Possible values, comma separated: info, debug, trace, warn, error."),
			CommandLineOption(configPath, description = "Path to a custom configuration file, which replaces the default configuration.", short = "config"),
			// ApiMonitorServer
			CommandLineOption(monitorSocketTimeout, description = "Socket timeout to communicate with the API monitor service."),
			CommandLineOption(monitorUseLogcat, description = "Use logical for API logging instead of TCPServer (deprecated)."),
			CommandLineOption(ConfigProperties.ApiMonitorServer.basePort, description = "The base port for the communication with the the API monitor service. DroidMate communicates over this base port + device index."),
			// ExecutionMode
			CommandLineOption(inline, description = "If present, instead of normal run, DroidMate will inline all non-inlined apks. Before inlining backup copies of the apks will be created and put into a sub-directory of the directory containing the apks. This flag cannot be combined with another execution mode."),
			CommandLineOption(report, description = "If present, instead of normal run, DroidMate will generate reports from previously serialized data. This flag cannot be combined with another execution mode."),
			CommandLineOption(explore, description = "Run DroidMate in exploration mode."),
			CommandLineOption(coverage, description = "If present, instead of normal run, DroidMate will run in 'instrument APK for coverage' mode. This flag cannot be combined with another execution mode."),
			// Deploy
			CommandLineOption(installApk, description = "Reinstall the app to the device. If the app is not previously installed the exploration will fail"),
			CommandLineOption(installAux, description = "Reinstall the auxiliary files (UIAutomator and Monitor) to the device. If the auxiliary files are not previously installed the exploration will fail."),
			CommandLineOption(uninstallApk, description = "Uninstall the APK after the exploration."),
			CommandLineOption(uninstallAux, description = "Uninstall auxiliary files (UIAutomator and Monitor) after the exploration."),
			CommandLineOption(replaceResources, description = "Replace the resources from the extracted resources folder upon execution."),
			CommandLineOption(shuffleApks, description = "ExplorationStrategy the apks in the input directory in a random order."),
			CommandLineOption(deployRawApks, description = "Deploys apks to device in 'raw' form, that is, without instrumenting them. Will deploy them raw even if instrumented version is available from last run."),
			// DeviceCommunication
			CommandLineOption(checkAppIsRunningRetryAttempts, description = "Number of attempts to check if an app is running on the device."),
			CommandLineOption(checkAppIsRunningRetryDelay, description = "Timeout for each attempt to check if an app is running on the device in milliseconds."),
			CommandLineOption(checkDeviceAvailableAfterRebootAttempts, description = "Determines how often DroidMate checks if a device is available after a reboot."),
			CommandLineOption(checkDeviceAvailableAfterRebootFirstDelay, description = "The first timeout after a device rebooted, before its availability will be checked."),
			CommandLineOption(checkDeviceAvailableAfterRebootLaterDelays, description = "The non-first timeout after a device rebooted, before its availability will be checked."),
			CommandLineOption(stopAppRetryAttempts, description = "Number of attempts to close an 'application has stopped' dialog."),
			CommandLineOption(stopAppSuccessCheckDelay, description = "Delay after each failed attempt close an 'application has stopped' dialog"),
			CommandLineOption(waitForCanRebootDelay, description = "Delay (in milliseconds) after an attempt was made to reboot a device, before."),
			CommandLineOption(deviceOperationAttempts, description = "Number of attempts to retry other failed device operations."),
			CommandLineOption(deviceOperationDelay, description = "Delay (in milliseconds) after an attempt was made to perform a device operation, before retrying again."),
			CommandLineOption(waitForDevice, description = "Wait for a device to be connected to the PC instead of cancelling the exploration."),
			// Exploration
			CommandLineOption(apksDir, description = "Directory containing the apks to be processed by DroidMate."),
			CommandLineOption(apksLimit, description = "Limits the number of apks on which DroidMate will run. 0 means no limit."),
			CommandLineOption(apkNames, description = "Filters apps on which DroidMate will be run. Supply full file names, separated by commas, surrounded by square brackets. If the list is empty, it will run on all the apps in the apks dir. Example value: [app1.apk, app2.apk]"),
			CommandLineOption(deviceIndex, description = "Index of the device to be used (from adb devices). Zero based."),
			CommandLineOption(deviceSerialNumber, description = "Serial number of the device to be used. Mutually exclusive to index."),
			CommandLineOption(runOnNotInlined, description = "Allow DroidMate to run on non-inlined apks."),
			CommandLineOption(launchActivityDelay, description = "Delay (in milliseconds) to wait for the app to load before continuing the exploration after a reset (or exploration start)."),
			CommandLineOption(launchActivityTimeout, description = "Maximum amount of time to be waited for an app to start after a reset in milliseconds."),
			CommandLineOption(apiVersion, description = "Has to be set to the Android API version corresponding to the (virtual) devices on which DroidMate will run. Currently supported values: api23"),
			// Output
			CommandLineOption(outputDir, description = "Path to the directory that will contain DroidMate exploration output."),
			CommandLineOption(coverageDir, description = "Path to the directory that will contain the coverage data."),
			CommandLineOption(screenshotDir, description = "Path to the directory that will contain the screenshots from an exploration."),
			CommandLineOption(reportDir, description = "Path to the directory that will contain the report files."),
			// Strategies
			CommandLineOption(reset, description = "Enables use of the reset strategy during an exploration."),
			CommandLineOption(ConfigProperties.Strategies.explore, description = "Enables use of biased random exploration strategy."),
			CommandLineOption(terminate, description = "Enables use of default terminate strategy."),
			CommandLineOption(back, description = "Enables use of 'press back button' strategy"),
			CommandLineOption(modelBased, description = "Enables use of random exploration strategy using static model."),
			CommandLineOption(fitnessProportionate, description = "Enables use of random exploration strategy using static model and fitness proportionate selection."),
			CommandLineOption(allowRuntimeDialog, description = "Enables use of strategy to always click 'Allow' on permission dialogs."),
			CommandLineOption(denyRuntimeDialog, description = "Enables use of strategy to always click 'Deny' on permission dialogs."),
			CommandLineOption(playback, description = "Enables use of playback strategy (if a playback model is provided)."),
			CommandLineOption(ConfigProperties.Strategies.dfs, description = "Enables use of Depth-First-Search strategy."),
			CommandLineOption(rotateUI, description = "Enables use of Rotate UI strategy."),
			CommandLineOption(minimizeMaximize, description = "Enables use of Minimize-Maximize strategy to attempt to close the app and reopen it on the same screen."),
			// Strategies parameters
			CommandLineOption(uiRotation, description = "Value of the UI rotation for Rotate UI strategy. Valid values are: 0, 90, 180, 270. Other values will be rounded to one of these."),

			// Selectors
			CommandLineOption(pressBackProbability, description = "Probability of randomly pressing the back button while exploring. Set to 0 to disable the press back strategy."),
			CommandLineOption(widgetIndexes, description = "Makes the exploration strategy to choose widgets to click that have the indexes as provided by this parameter, in sequence. The format is: [<first widget index>,<second widget index>,...<nth widget index>], starting indexing at 0. Example: [0,7,3]"),
			CommandLineOption(playbackModelDir, description = "Directory of a previous exploration model. Required for playback."),
			CommandLineOption(resetEvery, description = "Number of actions to automatically reset the exploration from its initial activity. Set to 0 to disable."),
			CommandLineOption(actionLimit, description = "How many actions the GUI exploration strategy can conduct before terminating."),
			CommandLineOption(timeLimit, description = "How long the exploration of any given apk should take, in seconds. If set to 0, instead actionsLimit will be used."),
			CommandLineOption(randomSeed, description = "The seed for a random generator used by a random-clicking GUI exploration strategy. If null, a seed will be randomized."),
			CommandLineOption(stopOnExhaustion, description = "Terminate exploration when all widgets have been explored at least 1x."),
			CommandLineOption(ConfigProperties.Selectors.dfs, description = "Use Depth-First-Search strategy, if the strategy is registered."),
			// Report
			CommandLineOption(inputDir, description = "Path to the directory containing report input. The input is to be DroidMate exploration output."),
			CommandLineOption(includePlots, description = "Include plots on reports (requires gnu plot)."),
			// UiAutomatorServer
			CommandLineOption(startTimeout, description = "How long DroidMate should wait, in milliseconds, for message on logcat confirming that UiAutomatorDaemonServer has started on android (virtual) device."),
			CommandLineOption(waitForIdleTimeout, description = "Timeout for a device to be idle an operation."),
			CommandLineOption(waitForInteractableTimeout, description = "Timeout for a widget to be available after an operation."),
			CommandLineOption(socketTimeout, description = "Socket timeout to communicate with the UiDaemonServer."),
			CommandLineOption(basePort, description = "The base port for the communication with the devices. DroidMate communicates over this base port + device index.")
			).first, fs)

	@Throws(ConfigurationException::class)
	override fun build(cmdLineConfig: Configuration, fs: FileSystem): ConfigurationWrapper {
		val defaultConfig = ConfigurationProperties.fromResource("defaultConfig.properties")

		val customFile = when {
			cmdLineConfig.contains(configPath) -> File(cmdLineConfig[configPath].path)
			defaultConfig.contains(configPath) -> File(defaultConfig[configPath].path)
			else -> null
		}

		val config : Configuration =
				// command line
				cmdLineConfig overriding
				// overrides custom config file
				(if (customFile?.exists() == true)
					ConfigurationProperties.fromFile(customFile)
				else
					cmdLineConfig) overriding
				// overrides default config file
				defaultConfig

		// Set the logging directory for the logback logger as early as possible
		val outputPath = Paths.get(config[outputDir].toString())
								.resolve(getDeviceDir(config))
								.resolve(ConfigurationWrapper.log_dir_name)

		System.setProperty("logsDir", outputPath.toString())
		assert(System.getProperty("logsDir") == outputPath.toString())

		return ConfigurationBuilder.memoizedBuildConfiguration(config, fs)
	}

	companion object {
		private val log by lazy { LoggerFactory.getLogger(ConfigurationBuilder::class.java) }

		@JvmStatic
		private fun memoizedBuildConfiguration(cfg: Configuration, fs: FileSystem): ConfigurationWrapper {
			log.debug("memoizedBuildConfiguration(args, fileSystem)")

			return bindAndValidate( ConfigurationWrapper(cfg, fs) )
		}

		@JvmStatic
		@Throws(ConfigurationException::class)
		private fun bindAndValidate(config: ConfigurationWrapper): ConfigurationWrapper {
			try {
				setLogbackRootLoggerLoggingLevel(config)
				setupResourcesAndPaths(config)
				validateExplorationSettings(config)
				normalizeAndroidApi(config)

				// This increment is done so each connected device will have its uiautomator-daemon reachable on a separate port.
				//config.uiautomatorDaemonTcpPort += config.deviceIndex

			} catch (e: ConfigurationException) {
				throw e
			}

			logConfigurationInEffect(config)

			return config
		}

		@JvmStatic
		private fun normalizeAndroidApi(config: ConfigurationWrapper) {
			// Currently supports only API23 as configuration (works with API 24, 25 and 26 as well)
			assert(config[apiVersion] == ConfigurationWrapper.api23)
		}

		@JvmStatic
		private fun validateExplorationSettings(cfg: ConfigurationWrapper) {
			validateExplorationStrategySettings(cfg)

			val apkNames = Files.list(cfg.getPath(cfg[apksDir]))
					.filter { it.toString().endsWith(".apk") }
					.map { it -> it.fileName.toString() }
					.toList()

			if (cfg[deployRawApks] && arrayListOf("inlined", "monitored").any { apkNames.contains(it) })
				throw ConfigurationException(
						"DroidMate was instructed to deploy raw apks, while the apks dir contains an apk " +
								"with 'inlined' or 'monitored' in its name. Please do not mix such apk with raw apks in one dir.\n" +
								"The searched apks dir path: ${cfg.getPath(cfg[apksDir]).toAbsolutePath()}")
		}

		@JvmStatic
		private fun validateExplorationStrategySettings(cfg: ConfigurationWrapper) {
			/*val settingsCount = widgetClickingStrategySettingsCount(cfg)

			if (settingsCount > 1)
				throw ConfigurationException("Exploration strategy has been configured in too many different ways. Only one of the following expressions can be true:\n" +
						"randomSeed >= null: ${cfg[randomSeed] >= 0}\n" +
						"widgetIndexes.isNotEmpty(): ${cfg[widgetIndexes].isNotEmpty()}")*/

			if (cfg[randomSeed] == -1L) {
				log.info("Generated random seed: ${cfg.randomSeed}")
			}
		}

		/*@JvmStatic
		private fun widgetClickingStrategySettingsCount(cfg: ConfigurationWrapper): Int =
				arrayListOf(cfg.alwaysClickFirstWidget, cfg.widgetIndexes.isNotEmpty()).map { if (it) 1 else 0 }.sum()*/

		@JvmStatic
		private fun getDeviceDir(cfg: Configuration): String {
			// If not using main device, export again
			return when {
				cfg[deviceSerialNumber].isNotEmpty() -> "device" + cfg[deviceSerialNumber].replace(":", "-")
				cfg[deviceIndex] > 0 -> "device" + cfg[deviceIndex]
				else -> ""
			}
		}

		@JvmStatic
		private fun getResourcePath(cfg: ConfigurationWrapper, resourceName: String): Path {
			val path = cfg.resourceDir.resolve(resourceName)

			if (!cfg[replaceResources] && Files.exists(path))
				return path

			return Resource(resourceName).extractTo(cfg.resourceDir)
		}

		@JvmStatic
		@Throws(ConfigurationException::class)
		private fun setupResourcesAndPaths(cfg: ConfigurationWrapper) {
			cfg.droidmateOutputDirPath = cfg.getPath(cfg[outputDir])
					.resolve(getDeviceDir(cfg)).toAbsolutePath()
			cfg.resourceDir = cfg.droidmateOutputDirPath
					.resolve(BuildConstants.dir_name_temp_extracted_resources)
			cfg.droidmateOutputReportDirPath = cfg.droidmateOutputDirPath
					.resolve(cfg[reportDir]).toAbsolutePath()
			cfg.coverageReportDirPath = cfg.droidmateOutputDirPath
					.resolve(cfg[coverageDir]).toAbsolutePath()
			cfg.reportInputDirPath = cfg.getPath(cfg[ConfigProperties.Report.inputDir]).toAbsolutePath()

			cfg.uiautomator2DaemonApk = getResourcePath(cfg, "deviceControlDaemon.apk").toAbsolutePath()
			log.info("Using uiautomator2-daemon.apk located at ${cfg.uiautomator2DaemonApk}")

			cfg.uiautomator2DaemonTestApk = getResourcePath(cfg, "deviceControlDaemon-test.apk").toAbsolutePath()
			log.info("Using uiautomator2-daemon-test.apk located at ${cfg.uiautomator2DaemonTestApk}")

			cfg.monitorApkApi23 = getResourcePath(cfg, BuildConstants.monitor_api23_apk_name).toAbsolutePath()
			log.info("Using ${BuildConstants.monitor_api23_apk_name} located at ${cfg.monitorApkApi23}")

			cfg.apiPoliciesFile = getResourcePath(cfg, BuildConstants.api_policies_file_name).toAbsolutePath()
			log.info("Using ${BuildConstants.api_policies_file_name} located at ${cfg.apiPoliciesFile}")

			cfg.apksDirPath = if (cfg[useApkFixturesDir])
				ResourcePath(BuildConstants.apk_fixtures).path.toAbsolutePath()
			else
				cfg.getPath(cfg[apksDir]).toAbsolutePath()

			if (cfg.apksDirPath.createDirIfNotExists())
				log.info("Created directory from which DroidMate will read input apks: " + cfg.apksDirPath.toAbsolutePath().toString())

			if (Files.notExists(cfg.droidmateOutputDirPath)) {
				Files.createDirectories(cfg.droidmateOutputDirPath)
				log.info("Created directory to which DroidMate will output: " + cfg.droidmateOutputDirPath.toAbsolutePath().toString())
			}
		}

		@JvmStatic
		@Throws(ConfigurationException::class)
		private fun setLogbackRootLoggerLoggingLevel(config: ConfigurationWrapper) {
			val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
			val explorationLogger = LoggerFactory.getLogger("org.droidmate.exploration")

			if (rootLogger is ch.qos.logback.classic.Logger) {
				if (config[logLevel].toLowerCase() in arrayListOf("info", "debug", "trace", "warn", "error")) {
					rootLogger.level = Level.toLevel(config[logLevel])
					if (explorationLogger is ch.qos.logback.classic.Logger)
						explorationLogger.level = Level.toLevel(config[logLevel])
				} else
					throw ConfigurationException("Unrecognized logging level. Given level: ${config[logLevel]}. Expected one of levels: info debug trace")
			}
			else{
				rootLogger.warn("Logger is using ${rootLogger::class.java.simpleName} instead of ${ch.qos.logback.classic.Logger::class.java.simpleName}, cannot configure the log level.")
			}
		}

		/*
* To keep the source DRY, we use apache's ReflectionToStringBuilder, which gets the field names and values using
* reflection.
*/
		@JvmStatic
		private fun logConfigurationInEffect(config: Configuration) {

			// The customized display style strips the output of any data except the field name=value pairs.
			val displayStyle = StandardToStringStyle()
			displayStyle.isArrayContentDetail = true
			displayStyle.isUseClassName = false
			displayStyle.isUseIdentityHashCode = false
			displayStyle.contentStart = ""
			displayStyle.contentEnd = ""
			displayStyle.fieldSeparator = System.lineSeparator()

			var configurationDump = ReflectionToStringBuilder(config, displayStyle).toString()
			configurationDump = configurationDump.split(System.lineSeparator()).sorted().joinToString(System.lineSeparator())

			log.info(runData, "--------------------------------------------------------------------------------")
			log.info(runData, "")
			log.info(runData, "Working dir:   ${System.getProperty("user.dir")}")
			log.info(runData, "")
			log.info(runData, "JVM arguments: ${readJVMArguments()}")
			log.info(runData, "")
			log.debug(runData, "Configuration dump:")
			log.debug(runData, "")
			log.debug(runData, configurationDump)
			log.debug(runData, "")
			log.debug(runData, "End of configuration dump")
			log.info(runData, "")
			log.info(runData, "--------------------------------------------------------------------------------")

		}

		/**
		 * Based on: http://stackoverflow.com/a/1531999/986533
		 */
		@JvmStatic
		private fun readJVMArguments(): List<String> = ManagementFactory.getRuntimeMXBean().inputArguments
	}
}
