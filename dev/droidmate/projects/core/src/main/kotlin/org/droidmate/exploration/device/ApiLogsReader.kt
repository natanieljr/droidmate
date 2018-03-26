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
package org.droidmate.exploration.device

import org.droidmate.android_sdk.DeviceException
import org.droidmate.apis.*
import org.droidmate.device.IExplorableAndroidDevice
import org.droidmate.logging.LogbackConstants
import org.droidmate.misc.DroidmateException
import org.droidmate.misc.MonitorConstants
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * See {@link DeviceMessagesReader}
 */

class ApiLogsReader constructor(private val device: IExplorableAndroidDevice) : IApiLogsReader {
    companion object {
        private val log = LoggerFactory.getLogger(ApiLogsReader::class.java)
    }

    /**
     * <p>
     * The logs logged with the monitor logger will have different timestamps than the logged monitor logs, even though
     * {@link IDeviceTimeDiff} is applied. This is because the method that logs is executed a couple of seconds after the monitor
     * logs were logged on the device. Empirical observation shows up to 5 seconds delay for API logs.
     *
     * </p>
     */
    private val monitorLogger = LoggerFactory.getLogger(LogbackConstants.logger_name_monitor)

    @Suppress("OverridingDeprecatedMember")
    override fun getCurrentApiLogsFromLogcat(deviceTimeDiff: IDeviceTimeDiff): List<IApiLogcatMessage> {
        log.debug("getCurrentApiLogsFromLogcat(deviceTimeDiff)")
        return readApiLogcatMessages { this.getMessagesFromLogcat(deviceTimeDiff) }
    }

    override fun getAndClearCurrentApiLogsFromMonitorTcpServer(deviceTimeDiff: IDeviceTimeDiff): List<IApiLogcatMessage> {
        log.debug("getAndClearCurrentApiLogsFromMonitorTcpServer(deviceTimeDiff)")

        val logs = readApiLogcatMessages { this.getAndClearMessagesFromMonitorTcpServer(deviceTimeDiff) }

        log.debug("apiLogs# ${logs.size}")
        return logs
    }

    @Throws(DeviceException::class)
    private fun readApiLogcatMessages(messagesProvider: () -> List<ITimeFormattedLogcatMessage>): List<IApiLogcatMessage> {
        val messages = messagesProvider.invoke()

        messages.forEach { monitorLogger.trace(it.toLogcatMessageString) }

        try {
            val apiLogs = messages.map { ApiLogcatMessage.from(it) }
            val ret = ApiLogcatMessageListExtensions.sortedByTimePerPID(apiLogs)
            assert(ret)

            return apiLogs
        } catch (e: DroidmateException) {
            throw DeviceException("Failed to parse API call logs from one of the messages obtained from logcat.", e)
        }
    }

    @Throws(DeviceException::class)
    @Deprecated("Method is deprecated. It is recommended to get logs from TCP server")
    fun getMessagesFromLogcat(deviceTimeDiff: IDeviceTimeDiff): List<ITimeFormattedLogcatMessage> {
        val messages = device.readLogcatMessages(MonitorConstants.tag_api)

        return deviceTimeDiff.syncMessages(messages)
    }

    @Throws(DeviceException::class)
    private fun getAndClearMessagesFromMonitorTcpServer(deviceTimeDiff: IDeviceTimeDiff): List<ITimeFormattedLogcatMessage> {
        val messages = device.readAndClearMonitorTcpMessages()

        return extractLogcatMessagesFromTcpMessages(messages, deviceTimeDiff)
    }

    @Throws(DeviceException::class)
    private fun extractLogcatMessagesFromTcpMessages(messages: List<List<String>>, deviceTimeDiff: IDeviceTimeDiff): List<ITimeFormattedLogcatMessage> {
        return deviceTimeDiff.syncMessages(messages.map { msg ->

            val pid = msg[0]

            val deviceTime = LocalDateTime.parse(msg[1],
                    DateTimeFormatter.ofPattern(MonitorConstants.monitor_time_formatter_pattern,
                            MonitorConstants.monitor_time_formatter_locale))

            val payload = msg[2]

            TimeFormattedLogcatMessage.from(
                    deviceTime, MonitorConstants.loglevel.toUpperCase(), "[Adapted]" + MonitorConstants.tag_api, pid, payload)
        })
    }
}