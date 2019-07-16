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

import org.droidmate.configuration.ConfigProperties
import org.droidmate.device.error.DeviceException
import org.droidmate.device.android_sdk.IAdbWrapper
import org.droidmate.configuration.ConfigProperties.ApiMonitorServer.monitorSocketTimeout
import org.droidmate.configuration.ConfigProperties.Exploration.apiVersion
import org.droidmate.configuration.ConfigProperties.Core.hostIp
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.socketTimeout
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.waitForInteractableTimeout
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.startTimeout
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.ApkExplorationException
import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.logcat.TimeFormattedLogcatMessage
import org.droidmate.deviceInterface.DeviceConstants
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.misc.EnvironmentConstants
import org.droidmate.misc.MonitorConstants
import org.droidmate.misc.Utils
import org.droidmate.deviceInterface.DeviceConstants.logcatLogFileName
import org.droidmate.deviceInterface.DeviceConstants.uia2Daemon_packageName
import org.droidmate.deviceInterface.DeviceConstants.uia2Daemon_testPackageName
import org.droidmate.deviceInterface.communication.*
import org.droidmate.deviceInterface.exploration.DeviceResponse
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF.Companion.StatementCoverage.enableCoverage
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
 * {@link org.droidmate.tools.IAndroidDeviceDeployer#setupAndExecute(String, int, Closure)
 * AndroidDeviceDeployer.setupAndExecute(closure)}, thus guaranteeing invariant of this class:
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
								"ExplorationTrace: ${response.stackTrace.joinToString("\n")} \n" +
								"The exception is given as a cause of this one. If it doesn't have enough information, " +
								"try inspecting the logcat output of the A(V)D. ",
						response)
		}
	}

	init {
		// Port files can only be generated here because they depend on the device index
		val monitorPortFile = File.createTempFile(EnvironmentConstants.monitor_port_file_name, ".tmp")
		monitorPortFile.writeText(Integer.toString(cfg.monitorPort))
		monitorPortFile.deleteOnExit()
		cfg.monitorPortFile = monitorPortFile.toPath().toAbsolutePath()
		log.info("Using ${EnvironmentConstants.monitor_port_file_name} located at ${cfg.monitorPortFile}")

		val coveragePortFile = File.createTempFile(EnvironmentConstants.coverage_port_file_name, ".tmp")
		coveragePortFile.writeText(Integer.toString(cfg.coverageMonitorPort))
		coveragePortFile.deleteOnExit()
		cfg.coveragePortFile = coveragePortFile.toPath().toAbsolutePath()
		log.info("Using ${EnvironmentConstants.coverage_port_file_name} located at ${cfg.coveragePortFile}")
	}

	private val tcpClients: ITcpClients = TcpClients(
			this.adbWrapper,
			this.serialNumber,
			cfg[monitorSocketTimeout],
			cfg[socketTimeout],
			cfg.uiAutomatorPort,
			cfg[startTimeout],
			cfg[waitForInteractableTimeout],
			cfg[hostIp],
			cfg.monitorPort,
			cfg.coverageMonitorPort)

	@Throws(DeviceException::class)
	override suspend fun pushFile(jar: Path) {
		pushFile(jar, "")
	}

	@Throws(DeviceException::class)
	override suspend fun pushFile(jar: Path, targetFileName: String) {
		log.debug("pushFile($jar, $targetFileName)")
		adbWrapper.pushFile(serialNumber, jar, targetFileName)
	}

	override suspend fun pullFile(fileName:String, dstPath: Path, srcPath: String){
		log.debug("pullFile $fileName from $srcPath to $dstPath")
		adbWrapper.pullFileApi23(serialNumber,srcPath+fileName,dstPath, uia2Daemon_packageName)
	}

	override suspend fun removeFile(fileName:String,srcPath: String){
		log.debug("remove device file $fileName from $srcPath")
		adbWrapper.removeFileApi23(serialNumber,srcPath+fileName, uia2Daemon_packageName)
	}

	override suspend fun hasPackageInstalled(packageName: String): Boolean {
		log.debug("hasPackageInstalled($packageName)")
		return adbWrapper.listPackage(serialNumber, packageName).contains(packageName)
	}

	override suspend fun perform(action: ExplorationAction): DeviceResponse {
		log.debug("perform($action)")

		return execute(action)
	}

	@Throws(DeviceException::class)
	private fun execute(action: ExplorationAction): DeviceResponse =
			issueCommand(ExecuteCommand(action))

	@Throws(DeviceException::class)
	private fun issueCommand(deviceCommand: DeviceCommand): DeviceResponse {
		val deviceResponse = this.tcpClients.sendCommandToUiautomatorDaemon(deviceCommand)

		return deviceResponse
	}

	override suspend fun closeConnection() {
		this.stopUiaDaemon(false)
	}

	override suspend fun stopUiaDaemon(uiaDaemonThreadIsNull: Boolean) {

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

	override suspend fun isAvailable(): Boolean {
//    logcat.trace("isAvailable(${this.serialNumber})")
		return this.adbWrapper.getAndroidDevicesDescriptors().any { it.deviceSerialNumber == this.serialNumber }
	}

	override suspend fun reboot() {
//    logcat.trace("reboot(${this.serialNumber})")
		this.adbWrapper.reboot(this.serialNumber)
	}

	override suspend fun uiaDaemonClientThreadIsAlive(): Boolean = this.tcpClients.getUiaDaemonThreadIsAlive()

	override suspend fun setupConnection() {
		log.trace("setupConnection($serialNumber) / this.tcpClients.forwardPorts()")
		this.tcpClients.forwardPorts()
		log.trace("setupConnection($serialNumber) / this.restartUiaDaemon()")
		restartUiaDaemon(true)
		log.trace("setupConnection($serialNumber) / DONE")
	}

	override suspend fun restartUiaDaemon(uiaDaemonThreadIsNull: Boolean) {
		if (this.uiaDaemonIsRunning()) {
			log.trace("stopUiaDaemon() during restart")
			this.stopUiaDaemon(uiaDaemonThreadIsNull)
		}
		log.trace("startUiaDaemon() during restart")
		this.startUiaDaemon()
	}

	override suspend fun startUiaDaemon() {
//		assert(!this.uiaDaemonIsRunning()) { "UIAutomatorDaemon is not running." }  //FIXME sometimes this fails
		try {
			this.clearLogcat()
		}catch(e: org.droidmate.misc.SysCmdExecutorException){
			log.warn("logcat could not be cleared before starting the device-controller")
		}
		this.tcpClients.startUiaDaemon()
	}

	override suspend fun removeLogcatLogFile() {

		log.debug("removeLogcatLogFile()")
		if (cfg[apiVersion] == ConfigurationWrapper.api23)
			this.adbWrapper.removeFileApi23(this.serialNumber, DeviceConstants.deviceLogcatLogDir_api23+logcatLogFileName, uia2Daemon_packageName)
		else
			throw UnexpectedIfElseFallthroughError("configured api version does not match ConfigurationWrapper.api23")
	}

	override suspend fun readLogcatMessages(messageTag: String): List<TimeFormattedLogMessageI> {
		log.debug("readLogcatMessages(tag: $messageTag)")
		val messages = adbWrapper.readMessagesFromLogcat(this.serialNumber, messageTag)
		return messages.map { TimeFormattedLogcatMessage.from(it) }
	}

	override suspend fun readStatements(): List<List<String>> {
		log.debug("readStatements()")

		try {
			val messages = this.tcpClients.getStatements()

			messages.forEach { msg ->
				assert(msg.size == 2) { "Expected 2 messages, received ${msg.size}" }
				assert(msg[0].isNotEmpty()) { "First part of the statement payload was empty" }
				assert(msg[1].isNotEmpty()) { "Second part of the statement payload was empty" }
			}

			return messages
		} catch (e: ApkExplorationException) {
			log.error("Error reading statements from monitor TCP server. Proceeding with exploration ${e.message}", e)
			return emptyList()
		}
	}

	override suspend fun waitForLogcatMessages(messageTag: String, minMessagesCount: Int, waitTimeout: Int, queryDelay: Int): List<TimeFormattedLogMessageI> {
		log.debug("waitForLogcatMessages(tag: $messageTag, minMessagesCount: $minMessagesCount, waitTimeout: $waitTimeout, queryDelay: $queryDelay)")
		val messages = adbWrapper.waitForMessagesOnLogcat(this.serialNumber, messageTag, minMessagesCount, waitTimeout, queryDelay)
		log.debug("waitForLogcatMessages(): obtained messages: ${messages.joinToString(System.lineSeparator())}")
		return messages.map { TimeFormattedLogcatMessage.from(it) }
	}

	override suspend fun readAndClearMonitorTcpMessages(): List<List<String>> {
		log.debug("readAndClearMonitorTcpMessages()")

		try {
			val messages = this.tcpClients.getLogs()

			messages.forEach { msg ->
				assert(msg.size == 3) { "Expected 3 messages, received ${msg.size}" }
				assert(msg[0].isNotEmpty()) { "First part of the statement payload was empty" }
				assert(msg[1].isNotEmpty()) { "Second part of the statement payload was empty" }
				assert(msg[2].isNotEmpty()) { "Third part of the statement payload was empty" }
			}

			return messages
		} catch (e: ApkExplorationException) {
			log.error("Error reading APIs from monitor TCP server. Proceeding with exploration ${e.message}", e)
			return emptyList()
		}
	}

	override suspend fun getCurrentTime(): LocalDateTime {
		log.debug("readAndClearMonitorTcpMessages()")
		val messages = this.tcpClients.getCurrentTime()

		assert(messages.size == 1) { "Expected 1 message, received ${messages.size}" }
		assert(messages[0].size == 3) { "Expected 3 messages, received ${messages.size}" }
		assert(messages[0][0].isNotEmpty()) { "First payload of first message is empty" }

		return LocalDateTime.parse(messages[0][0], DateTimeFormatter.ofPattern(MonitorConstants.monitor_time_formatter_pattern, MonitorConstants.monitor_time_formatter_locale))
	}

	override suspend fun appProcessIsRunning(appPackageName: String): Boolean {
		log.debug("appProcessIsRunning($appPackageName)")
		val ps = this.adbWrapper.ps(this.serialNumber)

		val out = ps.contains(appPackageName)
		if (out)
			log.trace("App process of $appPackageName is running")
		else
			log.trace("App process of $appPackageName is not running")
		return out
	}

	override suspend fun anyMonitorIsReachable(): Boolean =//    logcat.debug("anyMonitorIsReachable()")
			this.tcpClients.anyMonitorIsReachable()

	/**
	 * @throws org.droidmate.misc.SysCmdExecutorException when failed
	 */
	override suspend fun clearLogcat() {
		log.debug("clearLogcat()")
		adbWrapper.clearLogcat(serialNumber)
	}

	override suspend fun installApk(apk: IApk) {
		log.debug("installApk($apk.fileName)")
		adbWrapper.installApk(serialNumber, apk)
	}

	override suspend fun isApkInstalled(apkPackageName: String): Boolean {
		log.debug("Check if $apkPackageName is installed")
		return adbWrapper.isApkInstalled(serialNumber, apkPackageName)
	}

	override suspend fun uninstallApk(apkPackageName: String, ignoreFailure: Boolean) {
		log.debug("uninstallApk($apkPackageName, ignoreFailure: $ignoreFailure)")
		adbWrapper.uninstallApk(serialNumber, apkPackageName, ignoreFailure)
	}

	override suspend fun closeMonitorServers() {
		log.debug("closeMonitorServers()")
		tcpClients.closeMonitorServers()
	}

	override suspend fun clearPackage(apkPackageName: String) {
		log.debug("clearPackage($apkPackageName)")
		adbWrapper.clearPackage(serialNumber, apkPackageName)
	}

	override suspend fun removeJar(jar: Path) {
		log.debug("removeJar($jar)")
		adbWrapper.removeJar(serialNumber, jar)
	}

	override suspend fun installApk(apk: Path) {
		log.debug("installApk($apk.fileName)")
		adbWrapper.installApk(serialNumber, apk)
	}

	override suspend fun appIsRunning(appPackageName: String): Boolean =
			this.appProcessIsRunning(appPackageName) && this.anyMonitorIsReachable()

	override suspend fun reinstallUiAutomatorDaemon() {
		if (cfg[apiVersion] == ConfigurationWrapper.api23) {
			this.uninstallApk(uia2Daemon_testPackageName, true)
			this.uninstallApk(uia2Daemon_packageName, true)

			this.installApk(this.cfg.uiautomator2DaemonApk)
			this.installApk(this.cfg.uiautomator2DaemonTestApk)

		} else
			throw UnexpectedIfElseFallthroughError()
	}

	private suspend fun pushCoveragePort() {
		// Configuration file for statement coverage
		if (cfg[enableCoverage]) {
			this.pushFile(this.cfg.coveragePortFile, EnvironmentConstants.coverage_port_file_name)
		}
	}

	private suspend fun pushApiMonitorFiles() {
		// Configuration Files for API Monitoring
		if (cfg.monitorApk!=null) {
			this.pushFile(cfg.monitorApk!!, EnvironmentConstants.monitor_apk_name)
			this.pushFile(this.cfg.apiPoliciesFile!!, EnvironmentConstants.api_policies_file_name)
			this.pushFile(this.cfg.monitorPortFile, EnvironmentConstants.monitor_port_file_name)
		}
	}

	override suspend fun pushAuxiliaryFiles() {
		pushCoveragePort()

		if(cfg[ConfigProperties.Deploy.installMonitor]) {
			pushApiMonitorFiles()
		}
	}

	override suspend fun reconnectAdb() {
		this.adbWrapper.reconnect(this.serialNumber)
	}

	override suspend fun executeAdbCommand(command: String, successfulOutput: String, commandDescription: String) {
		this.adbWrapper.executeCommand(this.serialNumber, successfulOutput, commandDescription, command)
	}

	override suspend fun uiaDaemonIsRunning(): Boolean {
		if (cfg[apiVersion] != ConfigurationWrapper.api23)
			throw UnexpectedIfElseFallthroughError()

		val packageName = uia2Daemon_packageName

		val processList = this.adbWrapper.executeCommand(this.serialNumber,
				"USER", "Check if process $packageName is running.",
				"shell", "ps"
		)

		return processList.contains(packageName)
	}

	override suspend fun isPackageInstalled(packageName: String): Boolean {
		val uiadPackageList = this.adbWrapper.executeCommand(this.serialNumber,
				"", "Check if package $packageName is installed.",
				"shell", "pm", "list", "packages", packageName)
		val packages = uiadPackageList.trim().replace("package:", "").replace("\r", "|").replace("\n", "|").split("\\|")
		return packages.any { it == packageName }
	}

	override fun toString(): String = "{device $serialNumber}"
}
