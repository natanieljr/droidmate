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
import org.droidmate.deviceInterface.exploration.DeviceResponse
import org.droidmate.deviceInterface.communication.TimeFormattedLogMessageI
import org.droidmate.deviceInterface.exploration.ExplorationAction

import java.time.LocalDateTime

interface IExplorableAndroidDevice {
	@Throws(DeviceException::class)
	suspend fun hasPackageInstalled(packageName: String): Boolean

	@Throws(DeviceException::class)
	suspend fun perform(action: ExplorationAction): DeviceResponse

	@Throws(DeviceException::class)
	suspend fun readLogcatMessages(messageTag: String): List<TimeFormattedLogMessageI>

	@Throws(DeviceException::class)
	suspend fun readStatements(): List<List<String>>

	@Throws(DeviceException::class)
	suspend fun waitForLogcatMessages(messageTag: String, minMessagesCount: Int, waitTimeout: Int, queryDelay: Int): List<TimeFormattedLogMessageI>

	@Throws(DeviceException::class)
	suspend fun clearLogcat()

	@Throws(DeviceException::class)
	suspend fun readAndClearMonitorTcpMessages(): List<List<String>>

	@Throws(DeviceException::class)
	suspend fun getCurrentTime(): LocalDateTime

	@Throws(DeviceException::class)
	suspend fun anyMonitorIsReachable(): Boolean

	@Throws(DeviceException::class)
	suspend fun appIsRunning(appPackageName: String): Boolean
}

