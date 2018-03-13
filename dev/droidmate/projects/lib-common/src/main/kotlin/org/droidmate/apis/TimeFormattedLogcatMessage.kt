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

package org.droidmate.apis

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 *
 * <p>
 * Represents a string that was obtained by reading a line of logcat output formatted with "-v time"
 * </p><p>
 *
 * Reference: http://developer.android.com/tools/debugging/debugging-log.html#outputFormat
 * </p>
 */
class TimeFormattedLogcatMessage private constructor(override val time: LocalDateTime,
                                                     override val level: String,
                                                     override val tag: String,
                                                     override val pidString: String,
                                                     override val messagePayload: String) : ITimeFormattedLogcatMessage {
    companion object {
        private const val serialVersionUID: Long = 1

        @JvmStatic
        public val assumedDate: LocalDateTime = LocalDateTime.now()
        private val rawMessageTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
        private val withYearMessageTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        @JvmStatic
        fun from(time: LocalDateTime, level: String, tag: String, pidString: String, messagePayload: String): ITimeFormattedLogcatMessage
                = TimeFormattedLogcatMessage(time, level, tag, pidString, messagePayload)

        /**
         * <p>
         * Parses logcat message in format of IntelliJ's "Android" logcat window.
         *
         * </p><p>
         * Example logs taken from IntelliJ IDEA "Android" logcat window:
         *
         * <pre>
         * <code>02-04 21:54:52.600  19183-19183/? V/UIEventsToLogcat﹕ text: Video qualityAutomatic package: com.snapchat.android class: android.widget.LinearLayout type: TYPE_VIEW_CLICKED time: 10479703
         * 02-05 12:39:39.261  26443-26443/? I/Instrumentation﹕ Redirected org.apache.http.impl.client.AbstractHttpClient-><init>
         * 02-05 12:39:39.261  26443-26443/? I/Monitor﹕ Monitor initialized for package com.snapchat.android
         * 02-05 12:39:39.511  26443-26443/? I/Monitored_API_method_call﹕ objCls: android.net.ConnectivityManager mthd: getActiveNetworkInfo retCls: android.net.NetworkInfo params:  stacktrace: long_stack_trace

         * </code></pre>
         * </p>
         */
        @JvmStatic
        @Suppress("unused", "UNUSED_PARAMETER")
        fun fromIntelliJ(logcatMessage: String): ITimeFormattedLogcatMessage = throw NotImplementedError()

        /**
         * <p>
         * Parses {@code logcatMessage} being in standard Android format.
         *
         * </p><p>
         * Example logs taken from adb logcat output:
         *
         * <pre>
         * <code>12-22 20:30:01.440 I/PMBA    (  483): Previous metadata 937116 mismatch vs 1227136 - rewriting
         * 12-22 20:30:01.500 D/BackupManagerService(  483): Now staging backup of com.android.vending
         * 12-22 20:30:34.190 D/dalvikvm( 1537): GC_CONCURRENT freed 390K, 5% free 9132K/9572K, paused 17ms+5ms, total 65ms</code></pre>
         *
         * </p><p>
         * Reference: http://developer.android.com/tools/debugging/debugging-log.html#outputFormat
         *
         * </p>
         */
        @JvmStatic
        fun from(logcatMessage: String): ITimeFormattedLogcatMessage {
            assert(logcatMessage.isNotEmpty())
            assert("\\d\\d-\\d\\d".toRegex().find(logcatMessage) != null,
                    {
                        "Failed parsing logcat message. Was expecting to see \"MM-DD \" at the beginning, " +
                                "where M denotes Month digit and D denotes day-of-month digit.\n" +
                                "The offending logcat message:\n\n$logcatMessage\n\n"
                    })

            var data = logcatMessage.split(" ".toRegex(), 3)
            val monthAndDay: String = data[0]
            val hourMinutesSecondsMillis: String = data[1]
            var notYetParsedMessagePart: String = data[2]

            val year = assumedDate.year.toString()

            val time = LocalDateTime.parse("$year-$monthAndDay $hourMinutesSecondsMillis", withYearMessageTimeFormatter)

            data = notYetParsedMessagePart.split("/".toRegex(), 2)
            val logLevel = data[0]
            notYetParsedMessagePart = data[1]

            // On this split we make an implicit assumption that the '(' character (i.e. left parenthesis) doesn't appear in logTag.
            data = notYetParsedMessagePart.split("\\(".toRegex(), 2)
            val logTag = data[0]
            notYetParsedMessagePart = data[1]

            data = notYetParsedMessagePart.split("\\)".toRegex(), 2)
            val pidString = data[0]
            notYetParsedMessagePart = data[1]

            assert(notYetParsedMessagePart.startsWith(": "))
            val messagePayload = notYetParsedMessagePart.drop(2)

            arrayListOf(logLevel, logTag, pidString).forEach { assert(it.isNotEmpty()) }
            return TimeFormattedLogcatMessage(time, logLevel, logTag, pidString, messagePayload)
        }
    }

    override val toLogcatMessageString: String
        get() = "${time.format(rawMessageTimeFormatter)} $level/$tag($pidString): $messagePayload"

    override fun toString(): String {
        val out = toLogcatMessageString
        return if (out.length <= 256)
            out
        else
            out.substring(0, 256) + "... (truncated to 256 chars)"
    }
}
