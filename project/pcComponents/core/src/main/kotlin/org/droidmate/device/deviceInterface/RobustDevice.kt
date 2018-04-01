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
package org.droidmate.device.deviceInterface

import org.droidmate.device.android_sdk.AdbWrapperException
import org.droidmate.device.android_sdk.DeviceException
import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.android_sdk.NoAndroidDevicesAvailableException
import org.droidmate.apis.IApiLogcatMessage
import org.droidmate.apis.ITimeFormattedLogcatMessage
import org.droidmate.configuration.Configuration
import org.droidmate.device.AllDeviceAttemptsExhaustedException
import org.droidmate.device.IAndroidDevice
import org.droidmate.device.TcpServerUnreachableException
import org.droidmate.logging.Markers
import org.droidmate.misc.Utils
import org.droidmate.uiautomator_daemon.DeviceResponse
import org.droidmate.uiautomator_daemon.guimodel.Action
import org.droidmate.uiautomator_daemon.guimodel.ClickAction
import org.droidmate.uiautomator_daemon.guimodel.FetchGUI
import org.droidmate.uiautomator_daemon.guimodel.PressHome
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.nio.file.Path
import java.time.LocalDateTime

class RobustDevice : IRobustDevice {
	companion object {
		private val log = LoggerFactory.getLogger(RobustDevice::class.java)
	}

	private val ensureHomeScreenIsDisplayedAttempts = 3

	private val device: IAndroidDevice
	private val cfg: Configuration

	private val messagesReader: IDeviceMessagesReader

	private val clearPackageRetryAttempts: Int
	private val clearPackageRetryDelay: Int

	private val getValidGuiSnapshotRetryAttempts: Int
	private val getValidGuiSnapshotRetryDelay: Int

	private val checkAppIsRunningRetryAttempts: Int
	private val checkAppIsRunningRetryDelay: Int

	private val stopAppRetryAttempts: Int
	private val stopAppSuccessCheckDelay: Int

	private val closeANRAttempts: Int
	private val closeANRDelay: Int

	private val checkDeviceAvailableAfterRebootAttempts: Int
	private val checkDeviceAvailableAfterRebootFirstDelay: Int
	private val checkDeviceAvailableAfterRebootLaterDelays: Int

	private val waitForCanRebootDelay: Int

	constructor(device: IAndroidDevice, cfg: Configuration) : this(device,
			cfg,
			cfg.clearPackageRetryAttempts,
			cfg.clearPackageRetryDelay,
			cfg.getValidGuiSnapshotRetryAttempts,
			cfg.getValidGuiSnapshotRetryDelay,
			cfg.checkAppIsRunningRetryAttempts,
			cfg.checkAppIsRunningRetryDelay,
			cfg.stopAppRetryAttempts,
			cfg.stopAppSuccessCheckDelay,
			cfg.closeANRAttempts,
			cfg.closeANRDelay,
			cfg.checkDeviceAvailableAfterRebootAttempts,
			cfg.checkDeviceAvailableAfterRebootFirstDelay,
			cfg.checkDeviceAvailableAfterRebootLaterDelays,
			cfg.waitForCanRebootDelay,
			cfg.monitorUseLogcat)

	constructor(device: IAndroidDevice,
	            cfg: Configuration,
	            clearPackageRetryAttempts: Int,
	            clearPackageRetryDelay: Int,
	            getValidGuiSnapshotRetryAttempts: Int,
	            getValidGuiSnapshotRetryDelay: Int,
	            checkAppIsRunningRetryAttempts: Int,
	            checkAppIsRunningRetryDelay: Int,
	            stopAppRetryAttempts: Int,
	            stopAppSuccessCheckDelay: Int,
	            closeANRAttempts: Int,
	            closeANRDelay: Int,
	            checkDeviceAvailableAfterRebootAttempts: Int,
	            checkDeviceAvailableAfterRebootFirstDelay: Int,
	            checkDeviceAvailableAfterRebootLaterDelays: Int,
	            waitForCanRebootDelay: Int,
	            monitorUseLogcat: Boolean) {
		this.device = device
		this.cfg = cfg
		this.messagesReader = DeviceMessagesReader(device, monitorUseLogcat)

		this.clearPackageRetryAttempts = clearPackageRetryAttempts
		this.clearPackageRetryDelay = clearPackageRetryDelay

		this.getValidGuiSnapshotRetryAttempts = getValidGuiSnapshotRetryAttempts
		this.getValidGuiSnapshotRetryDelay = getValidGuiSnapshotRetryDelay

		this.checkAppIsRunningRetryAttempts = checkAppIsRunningRetryAttempts
		this.checkAppIsRunningRetryDelay = checkAppIsRunningRetryDelay

		this.stopAppRetryAttempts = stopAppRetryAttempts
		this.stopAppSuccessCheckDelay = stopAppSuccessCheckDelay

		this.closeANRAttempts = closeANRAttempts
		this.closeANRDelay = closeANRDelay

		this.checkDeviceAvailableAfterRebootAttempts = checkDeviceAvailableAfterRebootAttempts
		this.checkDeviceAvailableAfterRebootFirstDelay = checkDeviceAvailableAfterRebootFirstDelay
		this.checkDeviceAvailableAfterRebootLaterDelays = checkDeviceAvailableAfterRebootLaterDelays

		this.waitForCanRebootDelay = waitForCanRebootDelay

		assert(clearPackageRetryAttempts >= 1)
		assert(checkAppIsRunningRetryAttempts >= 1)
		assert(stopAppRetryAttempts >= 1)
		assert(closeANRAttempts >= 1)
		assert(checkDeviceAvailableAfterRebootAttempts >= 1)

		assert(clearPackageRetryDelay >= 0)
		assert(checkAppIsRunningRetryDelay >= 0)
		assert(stopAppSuccessCheckDelay >= 0)
		assert(closeANRDelay >= 0)
		assert(checkDeviceAvailableAfterRebootFirstDelay >= 0)
		assert(checkDeviceAvailableAfterRebootLaterDelays >= 0)
		assert(waitForCanRebootDelay >= 0)
	}

	override fun uninstallApk(apkPackageName: String, ignoreFailure: Boolean) {
		if (ignoreFailure)
			device.uninstallApk(apkPackageName, ignoreFailure)
		else {
			try {
				device.uninstallApk(apkPackageName, ignoreFailure)
			} catch (e: DeviceException) {
				val appIsInstalled: Boolean
				try {
					appIsInstalled = device.hasPackageInstalled(apkPackageName)
				} catch (e2: DeviceException) {
					throw DeviceException("Uninstalling of $apkPackageName failed with exception E1: '$e'. " +
							"Tried to check if the app that was to be uninstalled is still installed, but that also resulted in exception, E2. " +
							"Discarding E1 and throwing an exception having as a cause E2", e2)
				}

				if (appIsInstalled)
					throw DeviceException("Uninstalling of $apkPackageName threw an exception (given as cause of this exception) and the app is indeed still installed.", e)
				else {
					log.debug("Uninstalling of $apkPackageName threw na exception, but the app is no longer installed. Note: this situation has proven to make the uiautomator be unable to dump window hierarchy. Discarding the exception '$e', resetting connection to the device and continuing.")
					// Doing .rebootAndRestoreConnection() just hangs the emulator: http://stackoverflow.com/questions/9241667/how-to-reboot-emulator-to-test-action-boot-completed
					this.closeConnection()
					this.setupConnection()
				}
			}
		}
	}

	override fun setupConnection() {
		rebootIfNecessary("device.setupConnection()", true) { this.device.setupConnection() }
	}

	override fun clearPackage(apkPackageName: String) {
		// Clearing package has to happen more than once, because sometimes after cleaning suddenly the ActivityManager restarts
		// one of the activities of the app.
		Utils.retryOnFalse({

			Utils.retryOnException({ device.clearPackage(apkPackageName) },
					{},
					DeviceException::class,
					this.clearPackageRetryAttempts,
					this.clearPackageRetryDelay,
					"clearPackage")

			// Sleep here to give the device some time to stop all the processes belonging to the cleared package before checking
			// if indeed all of them have been stopped.
			sleep(this.stopAppSuccessCheckDelay.toLong())

			!this.getAppIsRunningRebootingIfNecessary(apkPackageName)

		},
				this.stopAppRetryAttempts,
				/* Retry delay. Zero, because after seeing the app didn't stop, we immediately clear package again. */
				0)
	}

	override fun ensureHomeScreenIsDisplayed(): DeviceResponse {
		var guiSnapshot = this.getExplorableGuiSnapshot()
		if (guiSnapshot.isHomeScreen)
			return guiSnapshot

		Utils.retryOnFalse({
			if (!guiSnapshot.isHomeScreen) {
				guiSnapshot = when {
					guiSnapshot.isSelectAHomeAppDialogBox -> closeSelectAHomeAppDialogBox(guiSnapshot)
					guiSnapshot.isUseLauncherAsHomeDialogBox -> closeUseLauncherAsHomeDialogBox(guiSnapshot)
					else -> {
						device.perform(PressHome())
					}
				}
			}

			guiSnapshot.isHomeScreen
		},
				ensureHomeScreenIsDisplayedAttempts, /* delay */ 0)

		if (!guiSnapshot.isHomeScreen) {
			throw DeviceException("Failed to ensure home screen is displayed. " +
					"Pressing 'home' button didn't help. Instead, ended with GUI state of: $guiSnapshot.\n" +
					"Full window hierarchy dump:\n" +
					guiSnapshot.windowHierarchyDump)
		}

		return guiSnapshot
	}

	private fun closeSelectAHomeAppDialogBox(snapshot: DeviceResponse): DeviceResponse {
		val launcherWidget = snapshot.widgets.single { it.text == "Launcher" }
		device.perform(ClickAction(launcherWidget.xpath, launcherWidget.resourceId))

		var guiSnapshot = this.getExplorableGuiSnapshot()
		if (guiSnapshot.isSelectAHomeAppDialogBox) {
			val justOnceWidget = guiSnapshot.widgets.single { it.text == "Just once" }
			device.perform(ClickAction(justOnceWidget.xpath, justOnceWidget.resourceId))
			guiSnapshot = this.getExplorableGuiSnapshot()
		}
		assert(!guiSnapshot.isSelectAHomeAppDialogBox)

		return guiSnapshot
	}

	private fun closeUseLauncherAsHomeDialogBox(snapshot: DeviceResponse): DeviceResponse {
		val justOnceWidget = snapshot.widgets.single { it.text == "Just once" }
		device.perform(ClickAction(justOnceWidget.xpath, justOnceWidget.resourceId))

		val guiSnapshot = this.getExplorableGuiSnapshot()
		assert(!guiSnapshot.isUseLauncherAsHomeDialogBox)
		return guiSnapshot
	}

	override fun perform(action: Action): DeviceResponse {
		return Utils.retryOnException(
				{ this.device.perform(action) },
				{ this.restartUiaDaemon(false) },
				DeviceException::class,
				getValidGuiSnapshotRetryAttempts,
				0,
				"device.perform(action:$action)"
		)
	}

	override fun appIsNotRunning(apk: IApk): Boolean {
		return Utils.retryOnFalse({ !this.getAppIsRunningRebootingIfNecessary(apk.packageName) },
				checkAppIsRunningRetryAttempts,
				checkAppIsRunningRetryDelay)
	}

	@Throws(DeviceException::class)
	private fun getAppIsRunningRebootingIfNecessary(packageName: String): Boolean = rebootIfNecessary("device.appIsRunning(packageName:$packageName)", true) { this.device.appIsRunning(packageName) }

	override fun launchApp(apk: IApk): DeviceResponse {
		log.debug("launchApp(${apk.packageName})")

		if (apk.launchableActivityName.isNotEmpty())
			this.launchMainActivity(apk.launchableActivityComponentName)
		else {
			assert(apk.applicationLabel.isNotEmpty())
			this.clickAppIcon(apk.applicationLabel)
		}

		return this.perform(FetchGUI())
	}

	override fun clickAppIcon(iconLabel: String) {
		rebootIfNecessary("device.clickAppIcon(iconLabel:$iconLabel)", true) { this.device.clickAppIcon(iconLabel) }
	}

	override fun launchMainActivity(launchableActivityComponentName: String) {
		// KJA recognition if launch succeeded and checking if ANR is displayed should be also implemented for
		// this.clickAppIcon(), which is called by caller of this method.

		var launchSucceeded = false
		try {
			// WISH when ANR immediately appears, waiting for full SysCmdExecutor.sysCmdExecuteTimeout to pass here is wasteful.
			this.device.launchMainActivity(launchableActivityComponentName)
			launchSucceeded = true

		} catch (e: AdbWrapperException) {
			log.warn(Markers.appHealth, "! device.launchMainActivity($launchableActivityComponentName) threw $e " +
					"Discarding the exception, rebooting and continuing.")

			this.rebootAndRestoreConnection()
		}

		// KJA if launch succeeded, but uia-daemon broke, this command will reboot device, returning home screen,
		// making exploration strategy terminate due to "home screen after reset". This happened on
		// net.zedge.android_v4.10.2-inlined.apk
		// KJA think where else the bug above can also cause problems. I.e. getting home screen due to uia-d reset.
		val guiSnapshot = this.getExplorableGuiSnapshotWithoutClosingANR()

		// KJA this case happened once com.spotify.music_v1.4.0.631-inlined.apk, but I forgot to write down random seed.
		// If this will happen more often, consider giving app second chance on restarting even after it crashes:
		// do not try to relaunch here; instead do it in exploration strategy. This way API logs from the failed launch will be
		// separated.
		if (launchSucceeded && guiSnapshot.isAppHasStoppedDialogBox)
			log.debug(Markers.appHealth, "device.launchMainActivity($launchableActivityComponentName) succeeded, but ANR is displayed.")
	}

	@Throws(DeviceException::class)
	private fun getExplorableGuiSnapshot(): DeviceResponse {
		var guiSnapshot = this.getRetryValidGuiSnapshotRebootingIfNecessary()
		guiSnapshot = closeANRIfNecessary(guiSnapshot)
		return guiSnapshot
	}

	@Throws(DeviceException::class)
	private fun getExplorableGuiSnapshotWithoutClosingANR(): DeviceResponse = this.getRetryValidGuiSnapshotRebootingIfNecessary()

	@Throws(DeviceException::class)
	private fun closeANRIfNecessary(guiSnapshot: DeviceResponse): DeviceResponse {
		if (!guiSnapshot.isAppHasStoppedDialogBox)
			return guiSnapshot

		assert(guiSnapshot.isAppHasStoppedDialogBox)
		var okWidget = guiSnapshot.widgets.first { it.text == "OK" }
		assert(okWidget.enabled)
		log.debug("ANR encountered")

		var out: DeviceResponse? = null

		Utils.retryOnFalse({

			okWidget = guiSnapshot.widgets.first { it.text == "OK" }
			device.perform(ClickAction(okWidget.xpath, okWidget.resourceId))
			out = this.getRetryValidGuiSnapshotRebootingIfNecessary()

			if (out!!.isAppHasStoppedDialogBox) {
				okWidget = out!!.widgets.first { it.text == "OK" }
				assert(okWidget.enabled)
				log.debug("ANR encountered - again. Failed to properly close it even though its OK widget was enabled.")
				false
			} else
				true
		},
				this.closeANRAttempts,
				this.closeANRDelay)

		return out!!
	}

	@Throws(DeviceException::class)
	private fun getRetryValidGuiSnapshotRebootingIfNecessary(): DeviceResponse = rebootIfNecessary("device.getRetryValidGuiSnapshot()", true) { this.getRetryValidGuiSnapshot() }

	@Throws(DeviceException::class)
	private fun getRetryValidGuiSnapshot(): DeviceResponse {
		try {
			return Utils.retryOnException(
					{ getValidGuiSnapshot() },
					{ restartUiaDaemon(false) },
					DeviceException::class,
					getValidGuiSnapshotRetryAttempts,
					getValidGuiSnapshotRetryDelay,
					"getValidGuiSnapshot")
		} catch (e: DeviceException) {
			throw AllDeviceAttemptsExhaustedException("All attempts at getting valid GUI snapshot failed", e)
		}
	}

	@Throws(DeviceException::class)
	private fun getValidGuiSnapshot(): DeviceResponse {
		// the rebootIfNecessary will reboot on TcpServerUnreachable
		return rebootIfNecessary("device.getGuiSnapshot()", true) {
			this.device.perform(FetchGUI())
		}
	}

	@Throws(DeviceException::class)
	private fun <T> rebootIfNecessary(description: String, makeSecondAttempt: Boolean, operationOnDevice: () -> T): T {
		try {
			return operationOnDevice.invoke()
		} catch (e: Exception) {
			if ((e !is TcpServerUnreachableException) and (e !is AllDeviceAttemptsExhaustedException))
				throw e

			log.warn(Markers.appHealth, "! Attempt to execute '$description' threw an exception: $e. " +
					(if (makeSecondAttempt)
						"Reconnecting adb, rebooting the device and trying again."
					else
						"Reconnecting adb, rebooting the device and continuing."))

			// Removed by Nataniel
			// This is not feasible when using the device farm, upon restart of the ADB server the connection
			// to the device is lost and it's assigned a new, random port, which doesn't allow automatic reconnection.
			//this.reconnectAdbDiscardingException("Call to reconnectAdb() just before call to rebootAndRestoreConnection() " +
			//        "failed with: %s. Discarding the exception and continuing wih rebooting.")
			//this.reinstallUiautomatorDaemon()
			this.rebootAndRestoreConnection()

			if (makeSecondAttempt) {
				log.info("Reconnected adb and rebooted successfully. Making second and final attempt at executing '$description'")
				try {
					val out = operationOnDevice()
					log.info("Second attempt at executing '$description' completed successfully.")
					return out
				} catch (e2: Exception) {
					if ((e2 !is TcpServerUnreachableException) and (e2 !is AllDeviceAttemptsExhaustedException))
						throw e2
					log.warn(Markers.appHealth, "! Second attempt to execute '$description' threw an exception: $e2. " +
							"Giving up and rethrowing.")
					throw e2
				}
			} else {
				throw e
			}
		}
	}

	override fun reboot() {
		if (this.device.isAvailable()) {
			log.trace("Device is available for rebooting.")
		} else {
			log.trace("Device not yet available for a reboot. Waiting $waitForCanRebootDelay milliseconds. If the device still won't be available, " +
					"assuming it cannot be reached at all.")

			sleep(this.waitForCanRebootDelay.toLong())

			if (this.device.isAvailable())
				log.trace("Device can be rebooted after the wait.")
			else
				throw DeviceException("Device is not available for a reboot, even after the wait. Requesting to stop further apk explorations.", true)
		}

		log.trace("Rebooting.")
		this.device.reboot()

		sleep(this.checkDeviceAvailableAfterRebootFirstDelay.toLong())
		// WISH use "adb wait-for-device"
		val rebootResult = Utils.retryOnFalse({
			val out = this.device.isAvailable()
			if (!out)
				log.trace("Device not yet available after rebooting, waiting $checkDeviceAvailableAfterRebootLaterDelays milliseconds and retrying")
			out
		},
				checkDeviceAvailableAfterRebootAttempts,
				checkDeviceAvailableAfterRebootLaterDelays)

		if (rebootResult) {
			assert(this.device.isAvailable())
			log.trace("Reboot completed successfully.")
		} else {
			assert(!this.device.isAvailable())
			throw DeviceException("Device is not available after a reboot. Requesting to stop further apk explorations.", true)
		}

		assert(!this.device.uiaDaemonClientThreadIsAlive())
	}

	override fun rebootAndRestoreConnection() {
		// Removed by Nataniel
		// This is not feasible when using the device farm, upon restart of the ADB server the connection
		// to the device is lost and it's assigned a new, random port, which doesn't allow automatic reconnection.
	}

	override fun getAndClearCurrentApiLogs(): List<IApiLogcatMessage> =
			rebootIfNecessary("messagesReader.getAndClearCurrentApiLogs()", true) { this.messagesReader.getAndClearCurrentApiLogs() }

	override fun closeConnection() {
		rebootIfNecessary("closeConnection()", true) { this.device.closeConnection() }
	}

	override fun toString(): String = "robust-" + this.device.toString()

	override fun pushFile(jar: Path) {
		this.device.pushFile(jar)
	}

	override fun pushFile(jar: Path, targetFileName: String) {
		this.device.pushFile(jar, targetFileName)
	}

	override fun removeJar(jar: Path) {
		this.device.removeJar(jar)
	}

	override fun installApk(apk: Path) {
		this.device.installApk(apk)
	}

	override fun installApk(apk: IApk) {
		this.device.installApk(apk)
	}

	override fun isApkInstalled(apkPackageName: String): Boolean {
		return this.device.isApkInstalled(apkPackageName)
	}

	override fun closeMonitorServers() {
		this.device.closeMonitorServers()
	}

	override fun appProcessIsRunning(appPackageName: String): Boolean = this.device.appProcessIsRunning(appPackageName)

	override fun clearLogcat() {
		this.device.clearLogcat()
	}

	override fun stopUiaDaemon(uiaDaemonThreadIsNull: Boolean) {
		try {
			this.device.stopUiaDaemon(uiaDaemonThreadIsNull)
		} catch (e: TcpServerUnreachableException) {
			log.warn("Unable to issue stop command to UIAutomator. Assuming it'' no longer running.")
		}
	}

	override fun isAvailable(): Boolean {
		return try {
			this.device.isAvailable()
		} catch (ignored: NoAndroidDevicesAvailableException) {
			false
		}
	}

	override fun uiaDaemonClientThreadIsAlive(): Boolean = this.device.uiaDaemonClientThreadIsAlive()

	override fun restartUiaDaemon(uiaDaemonThreadIsNull: Boolean) {
		if (this.uiaDaemonIsRunning()) {
			this.stopUiaDaemon(uiaDaemonThreadIsNull)
		}
		this.startUiaDaemon()
	}

	override fun startUiaDaemon() {
		this.device.startUiaDaemon()
	}

	override fun removeLogcatLogFile() {
		this.device.removeLogcatLogFile()
	}

	override fun pullLogcatLogFile() {
		this.device.pullLogcatLogFile()
	}

	override fun reinstallUiautomatorDaemon() {
		this.device.reinstallUiautomatorDaemon()
	}

	override fun pushMonitorJar() {
		this.device.pushMonitorJar()
	}

	override fun reconnectAdb() {
		this.device.reconnectAdb()
	}

	override fun executeAdbCommand(command: String, successfulOutput: String, commandDescription: String) {
		this.device.executeAdbCommand(command, successfulOutput, commandDescription)
	}

	override fun uiaDaemonIsRunning(): Boolean {
		return try {
			this.device.uiaDaemonIsRunning()
		} catch (e: Exception) {
			log.warn("Could not check if UIAutomator daemon is running. Assuming it is not and proceeding")

			false
		}
	}

	override fun isPackageInstalled(packageName: String): Boolean = this.device.isPackageInstalled(packageName)

	override fun hasPackageInstalled(packageName: String): Boolean = this.device.hasPackageInstalled(packageName)

	override fun readLogcatMessages(messageTag: String): List<ITimeFormattedLogcatMessage> =
			this.device.readLogcatMessages(messageTag)

	override fun waitForLogcatMessages(messageTag: String, minMessagesCount: Int, waitTimeout: Int, queryDelay: Int): List<ITimeFormattedLogcatMessage> =
			this.device.waitForLogcatMessages(messageTag, minMessagesCount, waitTimeout, queryDelay)

	override fun readAndClearMonitorTcpMessages(): List<List<String>> = this.device.readAndClearMonitorTcpMessages()

	override fun getCurrentTime(): LocalDateTime = this.device.getCurrentTime()

	override fun anyMonitorIsReachable(): Boolean = this.device.anyMonitorIsReachable()

	override fun appIsRunning(appPackageName: String): Boolean = this.device.appIsRunning(appPackageName)

	override fun resetTimeSync() = this.messagesReader.resetTimeSync()
}
