@file:Suppress("DEPRECATION")
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

package org.droidmate.device

import org.droidmate.device.android_sdk.DeviceException
import org.droidmate.device.android_sdk.IAdbWrapper
import org.droidmate.device.android_sdk.IApk
import org.droidmate.configuration.ConfigProperties.ApiMonitorServer.monitorSocketTimeout
import org.droidmate.configuration.ConfigProperties.Exploration.apiVersion
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.socketTimeout
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.waitForInteractableTimeout
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.startTimeout
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.ApkExplorationException
import org.droidmate.device.logcat.TimeFormattedLogcatMessage
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.logging.LogbackUtils
import org.droidmate.misc.BuildConstants
import org.droidmate.misc.MonitorConstants
import org.droidmate.misc.Utils
import org.droidmate.deviceInterface.UiautomatorDaemonConstants.logcatLogFileName
import org.droidmate.deviceInterface.UiautomatorDaemonConstants.uia2Daemon_packageName
import org.droidmate.deviceInterface.UiautomatorDaemonConstants.uia2Daemon_testPackageName
import org.droidmate.deviceInterface.communication.*
import org.droidmate.deviceInterface.exploration.DeviceResponse
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.SimulationAdbClearPackage
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * <p>
 * <i> --- This doc was last reviewed on 21 Dec 2013.</i>
 * </p><p>
 * Provides programmatic access to Android (Virtual) Device. The instance of this class should be available only as a parameter
 * in {@code closure} passed to
 * {@link org.droidmate.tools.IAndroidDeviceDeployer#withSetupDevice(String, int, Closure)
 * AndroidDeviceDeployer.withSetupDevice(closure)}, thus guaranteeing invariant of this class:
 *
 * </p><p>
 * CLASS INVARIANT: the A(V)D accessed by a instance of this class is setup and available for duration of the instance existence.
 *
 * </p>
 */
class AndroidDevice constructor(private val serialNumber: String,
                                private val cfg: ConfigurationWrapper,
                                private val adbWrapper: IAdbWrapper) : IAndroidDevice {
	companion object {
		private val log by lazy { LoggerFactory.getLogger(AndroidDevice::class.java) }

		@JvmStatic
		@Throws(DeviceException::class)
		private fun throwDeviceResponseThrowableIfAny(deviceResponse: DeviceResponse) {
			val response = deviceResponse.throwable
			if (response != null)
				throw DeviceException(
						"Device returned DeviceResponse with non-null throwable, indicating something went horribly wrong on the A(V)D.\n" +
								"Exception: $response \n" +
								"Cause: ${response.cause ?: ""}" +
								"Trace: ${response.stackTrace.joinToString("\n")} \n" +
								"The exception is given as a cause of this one. If it doesn't have enough information, " +
								"try inspecting the logcat output of the A(V)D. ",
						response)
		}
	}

	init {
		// Port file can only be generated here because it depends on the device index
		val portFile = File.createTempFile(BuildConstants.port_file_name, ".tmp")
		portFile.writeText(Integer.toString(cfg.monitorPort))
		portFile.deleteOnExit()
		cfg.portFile = portFile.toPath().toAbsolutePath()
		log.info("Using ${BuildConstants.port_file_name} located at ${cfg.portFile}")
	}

	private val tcpClients: ITcpClients = TcpClients(
			this.adbWrapper,
			this.serialNumber,
			cfg[monitorSocketTimeout],
			cfg[socketTimeout],
			cfg.uiAutomatorPort,
			cfg[startTimeout],
			cfg[waitForInteractableTimeout],
			cfg.monitorPort)

	@Throws(DeviceException::class)
	override fun pushFile(jar: Path) {
		pushFile(jar, "")
	}

	@Throws(DeviceException::class)
	override fun pushFile(jar: Path, targetFileName: String) {
		log.debug("pushFile($jar, $targetFileName)")
		adbWrapper.pushFile(serialNumber, jar, targetFileName)
	}

	override fun hasPackageInstalled(packageName: String): Boolean {
		log.debug("hasPackageInstalled($packageName)")
		return adbWrapper.listPackage(serialNumber, packageName).contains(packageName)
	}

	override fun perform(action: ExplorationAction): DeviceResponse {
		log.debug("perform($action)")

		return when (action) {
			is SimulationAdbClearPackage -> throw DeviceException("call .clearPackage() directly instead")
			else -> execute(action)
		}
	}

	@Throws(DeviceException::class)
	private fun execute(action: ExplorationAction): DeviceResponse =
			issueCommand(ExecuteCommand(action))

	@Throws(DeviceException::class)
	private fun issueCommand(deviceCommand: DeviceCommand): DeviceResponse {
		val deviceResponse = this.tcpClients.sendCommandToUiautomatorDaemon(deviceCommand)

		throwDeviceResponseThrowableIfAny(deviceResponse)
		assert(deviceResponse.throwable == null)
		return deviceResponse
	}

	override fun closeConnection() {
		this.stopUiaDaemon(false)
	}

	override fun stopUiaDaemon(uiaDaemonThreadIsNull: Boolean) {

		log.trace("stopUiaDaemon(uiaDaemonThreadIsNull:$uiaDaemonThreadIsNull)")

		this.issueCommand(StopDaemonCommand)

		if (uiaDaemonThreadIsNull)
			assert(this.tcpClients.getUiaDaemonThreadIsNull())
		else
			this.tcpClients.waitForUiaDaemonToClose()

		assert(Utils.retryOnFalse({ !this.uiaDaemonIsRunning() }, 5, 1000))
		assert(!this.uiaDaemonIsRunning()) { "UIAutomatorDaemon is still running." }
		log.trace("DONE stopUiaDaemon()")
	}

	override fun isAvailable(): Boolean {
//    logcat.trace("isAvailable(${this.serialNumber})")
		return this.adbWrapper.getAndroidDevicesDescriptors().any { it.deviceSerialNumber == this.serialNumber }
	}

	override fun reboot() {
//    logcat.trace("reboot(${this.serialNumber})")
		this.adbWrapper.reboot(this.serialNumber)
	}

	override fun uiaDaemonClientThreadIsAlive(): Boolean = this.tcpClients.getUiaDaemonThreadIsAlive()

	override fun setupConnection() {
		log.trace("setupConnection($serialNumber) / this.tcpClients.forwardPorts()")
		this.tcpClients.forwardPorts()
		log.trace("setupConnection($serialNumber) / this.restartUiaDaemon()")
		restartUiaDaemon(true)
		log.trace("setupConnection($serialNumber) / DONE")
	}

	override fun restartUiaDaemon(uiaDaemonThreadIsNull: Boolean) {
		if (this.uiaDaemonIsRunning()) {
			log.trace("stopUiaDaemon() during restart")
			this.stopUiaDaemon(uiaDaemonThreadIsNull)
		}
		log.trace("startUiaDaemon() during restart")
		this.startUiaDaemon()
	}

	override fun startUiaDaemon() {
		assert(!this.uiaDaemonIsRunning()) { "UIAutomatorDaemon is not running." }
		this.clearLogcat()
		this.tcpClients.startUiaDaemon()
	}

	override fun removeLogcatLogFile() {

		log.debug("removeLogcatLogFile()")
		if (cfg[apiVersion] == ConfigurationWrapper.api23)
			this.adbWrapper.removeFileApi23(this.serialNumber, logcatLogFileName, uia2Daemon_packageName)
		else
			throw UnexpectedIfElseFallthroughError()
	}

	override fun pullLogcatLogFile() {
		log.debug("pullLogcatLogFile()")
		if (cfg[apiVersion] == ConfigurationWrapper.api23)
			this.adbWrapper.pullFileApi23(this.serialNumber, logcatLogFileName, cfg.getPath(LogbackUtils.getLogFilePath("logcat.txt")), uia2Daemon_packageName)
		else
			throw UnexpectedIfElseFallthroughError()
	}

	override fun readLogcatMessages(messageTag: String): List<TimeFormattedLogMessageI> {
		log.debug("readLogcatMessages(tag: $messageTag)")
		val messages = adbWrapper.readMessagesFromLogcat(this.serialNumber, messageTag)
		return messages.map { TimeFormattedLogcatMessage.from(it) }
	}

	override fun waitForLogcatMessages(messageTag: String, minMessagesCount: Int, waitTimeout: Int, queryDelay: Int): List<TimeFormattedLogMessageI> {
		log.debug("waitForLogcatMessages(tag: $messageTag, minMessagesCount: $minMessagesCount, waitTimeout: $waitTimeout, queryDelay: $queryDelay)")
		val messages = adbWrapper.waitForMessagesOnLogcat(this.serialNumber, messageTag, minMessagesCount, waitTimeout, queryDelay)
		log.debug("waitForLogcatMessages(): obtained messages: ${messages.joinToString(System.lineSeparator())}")
		return messages.map { TimeFormattedLogcatMessage.from(it) }
	}

	override fun readAndClearMonitorTcpMessages(): List<List<String>> {
		log.debug("readAndClearMonitorTcpMessages()")

		try {
			val messages = this.tcpClients.getLogs()

			messages.forEach { msg ->
				assert(msg.size == 3)
				assert(msg[0].isNotEmpty())
				assert(msg[1].isNotEmpty())
				assert(msg[2].isNotEmpty())
			}

			return messages
		}
		catch(e: ApkExplorationException){
			log.error("Error reading APIs from monitor TCP server. Proceeding with exploration ${e.message}")
			log.error("Trace: ${e.stackTrace}")

			return emptyList()
		}
	}

	override fun getCurrentTime(): LocalDateTime {
		val messages = this.tcpClients.getCurrentTime()

		assert(messages.size == 1)
		assert(messages[0].size == 3)
		assert(messages[0][0].isNotEmpty())
		//assert(messages[0][1].isNotEmpty())
		//assert(messages[0][2].isNotEmpty())

		return LocalDateTime.parse(messages[0][0], DateTimeFormatter.ofPattern(MonitorConstants.monitor_time_formatter_pattern, MonitorConstants.monitor_time_formatter_locale))

	}

	override fun appProcessIsRunning(appPackageName: String): Boolean {
		log.debug("appProcessIsRunning($appPackageName)")
		val ps = this.adbWrapper.ps(this.serialNumber)

		val out = ps.contains(appPackageName)
		if (out)
			log.trace("App process of $appPackageName is running")
		else
			log.trace("App process of $appPackageName is not running")
		return out
	}

	override fun anyMonitorIsReachable(): Boolean =//    logcat.debug("anyMonitorIsReachable()")
			this.tcpClients.anyMonitorIsReachable()

	override fun clearLogcat() {
		log.debug("clearLogcat()")
		adbWrapper.clearLogcat(serialNumber)
	}

	override fun installApk(apk: IApk) {
		log.debug("installApk($apk.fileName)")
		adbWrapper.installApk(serialNumber, apk)
	}

	override fun isApkInstalled(apkPackageName: String): Boolean {
		log.debug("Check if $apkPackageName is installed")
		return adbWrapper.isApkInstalled(serialNumber, apkPackageName)
	}

	override fun uninstallApk(apkPackageName: String, ignoreFailure: Boolean) {
		log.debug("uninstallApk($apkPackageName, ignoreFailure: $ignoreFailure)")
		adbWrapper.uninstallApk(serialNumber, apkPackageName, ignoreFailure)
	}

	override fun closeMonitorServers() {
		log.debug("closeMonitorServers()")
		tcpClients.closeMonitorServers()
	}

	override fun clearPackage(apkPackageName: String) {
		log.debug("clearPackage($apkPackageName)")
		adbWrapper.clearPackage(serialNumber, apkPackageName)
	}

	override fun removeJar(jar: Path) {
		log.debug("removeJar($jar)")
		adbWrapper.removeJar(serialNumber, jar)
	}

	override fun installApk(apk: Path) {
		log.debug("installApk($apk.fileName)")
		adbWrapper.installApk(serialNumber, apk)
	}

	override fun appIsRunning(appPackageName: String): Boolean =
			this.appProcessIsRunning(appPackageName) && this.anyMonitorIsReachable()

	override fun reinstallUiAutomatorDaemon() {
		if (cfg[apiVersion] == ConfigurationWrapper.api23) {
			this.uninstallApk(uia2Daemon_testPackageName, true)
			this.uninstallApk(uia2Daemon_packageName, true)

			this.installApk(this.cfg.uiautomator2DaemonApk)
			this.installApk(this.cfg.uiautomator2DaemonTestApk)

		} else
			throw UnexpectedIfElseFallthroughError()
	}

	override fun pushMonitorJar() {
		if (cfg[apiVersion] == ConfigurationWrapper.api23) {
			this.pushFile(this.cfg.monitorApkApi23, BuildConstants.monitor_on_avd_apk_name)

		} else
			throw UnexpectedIfElseFallthroughError()

		this.pushFile(this.cfg.apiPoliciesFile, BuildConstants.api_policies_file_name)
		this.pushFile(this.cfg.portFile, BuildConstants.port_file_name)
	}

	override fun reconnectAdb() {
		this.adbWrapper.reconnect(this.serialNumber)
	}

	override fun executeAdbCommand(command: String, successfulOutput: String, commandDescription: String) {
		this.adbWrapper.executeCommand(this.serialNumber, successfulOutput, commandDescription, command)
	}

	override fun uiaDaemonIsRunning(): Boolean {
		if (cfg[apiVersion] != ConfigurationWrapper.api23)
			throw UnexpectedIfElseFallthroughError()

		val packageName = uia2Daemon_packageName

		val processList = this.adbWrapper.executeCommand(this.serialNumber,
				"USER", "Check if process $packageName is running.",
				"shell", "ps"
		)

		return processList.contains(packageName)
	}

	override fun isPackageInstalled(packageName: String): Boolean {
		val uiadPackageList = this.adbWrapper.executeCommand(this.serialNumber,
				"", "Check if package $packageName is installed.",
				"shell", "pm", "list", "packages", packageName)
		val packages = uiadPackageList.trim().replace("package:", "").replace("\r", "|").replace("\n", "|").split("\\|")
		return packages.any { it == packageName }
	}

	override fun toString(): String = "{device $serialNumber}"
}
