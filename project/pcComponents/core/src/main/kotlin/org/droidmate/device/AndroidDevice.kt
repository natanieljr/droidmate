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
import org.droidmate.apis.ITimeFormattedLogcatMessage
import org.droidmate.apis.TimeFormattedLogcatMessage
import org.droidmate.configuration.Configuration
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.logging.LogbackUtils
import org.droidmate.misc.BuildConstants
import org.droidmate.misc.MonitorConstants
import org.droidmate.misc.Utils
import org.droidmate.uiautomator_daemon.*
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants.logcatLogFileName
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants.uia2Daemon_packageName
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants.uia2Daemon_testPackageName
import org.droidmate.uiautomator_daemon.guimodel.*
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
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
                                private val cfg: Configuration,
                                private val adbWrapper: IAdbWrapper) : IAndroidDevice {
	companion object {
		private val log = LoggerFactory.getLogger(AndroidDevice::class.java)

		@JvmStatic
		@Throws(DeviceException::class)
		private fun throwDeviceResponseThrowableIfAny(deviceResponse: DeviceResponse) {
			if (deviceResponse.throwable != null)
				throw DeviceException(String.format(
						"Device returned DeviceResponse with non-null throwable, indicating something went horribly wrong on the A(V)D. " +
								"The exception is given as a cause of this one. If it doesn't have enough information, " +
								"try inspecting the logcat output of the A(V)D.",
						deviceResponse.throwable))
		}
	}

	private val tcpClients: ITcpClients = TcpClients(
			this.adbWrapper,
			this.serialNumber,
			cfg.monitorSocketTimeout,
			cfg.uiautomatorDaemonSocketTimeout,
			cfg.uiautomatorDaemonTcpPort,
			cfg.uiautomatorDaemonServerStartTimeout,
			cfg.uiautomatorDaemonServerStartQueryDelay,
			cfg.port,
			cfg.monitorUseLegacyStream)

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

	override fun perform(action: Action): DeviceResponse {
		log.debug("perform($action)")
		assert(action::class in arrayListOf(ClickAction::class,
				CoordinateClickAction::class,
				CoordinateLongClickAction::class,
				LongClickAction::class,
				TextAction::class,
				WaitAction::class,
				SwipeAction::class,
				PressBack::class,
				PressHome::class,
				EnableWifi::class,
				LaunchApp::class,
				SimulationAdbClearPackage::class))

		return when (action) {
			is WaitAction -> wait(action)
			is LaunchApp -> throw DeviceException("call .launchMainActivity() directly instead")
			is ClickAction -> execute(action)
			is CoordinateClickAction -> execute(action)
			is LongClickAction -> execute(action)
			is CoordinateLongClickAction -> execute(action)
			is TextAction -> execute(action)
			is SwipeAction -> execute(action)
			is PressBack -> execute(action)
			is PressHome -> execute(action)
			is EnableWifi -> execute(action)
			is FetchGUI -> execute(action)
			is SimulationAdbClearPackage -> throw DeviceException("call .clearPackage() directly instead")
		}
	}

	@Throws(DeviceException::class)
	private fun wait(action: WaitAction): DeviceResponse {
		log.debug("perform wait action")
		return issueCommand(ExecuteCommand(action))
	}

	@Throws(DeviceException::class)
	private fun execute(action: Action): DeviceResponse =
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

		this.issueCommand(StopDaemonCommand())

		if (uiaDaemonThreadIsNull)
			assert(this.tcpClients.getUiaDaemonThreadIsNull())
		else
			this.tcpClients.waitForUiaDaemonToClose()

		assert(Utils.retryOnFalse({ !this.uiaDaemonIsRunning() }, 5, 1000))
		assert(!this.uiaDaemonIsRunning())
		log.trace("DONE stopUiaDaemon()")
	}

	override fun isAvailable(): Boolean {
//    log.trace("isAvailable(${this.serialNumber})")
		return this.adbWrapper.getAndroidDevicesDescriptors().any { it.deviceSerialNumber == this.serialNumber }
	}

	override fun reboot() {
//    log.trace("reboot(${this.serialNumber})")
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
		assert(!this.uiaDaemonIsRunning())
		this.clearLogcat()
		this.tcpClients.startUiaDaemon()
	}

	override fun removeLogcatLogFile() {

		log.debug("removeLogcatLogFile()")
		if (cfg.androidApi == Configuration.api23)
			this.adbWrapper.removeFile_api23(this.serialNumber, logcatLogFileName, uia2Daemon_packageName)
		else throw UnexpectedIfElseFallthroughError()
	}

	override fun pullLogcatLogFile() {
		log.debug("pullLogcatLogFile()")
		if (cfg.androidApi == Configuration.api23)
			this.adbWrapper.pullFile_api23(this.serialNumber, logcatLogFileName, LogbackUtils.getLogFilePath("logcat.txt"), uia2Daemon_packageName)
		else throw UnexpectedIfElseFallthroughError()
	}

	override fun readLogcatMessages(messageTag: String): List<ITimeFormattedLogcatMessage> {
		log.debug("readLogcatMessages(tag: $messageTag)")
		val messages = adbWrapper.readMessagesFromLogcat(this.serialNumber, messageTag)
		return messages.map { TimeFormattedLogcatMessage.from(it) }
	}

	override fun waitForLogcatMessages(messageTag: String, minMessagesCount: Int, waitTimeout: Int, queryDelay: Int): List<ITimeFormattedLogcatMessage> {
		log.debug("waitForLogcatMessages(tag: $messageTag, minMessagesCount: $minMessagesCount, waitTimeout: $waitTimeout, queryDelay: $queryDelay)")
		val messages = adbWrapper.waitForMessagesOnLogcat(this.serialNumber, messageTag, minMessagesCount, waitTimeout, queryDelay)
		log.debug("waitForLogcatMessages(): obtained messages: ${messages.joinToString(System.lineSeparator())}")
		return messages.map { TimeFormattedLogcatMessage.from(it) }
	}

	override fun readAndClearMonitorTcpMessages(): List<List<String>> {
		log.debug("readAndClearMonitorTcpMessages()")

		val messages = this.tcpClients.getLogs()

		messages.forEach { msg ->
			assert(msg.size == 3)
			assert(msg[0].isNotEmpty())
			assert(msg[1].isNotEmpty())
			assert(msg[2].isNotEmpty())
		}

		return messages
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

	override fun anyMonitorIsReachable(): Boolean =//    log.debug("anyMonitorIsReachable()")
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

	override fun launchMainActivity(launchableActivityComponentName: String) {

		log.debug("launchMainActivity($launchableActivityComponentName)")
		adbWrapper.launchMainActivity(serialNumber, launchableActivityComponentName)
		log.info("Sleeping after launching $launchableActivityComponentName for ${cfg.launchActivityDelay} ms")
		sleep(cfg.launchActivityDelay.toLong())
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

	override fun clickAppIcon(iconLabel: String) {

		log.debug("perform(newLaunchAppDeviceAction(iconLabel:$iconLabel))")
		this.perform(LaunchApp(iconLabel))
		log.info("Sleeping after clicking app icon labeled '$iconLabel' for ${cfg.launchActivityDelay} ms")
		sleep(cfg.launchActivityDelay.toLong())
	}

	override fun reinstallUiautomatorDaemon() {
		if (cfg.androidApi == Configuration.api23) {
			if (this.isApkInstalled(uia2Daemon_testPackageName)) {
				this.uninstallApk(uia2Daemon_testPackageName, true)
			}
			if (this.isApkInstalled(uia2Daemon_packageName)) {
				this.uninstallApk(uia2Daemon_packageName, true)
			}

			this.installApk(this.cfg.uiautomator2DaemonApk)
			this.installApk(this.cfg.uiautomator2DaemonTestApk)

		} else throw UnexpectedIfElseFallthroughError()
	}

	override fun pushMonitorJar() {
		if (cfg.androidApi == Configuration.api23) {
			this.pushFile(this.cfg.monitorApkApi23, BuildConstants.monitor_on_avd_apk_name)

		} else throw UnexpectedIfElseFallthroughError()

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
		if (cfg.androidApi != Configuration.api23)
			throw UnexpectedIfElseFallthroughError()

		val packageName = uia2Daemon_packageName

		val processList = this.adbWrapper.executeCommand(this.serialNumber,
				"USER", "Check if process $packageName is running.",
				"shell ps"
		)

		return processList.contains(packageName)

	}

	override fun isPackageInstalled(packageName: String): Boolean {
		val uiadPackageList = this.adbWrapper.executeCommand(this.serialNumber,
				"", "Check if package $packageName is installed.",
				"shell pm list packages $packageName")
		val packages = uiadPackageList.trim().replace("package:", "").replace("\r", "|").replace("\n", "|").split("\\|")
		return packages.any { it == packageName }
	}

	override fun toString(): String = "{device $serialNumber}"
}
