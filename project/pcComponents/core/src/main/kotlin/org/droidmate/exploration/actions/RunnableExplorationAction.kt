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
package org.droidmate.exploration.actions

import org.droidmate.android_sdk.DeviceException
import org.droidmate.android_sdk.IApk
import org.droidmate.device.datatypes.WaitA
import org.droidmate.device.datatypes.statemodel.ActionResult
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.exploration.device.IDeviceLogs
import org.droidmate.exploration.device.IRobustDevice
import org.droidmate.exploration.device.MissingDeviceLogs
import org.droidmate.logging.Markers
import org.droidmate.uiautomator_daemon.GuiStatusResponse
import org.slf4j.LoggerFactory
import java.net.URI

import java.time.LocalDateTime

abstract class RunnableExplorationAction(override val base: ExplorationAction,
                                         override val timestamp: LocalDateTime,
                                         override val takeScreenshot: Boolean = false) : IRunnableExplorationAction {

	companion object {
		private const val serialVersionUID: Long = 1
		internal val log = LoggerFactory.getLogger(RunnableExplorationAction::class.java)

		@JvmStatic
		@JvmOverloads
		fun from(action: ExplorationAction, timestamp: LocalDateTime, takeScreenShot: Boolean = false): RunnableExplorationAction =//    log.trace("Building exploration action ${action.class} with timestamp: $timestamp")

				when (action) {
					is ResetAppExplorationAction -> RunnableResetAppExplorationAction(action, timestamp, takeScreenShot)
					is WidgetExplorationAction -> RunnableWidgetExplorationAction(action, timestamp, takeScreenShot)
					is TerminateExplorationAction -> RunnableTerminateExplorationAction(action, timestamp, takeScreenShot)
					is EnterTextExplorationAction -> RunnableEnterTextExplorationAction(action, timestamp, takeScreenShot)
					is PressBackExplorationAction -> RunnablePressBackExplorationAction(action, timestamp, takeScreenShot)
					is WaitA -> RunnableWaitForWidget(action, timestamp, takeScreenShot)

					else -> throw UnexpectedIfElseFallthroughError("Unhandled ExplorationAction class. The class: ${action.javaClass}")
				}
	}

	protected lateinit var snapshot: GuiStatusResponse
	protected lateinit var logs: IDeviceLogs
	protected lateinit var exception: DeviceException
	override var screenshot: URI = URI.create("test://empty")

	override fun run(app: IApk, device: IRobustDevice): ActionResult {
		// @formatter:off
		this.logs = MissingDeviceLogs
		this.snapshot = GuiStatusResponse.empty
		this.exception = DeviceExceptionMissing()
		// @formatter:on

		val startTime = LocalDateTime.now()
		try {
			log.trace("${this.javaClass.simpleName}.performDeviceActions(app=${app.fileName}, device)")
			this.performDeviceActions(app, device)
			log.trace("${this.javaClass.simpleName}.performDeviceActions(app=${app.fileName}, device) - DONE")
		} catch (e: DeviceException) {
			this.exception = e
			log.warn(Markers.appHealth, "! Caught ${e.javaClass.simpleName} while performing device actionTrace of ${this.javaClass.simpleName}. " +
					"Returning failed ${this.javaClass.simpleName} with the exception assigned to a field.")
		}
		val endTime = LocalDateTime.now()

		// For post-conditions, see inside the constructor call made line below.
		return ActionResult(this.base, startTime, endTime, this.logs, this.snapshot, exception = this.exception, screenshot = this.screenshot)
	}

	@Throws(DeviceException::class)
	protected abstract fun performDeviceActions(app: IApk, device: IRobustDevice)

	@Throws(DeviceException::class)
	protected fun assertAppIsNotRunning(device: IRobustDevice, apk: IApk) {
		assert(device.appIsNotRunning(apk))
	}

	override fun toString(): String = "Runnable " + base.toString()
}