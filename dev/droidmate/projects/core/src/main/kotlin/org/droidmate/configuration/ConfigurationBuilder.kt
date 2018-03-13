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
import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import com.konradjamrozik.Resource
import com.konradjamrozik.ResourcePath
import com.konradjamrozik.createDirIfNotExists
import com.konradjamrozik.toList
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.apache.commons.lang3.builder.StandardToStringStyle
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
import java.util.*

/**
 * @see IConfigurationBuilder#build(java.lang.String [ ], java.nio.file.FileSystem)
 */

class ConfigurationBuilder : IConfigurationBuilder {
    @Throws(ConfigurationException::class)
    override fun build(args: Array<String>): Configuration = build(args, FileSystems.getDefault())

    @Throws(ConfigurationException::class)
    override fun build(args: Array<String>, fs: FileSystem): Configuration = ConfigurationBuilder.memoizedBuildConfiguration(args.toList(), fs)

    companion object {
        private val log = LoggerFactory.getLogger(ConfigurationBuilder::class.java)

        @JvmStatic
        private fun memoizedBuildConfiguration(args: List<String>, fs: FileSystem): Configuration {
            log.debug("memoizedBuildConfiguration(args, fs)")

            var config = Configuration(args.toTypedArray())

            val jCommander = populateConfigurationWithArgs(args.toTypedArray(), config)

            ifRequestedDisplayHelpAndExit(config, jCommander)
            assert(!config.displayHelp) {
                "DroidMate was instructed to display help. By now, it should have done it and exited, " +
                        "but instead of exiting the code execution reached this assertion."
            }

            config = bindAndValidate(config, fs)

            return config
        }

        @JvmStatic
        private fun populateConfigurationWithArgs(args: Array<String>, config: Configuration): JCommander {
            try {
                val jCommander = JCommander()
                jCommander.addObject(config)
                jCommander.parse(*args)
                return jCommander
            } catch (e: ParameterException) {
                throw ConfigurationException(e)
            }
        }

        @JvmStatic
        private fun ifRequestedDisplayHelpAndExit(config: Configuration, jCommander: JCommander) {
            if (config.displayHelp) {
                log.info("Detected request to display help. Displaying help & terminating.")

                jCommander.usage()

                System.exit(0)
            }
        }

        @JvmStatic
        @Throws(ConfigurationException::class)
        private fun bindAndValidate(config: Configuration, fs: FileSystem): Configuration {
            try {
                setLogbackRootLoggerLoggingLevel(config)
                setupResourcesAndPaths(config, fs)
                validateExplorationSettings(config)
                normalizeAndroidApi(config)

                // This increment is done so each connected device will have its uiautomator-daemon reachable on a separate port.
                config.uiautomatorDaemonTcpPort += config.deviceIndex

            } catch (e: ConfigurationException) {
                throw e
            }

            logConfigurationInEffect(config)

            return config
        }

        @JvmStatic
        private fun normalizeAndroidApi(config: Configuration) {
            if (config.androidApi == "23")
                config.androidApi = Configuration.api23
        }

        @JvmStatic
        private fun validateExplorationSettings(cfg: Configuration) {
            validateExplorationStrategySettings(cfg)

            val apkNames = Files.list(cfg.apksDirPath)
                    .filter { it.toString().endsWith(".apk") }
                    .map { it -> it.fileName.toString() }
                    .toList()

            if (cfg.deployRawApks && arrayListOf("inlined", "monitored").any { apkNames.contains(it) })
                throw ConfigurationException(
                        "DroidMate was instructed to deploy raw apks, while the apks dir contains an apk " +
                                "with 'inlined' or 'monitored' in its name. Please do not mix such apk with raw apks in one dir.\n" +
                                "The searched apks dir path: ${cfg.apksDirPath.toAbsolutePath()}")
        }

        @JvmStatic
        private fun validateExplorationStrategySettings(cfg: Configuration) {
            val settingsCount = widgetClickingStrategySettingsCount(cfg)

            if (settingsCount > 1)
                throw ConfigurationException("Exploration strategy has been configured in too many different ways. Only one of the following expressions can be true:\n" +
                        "alwaysClickFirstWidget: ${cfg.alwaysClickFirstWidget}\n" +
                        "randomSeed >= null: ${cfg.randomSeed >= 0}\n" +
                        "widgetIndexes.isNotEmpty(): ${cfg.widgetIndexes.isNotEmpty()}")

            if (cfg.randomSeed == -1) {
                cfg.randomSeed = Random().nextLong().toInt()
                log.info("Generated random seed: $cfg.randomSeed")
            }
        }

        @JvmStatic
        private fun widgetClickingStrategySettingsCount(cfg: Configuration): Int =
                arrayListOf(cfg.alwaysClickFirstWidget, cfg.widgetIndexes.isNotEmpty()).map { if (it) 1 else 0 }.sum()

        @JvmStatic
        private fun getResourcePath(cfg: Configuration, fs: FileSystem, resourceName: String): Path {
            var dstPath = BuildConstants.dir_name_temp_extracted_resources
            // If not using main device, export again
            if (cfg.deviceSerialNumber.isNotEmpty())
                dstPath += cfg.deviceSerialNumber.replace(":", "-")
            else if (cfg.deviceIndex > 0)
                dstPath += cfg.deviceIndex

            val path = fs.getPath(dstPath, resourceName)

            if (!cfg.replaceExtractedResources && Files.exists(path))
                return path

            return Resource(resourceName).extractTo(fs.getPath(dstPath))
        }

        @JvmStatic
        @Throws(ConfigurationException::class)
        private fun setupResourcesAndPaths(cfg: Configuration, fs: FileSystem) {
            cfg.uiautomator2DaemonApk = getResourcePath(cfg, fs, "uiautomator2-daemon.apk")
            log.info("Using uiautomator2-daemon.apk located at " + cfg.uiautomator2DaemonApk.toAbsolutePath().toString())

            cfg.uiautomator2DaemonTestApk = getResourcePath(cfg, fs, "uiautomator2-daemon-test.apk")
            log.info("Using uiautomator2-daemon-test.apk located at " + cfg.uiautomator2DaemonTestApk.toAbsolutePath().toString())

            cfg.monitorApkApi23 = getResourcePath(cfg, fs, BuildConstants.monitor_api23_apk_name)
            log.info("Using ${BuildConstants.monitor_api23_apk_name} located at " + cfg.monitorApkApi23.toAbsolutePath().toString())

            cfg.apiPoliciesFile = getResourcePath(cfg, fs, BuildConstants.api_policies_file_name)
            log.info("Using ${BuildConstants.api_policies_file_name} located at " + cfg.apiPoliciesFile.toAbsolutePath().toString())

            val portFile = File.createTempFile(BuildConstants.port_file_name, ".tmp")
            portFile.writeText(Integer.toString(cfg.port))
            portFile.deleteOnExit()
            cfg.portFile = portFile.toPath()
            log.info("Using ${BuildConstants.port_file_name} located at " + cfg.portFile.toAbsolutePath().toString())

            cfg.droidmateOutputDirPath = fs.getPath(cfg.droidmateOutputDir).toAbsolutePath()
            cfg.droidmateOutputReportDirPath = cfg.droidmateOutputDirPath.resolve(cfg.reportOutputSubDir).toAbsolutePath()
            cfg.reportInputDirPath = fs.getPath(cfg.reportInputDir).toAbsolutePath()
            cfg.reportOutputDirPath = fs.getPath(cfg.reportOutputDir).toAbsolutePath()
            cfg.apksDirPath = if (cfg.useApkFixturesDir)
                ResourcePath(BuildConstants.apk_fixtures).path.toAbsolutePath()
            else
                fs.getPath(cfg.apksDirName).toAbsolutePath()

            if (cfg.apksDirPath.createDirIfNotExists())
                log.info("Created directory from which DroidMate will read input apks: " + cfg.apksDirPath.toAbsolutePath().toString())

            if (Files.notExists(cfg.droidmateOutputDirPath)) {
                Files.createDirectories(cfg.droidmateOutputDirPath)
                log.info("Created directory to which DroidMate will output: " + cfg.droidmateOutputDirPath.toAbsolutePath().toString())
            }
        }

        @JvmStatic
        @Throws(ConfigurationException::class)
        private fun setLogbackRootLoggerLoggingLevel(config: Configuration) {
            val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
            val explorationLogger = LoggerFactory.getLogger("org.droidmate.exploration") as ch.qos.logback.classic.Logger

            if (config.logLevel.toLowerCase() in arrayListOf("info", "debug", "trace", "warn", "error")) {
                rootLogger.level = Level.toLevel(config.logLevel)
                explorationLogger.level = Level.toLevel(config.logLevel)
            } else
                throw ConfigurationException(String.format(
                        "Unrecognized logging level. Given level: %s. Expected one of levels: info debug trace",
                        config.logLevel))
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
