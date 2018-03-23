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

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.android_sdk.IApk
import org.droidmate.debug.debugT
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.exploration.device.DeviceLogsHandler
import org.droidmate.exploration.device.IRobustDevice
import org.droidmate.uiautomator_daemon.guimodel.ClickAction
import org.droidmate.uiautomator_daemon.guimodel.CoordinateClickAction
import org.droidmate.uiautomator_daemon.guimodel.CoordinateLongClickAction
import org.droidmate.uiautomator_daemon.guimodel.LongClickAction
import sun.audio.AudioDevice.device
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private var performT:Long = 0
private var performN:Int = 1
class RunnableWidgetExplorationAction constructor(action: WidgetExplorationAction,
                                                  timestamp: LocalDateTime,
                                                  takeScreenshot: Boolean) : RunnableExplorationAction(action, timestamp, takeScreenshot) {

    companion object {
        private const val serialVersionUID: Long = 1
    }

    override fun performDeviceActions(app: IApk, device: IRobustDevice) = runBlocking{
        log.debug("1. Assert only background API logs are present, if any.")
        val logsHandler = DeviceLogsHandler(device)
        debugT("reading log",{logsHandler.readClearAndAssertOnlyBackgroundApiLogsIfAny()}, inMillis = true)

        val action = base as WidgetExplorationAction
        log.debug("2. Perform widget click: $action.")

        val x = action.widget.bounds.centerX.toInt()
        val y = action.widget.bounds.centerY.toInt()
        try {
            debugT("perform action on average ${performT/performN} ms", {
	            launch {
		            // do the perform as launch to inject a suspension point, as perform is currently no suspend function
		            when {
			            action.useCoordinates && !action.longClick -> device.perform(CoordinateClickAction(x, y))
			            action.useCoordinates && action.longClick -> device.perform(CoordinateLongClickAction(x, y))
			            !action.useCoordinates && !action.longClick -> device.perform(ClickAction(action.widget.xpath, action.widget.resourceId))
			            !action.useCoordinates && action.longClick -> device.perform(LongClickAction(action.widget.xpath, action.widget.resourceId))
			            else -> throw UnexpectedIfElseFallthroughError("Action type not yet supported in ${this.javaClass.simpleName}")
		            }
	            }.join()
            }, timer = {
              performT += it/1000000
	            performN += 1
            }, inMillis = true)
        } catch (e: Exception) {
            if (!action.useCoordinates) {
                log.warn("2.1. Failed to click using XPath and resourceID, attempting restart UIAutomatorDaemon and to click coordinates: $action.")
                device.restartUiaDaemon(false)
                when {
                    !action.longClick -> device.perform(CoordinateClickAction(x, y))
                    action.longClick -> device.perform(CoordinateLongClickAction(x, y))
                }
            }
        }

        log.debug("3. Read and clear API logs if any, then seal logs reading.")
        debugT("read log after action",{logsHandler.readAndClearApiLogs()}, inMillis = true)
        logs = logsHandler.getLogs()

//        Thread.sleep(action.delay.toLong())
        delay(action.delay)

        if (takeScreenshot) {
            // this was moved before the snapshot, as otherwise the screen may show the loaded page but the snapshot does not contain the elements
            log.debug("4. Get GUI screenshot.")

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS")
            screenshot = debugT("get screenshot",{ device.takeScreenshot(app, timestamp.format(formatter)).toUri() }, inMillis = true)
        }

        log.debug("4. Get GUI snapshot.")
        snapshot = debugT("windowDump retrieve",{device.getGuiSnapshot()}, inMillis = true)
        //TODO take screenshot before and after dump to ensure they are matching, if not equal => take another device GuiSnapshot
    }
}

