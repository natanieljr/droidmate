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

import org.droidmate.device.error.DeviceException
import org.droidmate.device.deviceInterface.IRobustDevice
import org.droidmate.errors.ForbiddenOperationError
import java.util.*

class DeviceLogsHandler constructor(val device: IRobustDevice) : IDeviceLogsHandler {

	companion object {
		private val uiThreadId = "1"
	}

	private var gotLogs = false

	private var logs: MutableList<IApiLogcatMessage> = mutableListOf()

	override suspend fun readAndClearApiLogs() {
		val apiLogs = _readAndClearApiLogs()
		addApiLogs(apiLogs)
	}

	override suspend fun readClearAndAssertOnlyBackgroundApiLogsIfAny() {
		val apiLogs = _readAndClearApiLogs()
		assert(this.logs.all { it.threadId != uiThreadId })

		addApiLogs(apiLogs)
	}

	private fun addApiLogs(apiLogs: List<IApiLogcatMessage>) {
		if (this.logs.isEmpty())
			this.logs = LinkedList()

		if (this.logs.isNotEmpty() && apiLogs.isNotEmpty())
			assert(this.logs.last().time <= apiLogs.first().time)

		this.logs.addAll(apiLogs.sortedBy { it.toString() })
	}

	override fun getLogs(): MutableList<IApiLogcatMessage> {
		if (gotLogs)
			throw ForbiddenOperationError()
		this.gotLogs = true
		return this.logs
	}

	@Throws(DeviceException::class)
	private suspend fun _readAndClearApiLogs(): List<IApiLogcatMessage> = device.getAndClearCurrentApiLogs()
}
