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

package org.droidmate.test_tools.device_simulation

import org.droidmate.deviceInterface.communication.TimeFormattedLogMessageI
import org.droidmate.device.IAndroidDevice
import org.droidmate.device.android_sdk.IApk
import org.droidmate.deviceInterface.exploration.DeviceResponse
import org.droidmate.deviceInterface.exploration.ExplorationAction
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * The simulator has only rudimentary support for multiple apps.
 * It is expected to be either used with one app, or with multiple apps only for exception handling simulation.
 * Right now "spec" is used for all the apks simulations on the simulator (obtained pkgNames) and a call to "installApk"
 * switches the simulations.
 */
class AndroidDeviceSimulator/*(timeGenerator: ITimeGenerator,
                             pkgNames: List<String> = arrayListOf(ApkFixtures.apkFixture_simple_packageName),
                             spec: String,
                             private val exceptionSpecs: List<IExceptionSpec> = ArrayList(),
                             unreliableSimulation: Boolean = false)*/ // TODO Fix tests
	: IAndroidDevice {
	override suspend fun readStatements(): List<List<String>> {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun pullFile(fileName: String, dstPath: Path, srcPath: String) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun removeFile(fileName: String, srcPath: String) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun isApkInstalled(apkPackageName: String): Boolean {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	// TODO Fix tests
	override suspend fun pushFile(jar: Path) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun pushFile(jar: Path, targetFileName: String) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun removeJar(jar: Path) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun installApk(apk: Path) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun installApk(apk: IApk) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun uninstallApk(apkPackageName: String, ignoreFailure: Boolean) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun closeMonitorServers() {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun clearPackage(apkPackageName: String) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun appProcessIsRunning(appPackageName: String): Boolean {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun clearLogcat() {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun closeConnection() {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun reboot() {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun stopUiaDaemon(uiaDaemonThreadIsNull: Boolean) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun isAvailable(): Boolean {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun uiaDaemonClientThreadIsAlive(): Boolean {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun restartUiaDaemon(uiaDaemonThreadIsNull: Boolean) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun startUiaDaemon() {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun removeLogcatLogFile() {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun reinstallUiAutomatorDaemon() {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun pushAuxiliaryFiles() {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun setupConnection() {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun reconnectAdb() {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun executeAdbCommand(command: String, successfulOutput: String, commandDescription: String) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun uiaDaemonIsRunning(): Boolean {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun isPackageInstalled(packageName: String): Boolean {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun hasPackageInstalled(packageName: String): Boolean {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun perform(action: ExplorationAction): DeviceResponse {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun readLogcatMessages(messageTag: String): List<TimeFormattedLogMessageI> {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun waitForLogcatMessages(messageTag: String, minMessagesCount: Int, waitTimeout: Int, queryDelay: Int): List<TimeFormattedLogMessageI> {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun readAndClearMonitorTcpMessages(): List<List<String>> {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun getCurrentTime(): LocalDateTime {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun anyMonitorIsReachable(): Boolean {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override suspend fun appIsRunning(appPackageName: String): Boolean {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	// TODO Fix tests
	/*companion object {
			private val logcat = LoggerFactory.getLogger(AndroidDeviceSimulator::class.java)

			@JvmStatic
			fun build(timeGenerator: ITimeGenerator = TimeGenerator(),
								pkgNames: List<String>,
								exceptionSpecs: List<IExceptionSpec> = ArrayList(),
								unreliableSimulation: Boolean = false): AndroidDeviceSimulator {
					return AndroidDeviceSimulator(timeGenerator, pkgNames, "s1-w12->s2 " +
									"s1-w13->s3 " +
									"s2-w22->s2 " +
									"s2-w2h->home", exceptionSpecs, unreliableSimulation)
			}
	}

	private val simulations: List<IDeviceSimulation>

	var currentSimulation: IDeviceSimulation? = null

	private val logcatMessagesToBeReadNext: MutableList<TimeFormattedLogMessageI> = mutableListOf()

	private val callCounters = CallCounters()
	private var uiaDaemonIsRunning = false

	fun buildDeviceSimulation(timeGenerator: ITimeGenerator, packageName: String, spec: String, unreliable: Boolean): IDeviceSimulation {
			if (unreliable)
					return UnreliableDeviceSimulation(timeGenerator, packageName, spec)
			else
					return DeviceSimulation(timeGenerator, packageName, spec)
	}

	private fun getCurrentlyDeployedPackageName(): String
					= this.currentSimulation!!.packageName

	override fun hasPackageInstalled(packageName: String): Boolean {
			logcat.debug("hasPackageInstalled($packageName)")
			assert(this.getCurrentlyDeployedPackageName() == packageName)

			val s = findMatchingExceptionSpecAndThrowIfApplies("hasPackageInstalled", packageName)
			if (s != null) {
					assert(!s.throwsEx)
					return s.exceptionalReturnBool!!
			}

			return this.getCurrentlyDeployedPackageName() == packageName
	}

	private fun findMatchingExceptionSpec(methodName: String, packageName: String): IExceptionSpec? {
			return this.exceptionSpecs.singleOrNull {
					it.matches(methodName, packageName, callCounters.get(packageName, methodName))
			}
	}

	@Throws(TestDeviceException::class)
	private fun findMatchingExceptionSpecAndThrowIfApplies(methodName: String, packageName: String): IExceptionSpec? {
			callCounters.increment(packageName, methodName)
			val s = findMatchingExceptionSpec(methodName, packageName)
			if (s != null) {
					if (s.throwsEx)
							s.throwEx()
			}
			assert(s == null || !s.throwsEx)
			return s
	}

	override fun getGuiSnapshot(): IDeviceGuiSnapshot {
			logcat.debug("getGuiSnapshot()")

			findMatchingExceptionSpecAndThrowIfApplies("getGuiSnapshot", this.getCurrentlyDeployedPackageName())

			val outSnapshot = this.currentSimulation!!.getCurrentGuiSnapshot()

			logcat.debug("getGuiSnapshot(): $outSnapshot")
			return outSnapshot
	}

	override fun perform(action: ExplorationAction) {
			logcat.debug("perform($action)")

			findMatchingExceptionSpecAndThrowIfApplies("perform", this.getCurrentlyDeployedPackageName())

			when (action) {
					is LaunchAppAction -> assert(false, { "call .launchApp() directly instead" })
					is ClickAction -> updateSimulatorState(action)
					is CoordinateClickAction -> updateSimulatorState(action)
					is LongClickAction -> updateSimulatorState(action)
					is CoordinateLongClickAction -> updateSimulatorState(action)
					is SimulationAdbClearPackageAction -> assert(false, { "call .clearPackage() directly instead" })
					is EnableWifiAction -> { /* do nothing */
					}
					is PressHomeAction -> { /* do nothing */
					}
					is PressBackAction -> { /* do nothing */
					}
					else -> throw UnexpectedIfElseFallthroughError()
			}
	}

	private fun updateSimulatorState(action: ExplorationAction) {
			//if (action is ClickExplorationAction)
			//  println("action widget uid: ${(action as ClickExplorationAction).widget.uid}")

			this.currentSimulation!!.updateState(action)
			this.logcatMessagesToBeReadNext.addAll(currentSimulation!!.getCurrentLogs())
	}

	override fun clearLogcat() {
			logcat.debug("clearLogcat()")

			logcatMessagesToBeReadNext.clear()
	}

	override fun closeConnection() {
			findMatchingExceptionSpecAndThrowIfApplies("closeConnection", this.getCurrentlyDeployedPackageName())
			this.stopUiaDaemon(false)
	}

	override fun readLogcatMessages(messageTag: String): List<TimeFormattedLogMessageI> =
					logcatMessagesToBeReadNext.filter { it.tag == messageTag }

	override fun waitForLogcatMessages(messageTag: String, minMessagesCount: Int, waitTimeout: Int, queryDelay: Int): List<TimeFormattedLogMessageI> =
					readLogcatMessages(messageTag)

	override fun getCurrentTime(): LocalDateTime {
			return LocalDateTime.now()
	}

	override fun anyMonitorIsReachable(): Boolean = this.currentSimulation!!.getAppIsRunning()

	override fun launchApp(launchableActivityComponentName: String) {
			updateSimulatorState(LaunchAppAction(launchableActivityComponentName))
	}

	override fun appIsRunning(appPackageName: String): Boolean = this.appProcessIsRunning(appPackageName)

	override fun appProcessIsRunning(appPackageName: String): Boolean =
					this.currentSimulation!!.packageName == appPackageName && this.currentSimulation!!.getAppIsRunning()

	override fun clickAppIcon(iconLabel: String) {
			assert(false, { "Not yet implemented!" })
	}

	override fun takeScreenshot(app: IApk, suffix: String): Path {
			return Paths.get(".")
	}

	override fun pushFile(jar: Path) {
	}

	override fun pushFile(jar: Path, targetFileName: String) {
	}

	override fun removeJar(jar: Path) {
	}

	override fun installApk(apk: IApk) {
			this.currentSimulation = simulations.single { it.packageName == apk.packageName }
	}

	override fun installApk(apk: Path) {
			// Do nothing, used only to install UiAutomator2-daemon
	}

	override fun uninstallApk(apkPackageName: String, ignoreFailure: Boolean) {
			findMatchingExceptionSpecAndThrowIfApplies("uninstallApk", apkPackageName)
	}

	override fun closeMonitorServers() {
	}

	override fun clearPackage(apkPackageName: String) {
			updateSimulatorState(SimulationAdbClearPackageAction(apkPackageName))
	}

	override fun reboot() {
	}

	override fun stopUiaDaemon(uiaDaemonThreadIsNull: Boolean) {
			this.uiaDaemonIsRunning = false
	}

	override fun isAvailable(): Boolean {
			return true
	}

	override fun uiaDaemonClientThreadIsAlive(): Boolean {
			return this.uiaDaemonIsRunning
	}

	override fun restartUiaDaemon(uiaDaemonThreadIsNull: Boolean) {
			if (this.uiaDaemonIsRunning())
					this.stopUiaDaemon(uiaDaemonThreadIsNull)
			this.startUiaDaemon()
	}

	override fun startUiaDaemon() {
			this.uiaDaemonIsRunning = true
	}

	override fun setupConnection() {
			this.startUiaDaemon()
	}

	override fun removeLogcatLogFile() {
	}

	override fun pullLogcatLogFile() {
	}

	override fun reinstallUiAutomatorDaemon() {
	}

	override fun pushAuxiliaryFiles() {

	}

	override fun readAndClearMonitorTcpMessages(): List<List<String>> {
			return ArrayList()
	}

	override fun toString(): String {
			return this.javaClass.simpleName
	}

	override fun initModel() {
	}

	override fun reconnectAdb() {
	}

	override fun executeAdbCommand(command: String, successfulOutput: String, commandDescription: String) {
	}

	override fun uiaDaemonIsRunning(): Boolean {
			return this.uiaDaemonIsRunning
	}

	override fun isPackageInstalled(packageName: String): Boolean {
			return false
	}

	initialize {
			this.simulations = pkgNames.map { buildDeviceSimulation(timeGenerator, it, spec, unreliableSimulation) }
			this.currentSimulation = this.simulations[0]
	}*/
}