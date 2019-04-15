package org.droidmate.configuration

import com.natpryce.konfig.*

abstract class ConfigProperties {

	object Core : PropertyGroup() {
		val logLevel by stringType  // TODO we could use a nice enumType instead
		val configPath by uriType
	}

	object ApiMonitorServer : PropertyGroup() {
		val monitorSocketTimeout by intType
		val monitorUseLogcat by booleanType
		val basePort by intType
	}

	object TcpClient : PropertyGroup() {
		val serverAddress by stringType
	}

	object ExecutionMode : PropertyGroup() {
		val inline by booleanType
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
		val deployRawApks by booleanType
	}

	object DeviceCommunication : PropertyGroup() {
		val adbHost by stringType
		val checkAppIsRunningRetryAttempts by intType
		val checkAppIsRunningRetryDelay by intType
		val checkDeviceAvailableAfterRebootAttempts by intType
		val checkDeviceAvailableAfterRebootFirstDelay by intType
		val checkDeviceAvailableAfterRebootLaterDelays by intType
		val stopAppRetryAttempts by intType
		val stopAppSuccessCheckDelay by intType
		val deviceOperationAttempts by intType
		val deviceOperationDelay by intType
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
		val launchActivityDelay by longType
		val launchActivityTimeout by intType
		val apiVersion by intType
		val widgetActionDelay by longType
	}

	object Output : PropertyGroup() {
		val outputDir by uriType
		val screenshotDir by stringType
		val reportDir by stringType
	}

	object Strategies : PropertyGroup() {
		val reset by booleanType
		val explore by booleanType
		val terminate by booleanType
		val back by booleanType
		val allowRuntimeDialog by booleanType
		val denyRuntimeDialog by booleanType
		val playback by booleanType
		val dfs by booleanType
		val rotateUI by booleanType
		val minimizeMaximize by booleanType

		object Parameters : PropertyGroup() {
			val uiRotation by intType
			val randomScroll by booleanType
			val biasedRandom by booleanType
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
		val waitForIdleTimeout by intType
		val waitForInteractableTimeout by intType
		val enablePrintOuts by booleanType
		val delayedImgFetch by booleanType
		val imgQuality by intType
		val startTimeout by intType
		val socketTimeout by intType
		val basePort by intType
	}
}