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

import com.natpryce.konfig.*
import org.droidmate.misc.BuildConstants
import java.net.URI
import java.nio.file.*
import java.util.*

class ConfigurationWrapper @JvmOverloads constructor(private val cfg: Configuration,
													 private val fileSystem: FileSystem = FileSystems.getDefault()) : Configuration by cfg {

	val randomSeed by lazy {
		if (cfg[ConfigProperties.Selectors.randomSeed] == -1L)
			Random().nextLong()
		else
			cfg[ConfigProperties.Selectors.randomSeed]
	}

	val monitorPort by lazy {
		assert(deviceSerialNumber.isNotEmpty(), {"deviceSerialNumber should not be empty."})
		cfg[ConfigProperties.ApiMonitorServer.basePort] + deviceSerialNumber.hashCode() % 999
	}

	val uiAutomatorPort by lazy {
		assert(deviceSerialNumber.isNotEmpty(), {"deviceSerialNumber should not be empty."})
		cfg[ConfigProperties.UiAutomatorServer.basePort] + deviceSerialNumber.hashCode() % 999
	}

	init {
		///
	}

	// Running DroidMate only requires Exploration.deviceIndex or Exploration.deviceSerialNumber to be set.
	// During execution deviceSerialNumber is needed, therefore the deviceSerialNumber is calculated and
	// set by AndroidDeviceDeployer.
	lateinit var deviceSerialNumber: String

	//region Values set by ConfigurationBuilder
	lateinit var droidmateOutputDirPath: Path

	lateinit var droidmateOutputReportDirPath: Path

	lateinit var reportInputDirPath: Path

	lateinit var coverageReportDirPath: Path

	lateinit var apksDirPath: Path

	lateinit var monitorApkApi23: Path

	lateinit var resourceDir: Path

	val aaptCommand = BuildConstants.aapt_command
	val adbCommand = BuildConstants.adb_command

	/**
	 * Apk with uiautomator-daemon. This is a dummy package required only by instrumentation command (instrumentation target property)
	 * More information about th property in: http://developer.android.com/guide/topics/manifest/instrumentation-element.html
	 */
	lateinit var uiautomator2DaemonApk: Path

	/**
	 * Apk with "real" uiautomator-daemon. This apk will be deployed be on the android (virtual) device
	 * to enable GUI actionTrace execution.
	 */
	lateinit var uiautomator2DaemonTestApk: Path

	/**
	 * File with API policies. This file will be deployed be on the android (virtual) device
	 * to define which APIs will be accessible
	 */
	lateinit var apiPoliciesFile: Path

	/**
	 * File with the port for the monitor connection. This file will be deployed be on the android (virtual) device.
	 */
	lateinit var portFile: Path

	//endregion

	constructor(parsedData: Pair<Configuration, List<String>>, fileSystem: FileSystem = FileSystems.getDefault())
			: this(parsedData.first, fileSystem)

	fun getPath(path: String): Path {
		return fileSystem.getPath(path).toAbsolutePath()
	}

	fun getPath(uri: URI): Path {
		return getPath(uri.path)
	}

	companion object {
		@Throws(ConfigurationException::class)
		@JvmOverloads
		fun getDefault(fileSystem: FileSystem = FileSystems.getDefault()): ConfigurationWrapper =
				ConfigurationBuilder().build(emptyArray(), fileSystem)

		const val api23 = 23
		const val defaultApksDir = "apks"

		/**
		 * Name of the logging directory, containing all the logs.
		 * Unfortunately this cannot be in the LogbackConstants class, because
		 * it would result in accessing this static variable and loading another
		 * static variable 'LOGS_DIR_PATH', which wouldn't be ready yet.
		 */
		const val log_dir_name = "logs"
	}
}

abstract class ConfigProperties {
	object ModelProperties : PropertyGroup() {
		object path : PropertyGroup() {
			val defaultBaseDir by uriType
			val statesSubDir by uriType
			val widgetsSubDir by uriType
			val cleanDirs by booleanType
		}

		object dump : PropertyGroup() {
			val sep by stringType
			val onEachAction by booleanType

			val stateFileExtension by stringType

			val traceFileExtension by stringType
			val traceFilePrefix by stringType
		}

		object imgDump : PropertyGroup() {
			val states by booleanType
			val widgets by booleanType

			object widget : PropertyGroup() {
				val nonInteractable by booleanType
				val interactable by booleanType
				val onlyWhenNoText by booleanType
			}
		}
	}

	object Core : PropertyGroup() {
		val logLevel by stringType  // TODO we could use a nice enumType instead
		val configPath by uriType
		val debugMode by booleanType
	}

	object ApiMonitorServer : PropertyGroup() {
		val monitorSocketTimeout by intType
		val monitorUseLogcat by booleanType
		val monitorUseLegacyStream by booleanType
		val basePort by intType
	}

	object ExecutionMode : PropertyGroup() {
		val inline by booleanType
		val report by booleanType
		val explore by booleanType
		val coverage by booleanType
	}

	object Deploy : PropertyGroup() {
		val installApk by booleanType
		val installAux by booleanType
		val uninstallApk by booleanType
		val uninstallAux by booleanType
		val replaceResources by booleanType
		val shuffleApks by booleanType
		val useApkFixturesDir by booleanType
		val deployRawApks by booleanType
	}

	object DeviceCommunication : PropertyGroup() {
		val checkAppIsRunningRetryAttempts by intType
		val checkAppIsRunningRetryDelay by intType
		val checkDeviceAvailableAfterRebootAttempts by intType
		val checkDeviceAvailableAfterRebootFirstDelay by intType
		val checkDeviceAvailableAfterRebootLaterDelays by intType
		val clearPackageRetryAttempts by intType
		val clearPackageRetryDelay by intType
		val closeANRAttempts by intType
		val closeANRDelay by intType
		val getValidGuiSnapshotRetryAttempts by intType
		val getValidGuiSnapshotRetryDelay by intType
		val stopAppRetryAttempts by intType
		val stopAppSuccessCheckDelay by intType
		val waitForCanRebootDelay by intType
		val waitForDevice by booleanType
	}

	object Exploration : PropertyGroup() {
		val apksDir by uriType
		val apksLimit by intType
		val apkNames by listType(stringType)
		val deviceIndex by intType
		val deviceSerialNumber by stringType
		val runOnNotInlined by booleanType
		val launchActivityDelay by intType
		val launchActivityTimeout by intType
		val apiVersion by intType
	}

	object Output : PropertyGroup() {
		val droidmateOutputDirPath by uriType
		val coverageDir by stringType
		val screenshotDir by stringType
		val reportDir by stringType
	}

	object Strategies : PropertyGroup() {
		val reset by booleanType
		val explore by booleanType
		val terminate by booleanType
		val back by booleanType
		val modelBased by booleanType
		val fitnessProportionate by booleanType
		val allowRuntimeDialog by booleanType
		val denyRuntimeDialog by booleanType
		val playback by booleanType
		val dfs by booleanType
		val rotateUI by booleanType

		object Parameters : PropertyGroup() {
			val uiRotation by intType
		}
	}

	object Selectors : PropertyGroup() {
		val pressBackProbability by doubleType
		val widgetIndexes by listType(intType)
		val playbackModelDir by uriType
		val resetEvery by intType
		val actionLimit by intType
		val timeLimit by intType
		val randomSeed by longType
		val stopOnExhaustion by booleanType
		val dfs by booleanType
	}

	object Report : PropertyGroup() {
		val inputDir by uriType
		val includePlots by booleanType
	}

	object UiAutomatorServer : PropertyGroup() {
		val startTimeout by intType
		val startQueryDelay by intType
		val socketTimeout by intType
		val basePort by intType
	}
}