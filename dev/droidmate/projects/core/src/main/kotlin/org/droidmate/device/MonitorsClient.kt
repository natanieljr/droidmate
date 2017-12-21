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
package org.droidmate.device

import org.droidmate.android_sdk.DeviceException
import org.droidmate.android_sdk.IAdbWrapper
import org.droidmate.misc.MonitorConstants
import org.slf4j.LoggerFactory

class MonitorsClient(socketTimeout: Int,
                     private val deviceSerialNumber: String,
                     private val adbWrapper: IAdbWrapper) : IMonitorsClient {

    companion object {
        private val log = LoggerFactory.getLogger(MonitorsClient::class.java)
    }

    private val monitorTcpClient: ITcpClientBase<String, ArrayList<ArrayList<String>>> = TcpClientBase(socketTimeout)

    override fun anyMonitorIsReachable(): Boolean {
        val out = this.getPorts().any {
            this.isServerReachable(it)
        }
        if (out)
            log.trace("At least one monitor is reachable.")
        else
            log.trace("No monitor is reachable.")
        return out
    }

    private fun isServerReachable(port: Int): Boolean {
        try {
            val out = this.monitorTcpClient.queryServer(MonitorConstants.srvCmd_connCheck, port)
            val diagnostics = out.single()

            assert(diagnostics.size >= 2)
            val pid = diagnostics[0]
            val packageName = diagnostics[1]
            log.trace("Reached server at port $port. PID: $pid package: $packageName")
            return true
        } catch (ignored: TcpServerUnreachableException) {
            return false
        }
    }

    override fun getCurrentTime(): List<List<String>> {
        for (port in this.getPorts()) {
            try {
                return monitorTcpClient.queryServer(MonitorConstants.srvCmd_get_time, port)

            } catch (ignored: TcpServerUnreachableException) {
                // log.trace("Did not reach monitor TCP server at port $it when sending out ${MonitorConstants.srvCmd_get_time} request.")
                assert(!this.anyMonitorIsReachable())
                throw DeviceException("None of the monitor TCP servers were available.", true)
            }
        }

        throw DeviceException("No monitors available.", true)
    }

    override fun getLogs(): List<List<String>> {
        for (port in this.getPorts()) {
            return try {
                monitorTcpClient.queryServer(MonitorConstants.srvCmd_get_logs, port)
            } catch (ignored: TcpServerUnreachableException) {
                // log.trace("Did not reach monitor TCP server at port $it when sending out ${MonitorConstants.srvCmd_get_logs} request.")
                log.trace("None of the monitor TCP servers were available while obtaining API logs.")
                ArrayList()
            }
        }

        throw DeviceException("No monitors available.", true)
    }

    override fun closeMonitorServers() {
        this.getPorts().forEach {
            try {
                monitorTcpClient.queryServer(MonitorConstants.srvCmd_close, it)
            } catch (ignored: TcpServerUnreachableException) {
                // log.trace("Did not reach monitor TCP server at port $it when sending out ${MonitorConstants.srvCmd_close} request.")
            }
        }
    }

    override fun getPorts(): List<Int> = MonitorConstants.serverPorts

    override fun forwardPorts() {
        this.getPorts().forEach { this.adbWrapper.forwardPort(this.deviceSerialNumber, it) }
    }
}
