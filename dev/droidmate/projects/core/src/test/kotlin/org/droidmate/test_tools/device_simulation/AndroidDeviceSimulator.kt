// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org

package org.droidmate.test_tools.device_simulation

import org.droidmate.android_sdk.IApk
import org.droidmate.apis.ITimeFormattedLogcatMessage
import org.droidmate.device.IAndroidDevice
import org.droidmate.device.datatypes.*
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.test_tools.ApkFixtures
import org.droidmate.test_tools.exceptions.IExceptionSpec
import org.droidmate.test_tools.exceptions.TestDeviceException
import org.droidmate.uiautomator_daemon.guimodel.*
import org.slf4j.LoggerFactory

import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime

/**
 * The simulator has only rudimentary support for multiple apps.
 * It is expected to be either used with one app, or with multiple apps only for exception handling simulation.
 * Right now "spec" is used for all the apks simulations on the simulator (obtained pkgNames) and a call to "installApk"
 * switches the simulations.
 */
class AndroidDeviceSimulator(timeGenerator: ITimeGenerator,
                             pkgNames: List<String> = arrayListOf(ApkFixtures.apkFixture_simple_packageName),
                             spec: String,
                             private val exceptionSpecs: List<IExceptionSpec> = ArrayList(),
                             unreliableSimulation: Boolean = false) : IAndroidDevice {
    companion object {
        private val log = LoggerFactory.getLogger(AndroidDeviceSimulator::class.java)

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

    private val logcatMessagesToBeReadNext: MutableList<ITimeFormattedLogcatMessage> = ArrayList()

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
        log.debug("hasPackageInstalled($packageName)")
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
        log.debug("getGuiSnapshot()")

        findMatchingExceptionSpecAndThrowIfApplies("getGuiSnapshot", this.getCurrentlyDeployedPackageName())

        val outSnapshot = this.currentSimulation!!.getCurrentGuiSnapshot()

        log.debug("getGuiSnapshot(): $outSnapshot")
        return outSnapshot
    }

    override fun perform(action: Action) {
        log.debug("perform($action)")

        findMatchingExceptionSpecAndThrowIfApplies("perform", this.getCurrentlyDeployedPackageName())

        when (action) {
            is LaunchApp -> assert(false, { "call .launchMainActivity() directly instead" })
            is ClickAction-> updateSimulatorState(action)
            is CoordinateClickAction -> updateSimulatorState(action)
            is LongClickAction -> updateSimulatorState(action)
            is CoordinateLongClickAction -> updateSimulatorState(action)
            is SimulationAdbClearPackage -> assert(false, { "call .clearPackage() directly instead" })
            is EnableWifi -> { /* do nothing */}
            is PressHome -> { /* do nothing */}
            is PressBack -> { /* do nothing */}
            else -> throw UnexpectedIfElseFallthroughError()
        }
    }

    private fun updateSimulatorState(action: Action) {
        //if (action is WidgetExplorationAction)
        //  println("action widget uid: ${(action as WidgetExplorationAction).widget.uid}")

        this.currentSimulation!!.updateState(action)
        this.logcatMessagesToBeReadNext.addAll(currentSimulation!!.getCurrentLogs())
    }

    override fun clearLogcat() {
        log.debug("clearLogcat()")

        logcatMessagesToBeReadNext.clear()
    }

    override fun closeConnection() {
        findMatchingExceptionSpecAndThrowIfApplies("closeConnection", this.getCurrentlyDeployedPackageName())
        this.stopUiaDaemon(false)
    }

    override fun readLogcatMessages(messageTag: String): List<ITimeFormattedLogcatMessage> =
            logcatMessagesToBeReadNext.filter { it.tag == messageTag }

    override fun waitForLogcatMessages(messageTag: String, minMessagesCount: Int, waitTimeout: Int, queryDelay: Int): List<ITimeFormattedLogcatMessage> =
            readLogcatMessages(messageTag)

    override fun getCurrentTime(): LocalDateTime {
        return LocalDateTime.now()
    }

    override fun anyMonitorIsReachable(): Boolean = this.currentSimulation!!.getAppIsRunning()

    override fun launchMainActivity(launchableActivityComponentName: String) {
        updateSimulatorState(LaunchApp(launchableActivityComponentName))
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
        updateSimulatorState(SimulationAdbClearPackage(apkPackageName))
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

    override fun reinstallUiautomatorDaemon() {
    }

    override fun pushMonitorJar() {

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

    init {
        this.simulations = pkgNames.map { buildDeviceSimulation(timeGenerator, it, spec, unreliableSimulation) }
        this.currentSimulation = this.simulations[0]
    }
}