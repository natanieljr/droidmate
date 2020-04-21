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
import org.droidmate.explorationModel.debugT
import org.droidmate.misc.MonitorConstants
import org.droidmate.misc.MonitorConstants.Companion.monitor_time_formatter_locale
import org.droidmate.misc.MonitorConstants.Companion.monitor_time_formatter_pattern
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.BitSet
import java.util.Date

class CoverageMonitorClient(socketTimeout: Int,
                            private val deviceSerialNumber: String,
                            private val adbWrapper: IAdbWrapper,
                            hostIp: String,
                            private val port: Int) : ICoverageMonitorClient {

    companion object {
        private val log by lazy { LoggerFactory.getLogger(CoverageMonitorClient::class.java) }
    }

    // remove this.getPorts from all methods
    private val monitorTcpClient: ITcpClientBase<String, BitSet> = TcpClientBase(hostIp, socketTimeout)


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
            this.monitorTcpClient.queryServer(MonitorConstants.srvCmd_connCheck, port)
            true
        } catch (ignored: TcpServerUnreachableException) {
            false
        }
    }

    private val monitorTimeFormatter = SimpleDateFormat(
        monitor_time_formatter_pattern,
        monitor_time_formatter_locale
    )
    private fun getNowDate(): String {
        val nowDate = Date()
        return monitorTimeFormatter.format(nowDate)
    }

    override fun getStatements(): List<List<String>> {
        return try {
            val data = debugT(
                "readStatements", {
                    monitorTcpClient.queryServer(MonitorConstants.srvCmd_get_statements, this.getPort())
                },
                inMillis = true
            )
            val result = mutableListOf<List<String>>()
            val nowDate = getNowDate()
            var i = data.nextSetBit(0)
            while (i >= 0) {

                // operate on index i here
                if (i == Int.MAX_VALUE) {
                    break // or (i+1) would overflow
                }
                result.add(listOf(nowDate, i.toString()))
                i = data.nextSetBit(i + 1)
            }
            return result
        } catch (ignored: TcpServerUnreachableException) {
            // log.trace("Did not reach monitor TCP server at port $it when sending out ${MonitorConstants.srvCmd_get_logs} request.")
            log.trace("None of the monitor TCP servers were available while obtaining API logs.")
            emptyList()
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
