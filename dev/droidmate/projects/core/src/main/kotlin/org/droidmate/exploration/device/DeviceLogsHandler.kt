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
package org.droidmate.exploration.device

import org.droidmate.android_sdk.DeviceException
import org.droidmate.apis.IApiLogcatMessage
import org.droidmate.errors.ForbiddenOperationError
import java.util.*

class DeviceLogsHandler constructor(val device: IRobustDevice) : IDeviceLogsHandler {

    companion object {
        private val uiThreadId = "1"
    }

    private var gotLogs = false

    val logs = DeviceLogs()

    override fun readAndClearApiLogs() {
        val apiLogs = _readAndClearApiLogs()
        addApiLogs(apiLogs)
    }

    override fun readClearAndAssertOnlyBackgroundApiLogsIfAny() {
        val apiLogs = _readAndClearApiLogs()
        assert(this.logs.apiLogs.all { it.threadId != uiThreadId })

        addApiLogs(apiLogs)
    }

    private fun addApiLogs(apiLogs: List<IApiLogcatMessage>) {
        if (this.logs.apiLogs.isEmpty())
            this.logs.apiLogs = LinkedList()

        if (this.logs.apiLogs.isNotEmpty() && apiLogs.isNotEmpty())
            assert(this.logs.apiLogs.last().time <= apiLogs.first().time)

        this.logs.apiLogs.addAll(apiLogs.sortedBy { it.toString() })
    }

    override fun getLogs(): IDeviceLogs {
        if (gotLogs)
            throw ForbiddenOperationError()
        this.gotLogs = true
        return this.logs
    }

    @Throws(DeviceException::class)
    private fun _readAndClearApiLogs(): List<IApiLogcatMessage> = device.getAndClearCurrentApiLogs()
}
