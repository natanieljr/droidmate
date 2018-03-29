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
package org.droidmate.uiautomator2daemon

import android.app.UiAutomation
import android.content.Context
import android.os.Build

import android.os.RemoteException

import android.support.test.InstrumentationRegistry
import android.support.test.uiautomator.*
import android.util.Log
import org.droidmate.uiautomator_daemon.DeviceCommand
import org.droidmate.uiautomator_daemon.DeviceResponse
import org.droidmate.uiautomator_daemon.UiAutomatorDaemonException

import org.droidmate.uiautomator2daemon.DeviceAction.Companion.fetchDeviceData
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants.DEVICE_COMMAND_GET_UIAUTOMATOR_WINDOW_HIERARCHY_DUMP
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants.DEVICE_COMMAND_PERFORM_ACTION
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants.DEVICE_COMMAND_STOP_UIADAEMON
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants.uiaDaemon_logcatTag

/**
 * Decides if UiAutomator2DaemonDriver should wait for the window to go to idle state after each click.
 */
internal class UiAutomator2DaemonDriver : IUiAutomator2DaemonDriver {
	private val device: UiDevice?
	private val context: Context?
	private val automation: UiAutomation?

	private val deviceModel: String
		get() {
			Log.d(uiaDaemon_logcatTag, "getDeviceModel()")
			val model = Build.MODEL
			val manufacturer = Build.MANUFACTURER
			val api = Build.VERSION.SDK_INT
			val fullModelName = "$manufacturer-$model/$api"
			Log.d(uiaDaemon_logcatTag, "Device model: $fullModelName")
			return fullModelName
		}

	init {
		// Disabling waiting for selector implicit timeout
		Configurator.getInstance().waitForSelectorTimeout = 0L

		// The instrumentation required to run uiautomator2-daemon is
		// provided by the command: adb shell instrument <PACKAGE>/<RUNNER>
		val instr = InstrumentationRegistry.getInstrumentation() ?: throw AssertionError()

		this.automation = instr.uiAutomation
		if (this.automation == null) throw AssertionError()
		automation.setRunAsMonkey(true) // tell the app that it is run in test-framework TODO check if that helps with adds or hides behavior

		this.context = InstrumentationRegistry.getTargetContext()
		if (context == null) throw AssertionError()

		this.device = UiDevice.getInstance(instr)
		if (device == null) throw AssertionError()
		try {
			device.setOrientationNatural()
		} catch (e: RemoteException) {
			e.printStackTrace()
		}

	}

	@Throws(UiAutomatorDaemonException::class)
	override fun executeCommand(deviceCommand: DeviceCommand): DeviceResponse {
		Log.v(uiaDaemon_logcatTag, "Executing device command: " + deviceCommand.command)

		var response = DeviceResponse()

		try {

			when (deviceCommand.command) {
				DEVICE_COMMAND_STOP_UIADAEMON -> {
				}
				DEVICE_COMMAND_GET_UIAUTOMATOR_WINDOW_HIERARCHY_DUMP -> response = fetchDeviceData(device!!, automation!!, deviceModel)
				DEVICE_COMMAND_PERFORM_ACTION -> response = performAction(deviceCommand)
				else -> throw UiAutomatorDaemonException(String.format("The command %s is not implemented yet!", deviceCommand.command))
			}// The server will be closed after this response is sent, because the given deviceCommand.command will be interpreted
			// in the caller, i.e. Uiautomator2DaemonTcpServerBase.

		} catch (e: Throwable) {
			Log.e(uiaDaemon_logcatTag, "Error: " + e.message)
			Log.e(uiaDaemon_logcatTag, "Printing stack trace for debug")
			e.printStackTrace()

			response.throwable = e
		}

		return response
	}


	@Throws(UiAutomatorDaemonException::class)
	private fun performAction(deviceCommand: DeviceCommand): DeviceResponse {
		Log.v(uiaDaemon_logcatTag, "Performing GUI action")

		val action = DeviceAction.fromAction(deviceCommand.guiAction!!)

		action?.execute(device!!, context!!)

		return fetchDeviceData(device!!, automation!!, deviceModel)
	}
}