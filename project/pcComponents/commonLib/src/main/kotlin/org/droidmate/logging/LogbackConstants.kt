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

package org.droidmate.logging

import java.io.File
import java.nio.file.Paths


class LogbackConstants {
	companion object {

		@JvmStatic
		val LOGS_DIR_PATH = getLogsDirPath()

		@JvmStatic
		private fun getLogsDirPath(): String {
			// WISH note: logsDir has to be set on VM arg instead of normal arg. Maybe making it normal arg and then resetting
			// the config as described here [1] would help. [1]: http://logback.qos.ch/manual/configuration.html#joranDirectly
//			requireNotNull(System.getProperty("logsDir"), { "System.getProperty(\"logsDir\")='${System.getProperty("logsDir")}')" })
			val logsDir = System.getProperty("logsDir")?.let{ Paths.get(it)}
				?: Paths.get("out","logs")
			return logsDir.toString()
		}

		@JvmStatic
		fun fileAppendersUsedBeforeCleanLogsDir(): List<String> = arrayListOf(
				appender_name_master,
				appender_name_stdStreams,
				appender_name_runData
		)

		/**
		 * Denotes name of logger for logs that have been obtained from logcat from the loaded monitor class during exploration.
		 */
		val logger_name_monitor = "from monitor"

		val appender_name_monitor = "monitor.txt"

		val system_prop_stdout_loglevel = "loglevel"

		val appender_name_stdStreams = "std_streams.txt"

		val appender_name_master = "master_log.txt"

		val appender_name_warnings = "warnings.txt"

		val appender_name_runData = "run_data.txt"

		val appender_name_health = "app_health.txt"

		// WISH More exception hierarchy in the file: which exceptions came together, for which apk. E.g. Apk XYZ, Expl. Act. 150, EX1 attempt failed EX2 attempt failed E3 complete failure.
		val appender_name_exceptions = "exceptions.txt"

		val appender_name_exploration = "exploration.txt"

		val exceptions_log_path = "${LOGS_DIR_PATH}${File.separator}${appender_name_exceptions}"

		val err_log_msg = "Please see $exceptions_log_path log for details."
	}
}
