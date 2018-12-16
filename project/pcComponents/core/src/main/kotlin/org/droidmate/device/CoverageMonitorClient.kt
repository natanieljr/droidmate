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

import org.droidmate.device.android_sdk.IAdbWrapper
import org.droidmate.misc.MonitorConstants
import org.slf4j.LoggerFactory
import java.util.LinkedList

class CoverageMonitorClient(socketTimeout: Int,
                            private val deviceSerialNumber: String,
                            private val adbWrapper: IAdbWrapper,
                            private val port: Int) : ICoverageMonitorClient {

    companion object {
        private val log by lazy { LoggerFactory.getLogger(CoverageMonitorClient::class.java) }
    }

    // remove this.getPorts from all methods
    private val monitorTcpClient: ITcpClientBase<String, LinkedList<ArrayList<String>>> = TcpClientBase(socketTimeout)


    override fun anyMonitorIsReachable(): Boolean {
        val out = this.isServerReachable(this.getPort())
        if (out)
            log.trace("The monitor is reachable.")
        else
            log.trace("No monitor is reachable.")
        return out
    }

    private fun isServerReachable(port: Int): Boolean {
        return try {
            val out = this.monitorTcpClient.queryServer(MonitorConstants.srvCmd_connCheck, port)
            val diagnostics = out.single()

            assert(diagnostics.size >= 2)
            val pid = diagnostics[0]
            val packageName = diagnostics[1]
            log.trace("Reached server at port $port. PID: $pid package: $packageName")
            true
        } catch (ignored: TcpServerUnreachableException) {
            false
        }
    }

    override fun getStatements(): List<List<String>> {
        return try {
            monitorTcpClient.queryServer(MonitorConstants.srvCmd_get_statements, this.getPort())
        } catch (ignored: TcpServerUnreachableException) {
            // log.trace("Did not reach monitor TCP server at port $it when sending out ${MonitorConstants.srvCmd_get_logs} request.")
            log.trace("None of the monitor TCP servers were available while obtaining API logs.")
            ArrayList()
        }
    }

    override fun closeMonitorServers() {
        try {
            monitorTcpClient.queryServer(MonitorConstants.srvCmd_close, this.getPort())
        } catch (ignored: TcpServerUnreachableException) {
            // log.trace("Did not reach monitor TCP server at port $it when sending out ${MonitorConstants.srvCmd_close} request.")
        }
    }

    override fun getPort(): Int = port

    override fun forwardPorts() {
        this.adbWrapper.forwardPort(this.deviceSerialNumber, this.getPort())
    }
}
