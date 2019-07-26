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

package org.droidmate.device

import org.droidmate.device.error.DeviceException
import org.droidmate.device.android_sdk.IAdbWrapper
import org.droidmate.deviceInterface.communication.DeviceCommand
import org.droidmate.deviceInterface.exploration.DeviceResponse
import java.lang.UnsupportedOperationException

class TcpClients constructor(adbWrapper: IAdbWrapper,
                             deviceSerialNumber: String,
                             monitorSocketTimeout: Int,
                             uiautomatorDaemonSocketTimeout: Int,
                             uiautomatorDaemonTcpPort: Int,
                             uiautomatorDaemonServerServerStartTimeout: Int,
                             uiautomatorDaemonServerWaitForInteractableTimeout: Int,
                             hostIp: String,
                             apiMonitorPort: Int,
                             coverageMonitorPort: Int) : ITcpClients {

    private val apiMonitorClient: IApiMonitorClient = ApiMonitorClient(monitorSocketTimeout,
		deviceSerialNumber,
		adbWrapper,
		hostIp,
		apiMonitorPort)
    private val coverageMonitorClient = CoverageMonitorClient(monitorSocketTimeout,
		deviceSerialNumber,
		adbWrapper,
        	hostIp,
		coverageMonitorPort)
    private val monitorClients: List<IMonitorClient> = listOf(apiMonitorClient, coverageMonitorClient)

    private val uiautomatorClient: IUiautomatorDaemonClient = UiautomatorDaemonClient(
            adbWrapper,
            deviceSerialNumber,
            hostIp,
            uiautomatorDaemonTcpPort,
            uiautomatorDaemonSocketTimeout,
            uiautomatorDaemonServerServerStartTimeout,
            uiautomatorDaemonServerWaitForInteractableTimeout)

	override fun anyMonitorIsReachable(): Boolean = monitorClients.all { it.anyMonitorIsReachable() }

	override fun closeMonitorServers() {
		monitorClients.forEach { it.closeMonitorServers() }
	}

	override fun getCurrentTime(): List<List<String>> = apiMonitorClient.getCurrentTime()

	override fun getLogs(): List<List<String>> = apiMonitorClient.getLogs()

	override fun getStatements(): List<List<String>> = coverageMonitorClient.getStatements()

    // There is no single port for TcpClients
	override fun getPort(): Int = throw UnsupportedOperationException("Not supported for TcpClients.")

	override fun getUiaDaemonThreadIsAlive(): Boolean = uiautomatorClient.getUiaDaemonThreadIsAlive()

	override fun getUiaDaemonThreadIsNull(): Boolean = uiautomatorClient.getUiaDaemonThreadIsNull()

	override fun sendCommandToUiautomatorDaemon(deviceCommand: DeviceCommand): DeviceResponse =
			uiautomatorClient.sendCommandToUiautomatorDaemon(deviceCommand)

	override fun startUiaDaemon() {
		uiautomatorClient.startUiaDaemon()
	}

	override fun waitForUiaDaemonToClose() {
		uiautomatorClient.waitForUiaDaemonToClose()
	}

	override fun forwardPort() {
		this.uiautomatorClient.forwardPort()
	}

	@Throws(DeviceException::class)
	override fun forwardPorts() {
		this.uiautomatorClient.forwardPort()
		monitorClients.forEach { it.forwardPorts() }
	}
}
