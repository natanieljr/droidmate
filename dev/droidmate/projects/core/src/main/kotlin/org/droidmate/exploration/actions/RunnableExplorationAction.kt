// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2017 Konrad Jamrozik
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
package org.droidmate.exploration.actions

import org.droidmate.android_sdk.DeviceException
import org.droidmate.android_sdk.IApk
import org.droidmate.device.datatypes.IDeviceGuiSnapshot
import org.droidmate.device.datatypes.MissingGuiSnapshot
import org.droidmate.device.datatypes.WaitAction
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.exploration.device.IDeviceLogs
import org.droidmate.exploration.device.IRobustDevice
import org.droidmate.exploration.device.MissingDeviceLogs
import org.droidmate.logging.Markers
import org.slf4j.LoggerFactory
import java.net.URI

import java.time.LocalDateTime

abstract class RunnableExplorationAction(override val base: ExplorationAction,
                                         override val timestamp: LocalDateTime,
                                         override val takeScreenshot: Boolean = false) : IRunnableExplorationAction, ExplorationAction() {

    companion object {
        private const val serialVersionUID: Long = 1
        internal val log = LoggerFactory.getLogger(RunnableExplorationAction::class.java)

        @JvmStatic
        @JvmOverloads
        fun from(action: ExplorationAction, timestamp: LocalDateTime, takeScreenShot: Boolean = false): RunnableExplorationAction
                =//    log.trace("Building exploration action ${action.class} with timestamp: $timestamp")

                when (action) {
                    is ResetAppExplorationAction -> RunnableResetAppExplorationAction(action, timestamp, takeScreenShot)
                    is WidgetExplorationAction -> RunnableWidgetExplorationAction(action, timestamp, takeScreenShot)
                    is TerminateExplorationAction -> RunnableTerminateExplorationAction(action, timestamp, takeScreenShot)
                    is EnterTextExplorationAction -> RunnableEnterTextExplorationAction(action, timestamp, takeScreenShot)
                    is PressBackExplorationAction -> RunnablePressBackExplorationAction(action, timestamp, takeScreenShot)
                    is WaitAction -> RunnableWaitForWidget(action, timestamp, takeScreenShot)

                    else -> throw UnexpectedIfElseFallthroughError("Unhandled ExplorationAction class. The class: ${action.javaClass}")
                }
    }

    protected lateinit var snapshot: IDeviceGuiSnapshot
    protected lateinit var logs: IDeviceLogs
    protected lateinit var exception: DeviceException
    override var screenshot: URI = URI.create("test://empty")


    override fun run(app: IApk, device: IRobustDevice): IExplorationActionRunResult {
        var successful = true

        // @formatter:off
        this.logs = MissingDeviceLogs()
        this.snapshot = MissingGuiSnapshot()
        this.exception = DeviceExceptionMissing()
        // @formatter:on

        try {
            log.trace("${this.javaClass.simpleName}.performDeviceActions(app=${app.fileName}, device)")
            this.performDeviceActions(app, device)
            log.trace("${this.javaClass.simpleName}.performDeviceActions(app=${app.fileName}, device) - DONE")
        } catch (e: DeviceException) {
            successful = false
            this.exception = e
            log.warn(Markers.appHealth, "! Caught ${e.javaClass.simpleName} while performing device actions of ${this.javaClass.simpleName}. " +
                    "Returning failed ${ExplorationActionRunResult::class.java.simpleName} with the exception assigned to a field.")
        }

        // For post-conditions, see inside the constructor call made line below.
        val result = ExplorationActionRunResult(successful, app.packageName, this.logs, this.snapshot, this.exception, this.screenshot)

        frontendHook(result)

        return result
    }

    /**
     * Allows to hook into the result of interacting with the device after an ExplorationAction has been executed on it.
     */
    fun frontendHook(result: IExplorationActionRunResult) {
        base.notifyResult(result)

        /*if (!(result.guiSnapshot is MissingGuiSnapshot)) {
            val widgets = result.guiSnapshot.guiState.widgets
            val isANR = result.guiSnapshot.guiState.isAppHasStoppedDialogBox
            // And so on. see IGuiState
        }

        if (!(result.deviceLogs is MissingDeviceLogs)) {
            val logs = result.deviceLogs.apiLogs
            logs.forEach { log ->
                val time = log.time
                val methodName = log.methodName
                // And so on. See org.droidmate.apis.ITimeFormattedLogcatMessage
                // and org.droidmate.apis.IApi
            }
        }

        if (!(result.successful)) {
            val exception = result.exception
        }*/

        // To-do for SE team
    }

    @Throws(DeviceException::class)
    abstract protected fun performDeviceActions(app: IApk, device: IRobustDevice)

    @Throws(DeviceException::class)
    protected fun assertAppIsNotRunning(device: IRobustDevice, apk: IApk) {
        assert(device.appIsNotRunning(apk))
    }

    override fun toString(): String = "Runnable " + base.toString()

    override fun isEndorseRuntimePermission(): Boolean
            = base.isEndorseRuntimePermission()

    override fun toShortString(): String
            = base.toShortString()

    override fun toTabulatedString(): String
            = base.toShortString()

    override fun notifyResult(result: IExplorationActionRunResult) {
        base.notifyResult(result)
    }

    override fun notifyObservers(result: IExplorationActionRunResult) {
        base.notifyObservers(result)
    }

    override fun unregisterObserver(observer: IExplorationActionResultObserver) {
        base.unregisterObserver(observer)
    }

    override fun registerObserver(observer: IExplorationActionResultObserver) {
        base.registerObserver(observer)
    }
}