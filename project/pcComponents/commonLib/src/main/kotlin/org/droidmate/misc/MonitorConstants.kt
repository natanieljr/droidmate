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

package org.droidmate.misc

import java.util.*

class MonitorConstants {
	companion object {

		@JvmStatic
		val tag_api = "Monitor_API_method_call"

		@JvmStatic
		val tag_prefix = "droidmate/monit/"
		@JvmStatic
		val tag_srv = tag_prefix + "server"
		@JvmStatic
		val tag_run = tag_prefix + "srv_run"
		// mjt == MonitorJavaTemplate
		@JvmStatic
		val tag_mjt = tag_prefix + "mjt"

		@JvmStatic
		val loglevel = "i"
		@JvmStatic
		val msg_ctor_start = "ctor(): entering"
		@JvmStatic
		val msg_ctor_success = "ctor(): startMonitorTCPServer(): SUCCESS port: "
		@JvmStatic
		val msg_ctor_failure = "! ctor(): startMonitorTCPServer(): FAILURE"

		/**
		 * <p>
		 * Example full message:
		 * </p><p>
		 * {@code Monitor initialized for package org.droidmate.fixtures.apks.monitored}
		 * </p>
		 */
		@JvmStatic
		val msgPrefix_init_success = "init(): SUCCESS for package "
		@JvmStatic
		val srvCmd_connCheck = "connCheck"
		@JvmStatic
		val srvCmd_get_logs = "getLogs"
		@JvmStatic
		val srvCmd_get_statements = "getStatements"
		@JvmStatic
		val srvCmd_get_time = "getTime"
		@JvmStatic
		val srvCmd_close = "close"

		@JvmStatic
		val monitor_time_formatter_pattern = "yyyy-MM-dd HH:mm:ss.SSS"
		// !!! DUPLICATION WARNING !!! with org.droidmate.buildsrc.locale
		// EnvironmentConstants.getLocale() is not used here as monitor_time_formatter_locale is used in android device.
		//public static final Locale monitor_time_formatter_locale  = EnvironmentConstants.getLocale();
		@JvmStatic
		val monitor_time_formatter_locale: Locale = Locale.US
	}
}
