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

package org.droidmate.device.logcat

import org.droidmate.device.apis.Api
import org.droidmate.misc.MonitorConstants

import java.time.LocalDateTime

@Suppress("unused") // Actually used in org.droidmate.exploration.data_aggregators.ExplorationOutput2Builder.buildDeviceLogs
class ApiLogcatMessageTestHelper {
	companion object {
		@JvmStatic
		val log_level_for_testing = "I"

		@Suppress("UNCHECKED_CAST")
		@JvmStatic
		fun newApiLogcatMessage(apiAttributes: MutableMap<String, Any>): IApiLogcatMessage {
			val time = apiAttributes.remove("time") as LocalDateTime?
			apiAttributes["stackTrace"] = apiAttributes["stackTrace"] ?: "$Api.monitorRedirectionPrefix"

			val objectClass: String = apiAttributes["objectClass"].toString()
			val methodName: String = apiAttributes["methodName"].toString()
			val returnClass: String = apiAttributes["returnClass"].toString()
			val paramTypes: List<String> = apiAttributes["paramTypes"] as List<String>
			val paramValues: List<String> = apiAttributes["paramValues"] as List<String>
			val threadId: String = apiAttributes["threadId"].toString()
			val stackTrace: String = apiAttributes["stackTrace"].toString()

			val logcatMessage = TimeFormattedLogcatMessage.from(
					time ?: TimeFormattedLogcatMessage.assumedDate,
					log_level_for_testing,
					MonitorConstants.tag_api,
					"3993", // arbitrary process ID
					ApiLogcatMessage.toLogcatMessagePayload(Api(objectClass, methodName, returnClass, paramTypes, paramValues, threadId, stackTrace))
			)

			return ApiLogcatMessage.from(logcatMessage)
		}
	}
}
