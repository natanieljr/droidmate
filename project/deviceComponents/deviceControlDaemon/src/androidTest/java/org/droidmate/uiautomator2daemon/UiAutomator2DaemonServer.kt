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

import android.util.Log
import org.droidmate.deviceInterface.communication.DeviceCommand
import org.droidmate.deviceInterface.communication.StopDaemonCommand
import org.droidmate.deviceInterface.communication.DeviceDaemonException
import org.droidmate.deviceInterface.UiautomatorDaemonConstants.UIADAEMON_SERVER_START_MSG

import org.droidmate.deviceInterface.UiautomatorDaemonConstants.UIADAEMON_SERVER_START_TAG
import org.droidmate.deviceInterface.UiautomatorDaemonConstants.uiaDaemon_logcatTag
import org.droidmate.deviceInterface.exploration.DeviceResponse

class UiAutomator2DaemonServer internal constructor(private val uiaDaemonDriver: IUiAutomator2DaemonDriver)
	: Uiautomator2DaemonTcpServerBase<DeviceCommand, DeviceResponse>(UIADAEMON_SERVER_START_TAG, UIADAEMON_SERVER_START_MSG) {

	override fun onServerRequest(deviceCommand: DeviceCommand, deviceCommandReadEx: Exception?): DeviceResponse {

		try {
			if (deviceCommandReadEx != null)
				throw deviceCommandReadEx

			return uiaDaemonDriver.executeCommand(deviceCommand)

		} catch (e: DeviceDaemonException) {
			Log.e(uiaDaemon_logcatTag,"Server: Failed to execute command $deviceCommand and thus, obtain appropriate GuiState. Returning exception-DeviceResponse.", e)

			return DeviceResponse.empty.apply { throwable = e }
		} catch (t: Throwable) {
			Log.wtf(uiaDaemon_logcatTag, "Server: Failed, with a non-${DeviceDaemonException::class.java.simpleName} (!)," +
					"to execute command $deviceCommand and thus, obtain appropriate GuiState. Returning throwable-DeviceResponse.", t)

			return DeviceResponse.empty.apply { throwable = t }
		}
	}

	override fun shouldCloseServerSocket(deviceCommand: DeviceCommand): Boolean {
		return deviceCommand is StopDaemonCommand
	}

}
