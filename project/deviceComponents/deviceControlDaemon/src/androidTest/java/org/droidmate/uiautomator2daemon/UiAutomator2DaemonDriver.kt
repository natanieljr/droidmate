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

import android.app.UiAutomation
import android.content.Context

import android.os.RemoteException

import android.support.test.InstrumentationRegistry
import android.support.test.uiautomator.*
import android.util.Log
import org.droidmate.deviceInterface.DeviceCommand
import org.droidmate.deviceInterface.DeviceResponse
import org.droidmate.deviceInterface.ExecuteCommand
import org.droidmate.deviceInterface.StopDaemonCommand
import org.droidmate.deviceInterface.UiAutomatorDaemonException

import org.droidmate.deviceInterface.UiautomatorDaemonConstants.uiaDaemon_logcatTag
import kotlin.math.max

/**
 * Decides if UiAutomator2DaemonDriver should wait for the window to go to idle state after each click.
 */
internal class UiAutomator2DaemonDriver(private val waitForIdleTimeout: Long, private val waitForInteractableTimeout: Long) : IUiAutomator2DaemonDriver {
	private val device: UiDevice
	private val context: Context
	private val automation: UiAutomation

	init {
		interactableTimeout = waitForInteractableTimeout
		idleTimeout = waitForIdleTimeout

		// Disabling waiting for selector implicit timeout
		Configurator.getInstance().waitForSelectorTimeout = 0L

		// The instrumentation required to run uiautomator2-daemon is
		// provided by the command: adb shell instrument <PACKAGE>/<RUNNER>
		val instr = InstrumentationRegistry.getInstrumentation() ?: throw AssertionError()

		this.automation = instr.uiAutomation
		if (this.automation == null) throw AssertionError()
		automation.setRunAsMonkey(true) // tell the app that it is run in test-framework, e.g. to tell the app not to call emergency numbers

		this.context = InstrumentationRegistry.getTargetContext()
		if (context == null) throw AssertionError()

		this.device = UiDevice.getInstance(instr)
		if (device == null) throw AssertionError()

		// Orientation is set initially to natural, however can be changed by action
		try {
			device.setOrientationNatural()
			device.freezeRotation()
		} catch (e: RemoteException) {
			e.printStackTrace()
		}
	}

	private var nActions = 0
	@Throws(UiAutomatorDaemonException::class)
	override fun executeCommand(deviceCommand: DeviceCommand): DeviceResponse {
		Log.v(uiaDaemon_logcatTag, "Executing device command: (${nActions-3}) $deviceCommand")

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

			DeviceResponse.empty.apply { throwable = e }
		}
	}

	private var tFetch = 0L
	private var tExec = 0L
	private var et = 0.0
	@Throws(UiAutomatorDaemonException::class)
	private fun performAction(deviceCommand: ExecuteCommand): DeviceResponse =
		deviceCommand.guiAction.let { action ->
			debugT(" EXECUTE-TIME avg = ${et / max(1, nActions)}", {

				Log.v(uiaDaemon_logcatTag, "Performing GUI action $action")

				val result = debugT("execute action avg= ${tExec / (max(nActions, 1) * 1000000)}", {
					action.execute(device, context, automation)
				}, inMillis = true, timer = {
					tExec += it
				})

				if( !action.isFetch() ) // only fetch once even if the action was a FetchGUI action
				debugT("FETCH avg= ${tFetch / (max(nActions, 1) * 1000000)}", { fetchDeviceData(device, waitForIdleTimeout) }, inMillis = true, timer = {
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
		}
}