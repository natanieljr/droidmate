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
package org.droidmate.device.deviceInterface

import org.droidmate.device.error.DeviceException
import org.droidmate.device.IExplorableAndroidDevice
import org.droidmate.device.logcat.ApiLogsReader
import org.droidmate.device.logcat.IApiLogcatMessage
import org.droidmate.device.logcat.IDeviceMessagesReader


/**
 * <p>
 * This class is responsible for reading messages from the device. It can read messages from the device logcat or from the
 * monitor TCP server (for the server source code, see {@code org.droidmate.monitor.MonitorJavaTemplate.MonitorTCPServer}).
 *
 * </p><p>
 * The messages read are either monitor init messages coming from logcat, method instrumentation messages coming from logcat, or
 * monitored API logs coming from monitor TCP server. In addition, this class maintains the time difference between the device
 * and the host machine, to sync the time logs from the device's clock with the host machine's clock.
 * </p>
 */
class DeviceMessagesReader @JvmOverloads constructor(device: IExplorableAndroidDevice,
                                                     private val useLogcat: Boolean = false) : IDeviceMessagesReader {
	private val apiLogsReader = ApiLogsReader(device)
	private val deviceTimeDiff = DeviceTimeDiff(device)

	override fun resetTimeSync() {
		this.deviceTimeDiff.reset()
	}

	@Throws(DeviceException::class)
	override fun getAndClearCurrentApiLogs(): List<IApiLogcatMessage> {
		return if (useLogcat)
			this.getAndClearCurrentApiLogsFromLogcat()
		else
			this.getAndClearCurrentApiLogsFromMonitorTcpServer()
	}

	@Deprecated("Deprecated. Prefer to use the TCP server instead.")
	@Throws(DeviceException::class)
	private fun getAndClearCurrentApiLogsFromLogcat(): List<IApiLogcatMessage> {
		return apiLogsReader.getCurrentApiLogsFromLogcat(deviceTimeDiff)
	}

	@Throws(DeviceException::class)
	private fun getAndClearCurrentApiLogsFromMonitorTcpServer(): List<IApiLogcatMessage> {
		return apiLogsReader.getAndClearCurrentApiLogsFromMonitorTcpServer(deviceTimeDiff)
	}
}