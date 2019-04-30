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

package org.droidmate.uiautomator2daemon
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.DeviceConstants.uiaDaemon_logcatTag
import org.droidmate.deviceInterface.communication.DeviceCommand
import org.droidmate.deviceInterface.communication.ExecuteCommand
import org.droidmate.deviceInterface.communication.StopDaemonCommand
import org.droidmate.deviceInterface.exploration.DeviceResponse
import org.droidmate.uiautomator2daemon.exploration.*
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.UiAutomationEnvironment
import kotlin.math.max

/**
 * Decides if UiAutomator2DaemonDriver should wait for the window to go to idle state after each click.
 */
class UiAutomator2DaemonDriver(waitForIdleTimeout: Long, waitForInteractiveTimeout: Long, imgQuality: Int,
                               delayedImgTransfer: Boolean,
                               enablePrintouts: Boolean = false) : IUiAutomator2DaemonDriver {
	private val uiEnvironment: UiAutomationEnvironment = UiAutomationEnvironment(idleTimeout = waitForIdleTimeout, interactiveTimeout = waitForInteractiveTimeout, imgQuality = imgQuality, delayedImgTransfer = delayedImgTransfer, enablePrintouts = enablePrintouts)

	private var nActions = 0
	@Throws(DeviceDaemonException::class)
	override fun executeCommand(deviceCommand: DeviceCommand): DeviceResponse {
		Log.v(uiaDaemon_logcatTag, "Executing device command: ($nActions) $deviceCommand")

		return try {

			when (deviceCommand) {
				is ExecuteCommand ->
					performAction(deviceCommand)
			// The server will be closed after this response is sent, because the given deviceCommand
			// will be interpreted in the caller, i.e. Uiautomator2DaemonTcpServerBase.
				is StopDaemonCommand -> DeviceResponse.empty
			}
		} catch (e: Throwable) {
			Log.e(uiaDaemon_logcatTag, "Error: " + e.message)
			Log.e(uiaDaemon_logcatTag, "Printing stack trace for debug")
			e.printStackTrace()

			ErrorResponse(e)
		}
	}

	private var tFetch = 0L
	private var tExec = 0L
	private var et = 0.0
	@Throws(DeviceDaemonException::class)
	private fun performAction(deviceCommand: ExecuteCommand): DeviceResponse = runBlocking {
		deviceCommand.guiAction.let { action ->
			debugT(" EXECUTE-TIME avg = ${et / max(1, nActions)}", {

				Log.v(uiaDaemon_logcatTag, "Performing GUI action $action [${action.id}]")

				val result = debugT("execute action avg= ${tExec / (max(nActions, 1) * 1000000)}", {
					lastId = action.id
					action.execute(uiEnvironment)
				}, inMillis = true, timer = {
					tExec += it
				})
//TODO if the previous action was not successful we should return an "ActionFailed"-DeviceResponse

				if (!action.isFetch()) // only fetch once even if the action was a FetchGUI action
					debugT("FETCH avg= ${tFetch / (max(nActions, 1) * 1000000)}", { fetchDeviceData(uiEnvironment, afterAction = true) }, inMillis = true, timer = {
						//					if (action !is DeviceLaunchApp) {
						tFetch += it
//					}
					})
				else result as DeviceResponse
			}, inMillis = true, timer = {
				//				if (action !is DeviceLaunchApp) {
				et += it / 1000000.0
				nActions += 1
//				}
			})
		}}
}