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
import org.droidmate.configuration.ConfigProperties.ApiMonitorServer.monitorUseLegacyStream
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
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.clearPackageRetryAttempts
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.clearPackageRetryDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.closeANRAttempts
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.closeANRDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.getValidGuiSnapshotRetryAttempts
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.getValidGuiSnapshotRetryDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.stopAppRetryAttempts
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.stopAppSuccessCheckDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.waitForCanRebootDelay
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
import org.droidmate.configuration.ConfigProperties.Output.droidmateOutputDirPath
import org.droidmate.configuration.ConfigProperties.Output.reportDir
import org.droidmate.configuration.ConfigProperties.Output.screenshotDir
import org.droidmate.configuration.ConfigProperties.Report.includePlots
import org.droidmate.configuration.ConfigProperties.Report.inputDir
import org.droidmate.configuration.ConfigProperties.Selectors.actionLimit
import org.droidmate.configuration.ConfigProperties.Selectors.playbackModelDir
import org.droidmate.configuration.ConfigProperties.Selectors.pressBackProbability
import org.droidmate.configuration.ConfigProperties.Selectors.randomSeed
import org.droidmate.configuration.ConfigProperties.Selectors.resetEvery
import org.droidmate.configuration.ConfigProperties.Selectors.timeLimit
import org.droidmate.configuration.ConfigProperties.Selectors.widgetIndexes
import org.droidmate.configuration.ConfigProperties.Strategies.allowRuntimeDialog
import org.droidmate.configuration.ConfigProperties.Strategies.back
import org.droidmate.configuration.ConfigProperties.Strategies.denyRuntimeDialog
import org.droidmate.configuration.ConfigProperties.Strategies.fitnessProportionate
import org.droidmate.configuration.ConfigProperties.Strategies.modelBased
import org.droidmate.configuration.ConfigProperties.Strategies.playback
import org.droidmate.configuration.ConfigProperties.Strategies.reset
import org.droidmate.configuration.ConfigProperties.Strategies.terminate
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.basePort
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.socketTimeout
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.startQueryDelay
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.startTimeout
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.waitForGuiToStabilize
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.waitForWindowUpdateTimeout
import org.droidmate.logging.Markers.Companion.runData
import org.droidmate.misc.BuildConstants
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

/**
 * @see IConfigurationBuilder#build(java.lang.String [ ], java.nio.file.FileSystem)
 */
class ConfigurationBuilder : IConfigurationBuilder {
	@Throws(ConfigurationException::class)
	override fun build(args: Array<String>): ConfigurationWrapper = build(args, FileSystems.getDefault())

	@Throws(ConfigurationException::class)
	override fun build(args: Array<String>, fs: FileSystem): ConfigurationWrapper = build(parseArgs(args,
			CommandLineOption(logLevel), CommandLineOption(configPath, short = "config"),
			CommandLineOption(monitorSocketTimeout), CommandLineOption(monitorUseLogcat),
			CommandLineOption(monitorUseLegacyStream), CommandLineOption(ConfigProperties.ApiMonitorServer.basePort),
			CommandLineOption(inline), CommandLineOption(report), CommandLineOption(explore), CommandLineOption(coverage),
			CommandLineOption(installApk), CommandLineOption(installAux), CommandLineOption(uninstallApk),
			CommandLineOption(uninstallAux), CommandLineOption(replaceResources), CommandLineOption(shuffleApks), 
			CommandLineOption(useApkFixturesDir), CommandLineOption(deployRawApks), 
			CommandLineOption(checkAppIsRunningRetryAttempts), CommandLineOption(checkAppIsRunningRetryDelay),
			CommandLineOption(checkDeviceAvailableAfterRebootAttempts), CommandLineOption(checkDeviceAvailableAfterRebootFirstDelay),
			CommandLineOption(checkDeviceAvailableAfterRebootLaterDelays), CommandLineOption(clearPackageRetryAttempts),
			CommandLineOption(clearPackageRetryDelay), CommandLineOption(closeANRAttempts), CommandLineOption(closeANRDelay),
			CommandLineOption(getValidGuiSnapshotRetryAttempts), CommandLineOption(getValidGuiSnapshotRetryDelay),
			CommandLineOption(stopAppRetryAttempts), CommandLineOption(stopAppSuccessCheckDelay),
			CommandLineOption(waitForCanRebootDelay), CommandLineOption(waitForDevice), CommandLineOption(apksDir),
			CommandLineOption(apksLimit), CommandLineOption(apkNames), CommandLineOption(deviceIndex),
			CommandLineOption(deviceSerialNumber), CommandLineOption(runOnNotInlined), CommandLineOption(launchActivityDelay),
			CommandLineOption(launchActivityTimeout), CommandLineOption(apiVersion), CommandLineOption(droidmateOutputDirPath),
			CommandLineOption(coverageDir), CommandLineOption(screenshotDir), CommandLineOption(reportDir),
			CommandLineOption(reset), CommandLineOption(ConfigProperties.Strategies.explore), CommandLineOption(terminate),
			CommandLineOption(back), CommandLineOption(modelBased), CommandLineOption(fitnessProportionate),
			CommandLineOption(allowRuntimeDialog), CommandLineOption(denyRuntimeDialog), CommandLineOption(playback),
			CommandLineOption(pressBackProbability), CommandLineOption(widgetIndexes), CommandLineOption(playbackModelDir),
			CommandLineOption(resetEvery), CommandLineOption(actionLimit), CommandLineOption(timeLimit),
			CommandLineOption(randomSeed), CommandLineOption(inputDir), CommandLineOption(includePlots),
			CommandLineOption(startTimeout), CommandLineOption(startQueryDelay), CommandLineOption(socketTimeout),
			CommandLineOption(waitForGuiToStabilize), CommandLineOption(waitForWindowUpdateTimeout), CommandLineOption(basePort)
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

		return ConfigurationBuilder.memoizedBuildConfiguration(config, fs)
	}

	companion object {
		private val log = LoggerFactory.getLogger(ConfigurationBuilder::class.java)

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
		private fun getDeviceDir(cfg: Configuration): String{
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
			cfg.droidmateOutputDirPath = cfg.getPath(cfg[droidmateOutputDirPath])
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

			cfg.coverageMonitorScriptPath = getResourcePath(cfg, BuildConstants.coverage_monitor_script).toAbsolutePath()
			log.info("Using ${BuildConstants.coverage_monitor_script} located at ${cfg.coverageMonitorScriptPath}")

			val portFile = File.createTempFile(BuildConstants.port_file_name, ".tmp")
			portFile.writeText(Integer.toString(cfg.monitorPort))
			portFile.deleteOnExit()
			cfg.portFile = portFile.toPath().toAbsolutePath()
			log.info("Using ${BuildConstants.port_file_name} located at ${cfg.portFile}")

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
			val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
			val explorationLogger = LoggerFactory.getLogger("org.droidmate.exploration") as ch.qos.logback.classic.Logger

			if (config[logLevel].toLowerCase() in arrayListOf("info", "debug", "trace", "warn", "error")) {
				rootLogger.level = Level.toLevel(config[logLevel])
				explorationLogger.level = Level.toLevel(config[logLevel])
			} else
				throw ConfigurationException(String.format(
						"Unrecognized logging level. Given level: %s. Expected one of levels: info debug trace",
						config[logLevel]))
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
