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

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.debug.debugT
import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.deviceInterface.DeviceLogsHandler
import org.droidmate.device.deviceInterface.IRobustDevice
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.exploration.statemodel.Widget
import org.droidmate.uiautomator_daemon.guimodel.ClickAction
import org.droidmate.uiautomator_daemon.guimodel.CoordinateClickAction
import org.droidmate.uiautomator_daemon.guimodel.CoordinateLongClickAction
import org.droidmate.uiautomator_daemon.guimodel.LongClickAction

private var performT: Long = 0
private var performN: Int = 1

open class WidgetExplorationAction @JvmOverloads constructor(override val widget: Widget,
                                                             val longClick: Boolean = false,
                                                             val useCoordinates: Boolean = true,
                                                             val delay: Int = 100,
                                                             val swipe: Boolean = false,
                                                             val direction: Direction = Direction.UP) : AbstractExplorationAction() {
	companion object {
		private const val serialVersionUID: Long = 1
	}

	override fun toShortString(): String = "SW? ${if (swipe) 1 else 0} LC? ${if (longClick) 1 else 0} " + widget.toShortString()

	override fun toTabulatedString(): String = "SW? ${if (swipe) 1 else 0} LC? ${if (longClick) 1 else 0} " + widget.toTabulatedString()

	override fun performDeviceActions(app: IApk, device: IRobustDevice) = runBlocking {
		log.debug("1. Assert only background API logs are present, if any.")
		val logsHandler = DeviceLogsHandler(device)
		debugT("reading log", { logsHandler.readClearAndAssertOnlyBackgroundApiLogsIfAny() }, inMillis = true)

		log.debug("2. Perform widget click: $javaClass.")

		val x = widget.bounds.centerX.toInt()
		val y = widget.bounds.centerY.toInt()
		try {
			debugT("perform action on average ${performT / performN} ms", {
				launch {
					// do the perform as launch to inject a suspension point, as perform is currently no suspend function
					snapshot = when {
						useCoordinates && !longClick -> device.perform(CoordinateClickAction(x, y))
						useCoordinates && longClick -> device.perform(CoordinateLongClickAction(x, y))
						!useCoordinates && !longClick -> device.perform(ClickAction(widget.xpath, widget.resourceId))
						!useCoordinates && longClick -> device.perform(LongClickAction(widget.xpath, widget.resourceId))
						else -> throw UnexpectedIfElseFallthroughError("Action type not yet supported in ${this.javaClass.simpleName}")
					}
				}.join()
			}, timer = {
				performT += it / 1000000
				performN += 1
			}, inMillis = true)
		} catch (e: Exception) {
			if (!useCoordinates) {
				log.warn("2.1. Failed to click using XPath and resourceID, attempting restart UIAutomatorDaemon and to click coordinates: $javaClass.")
				device.restartUiaDaemon(false)
				snapshot = when {
					!longClick -> device.perform(CoordinateClickAction(x, y))
					longClick -> device.perform(CoordinateLongClickAction(x, y))
					else -> throw UnexpectedIfElseFallthroughError("Action type not yet supported in ${this.javaClass.simpleName}")
				}
			}
		}

		log.debug("3. Read and clear API logs if any, then seal logs reading.")
		debugT("read log after action", { logsHandler.readAndClearApiLogs() }, inMillis = true)
		logs = logsHandler.getLogs()

		delay(delay)
	}

}