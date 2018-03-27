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
package org.droidmate.exploration.actions

import org.droidmate.android_sdk.IApk
import org.droidmate.exploration.device.DeviceLogsHandler
import org.droidmate.exploration.device.IRobustDevice

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RunnableTerminateExplorationAction(action: TerminateExplorationAction, timestamp: LocalDateTime, takeScreenshot: Boolean)
	: RunnableExplorationAction(action, timestamp, takeScreenshot) {

	companion object {
		private const val serialVersionUID: Long = 1
	}

	override fun performDeviceActions(app: IApk, device: IRobustDevice) {
		log.debug("1. Read background API logs, if any.")
		val logsHandler = DeviceLogsHandler(device)
		logsHandler.readClearAndAssertOnlyBackgroundApiLogsIfAny()
		this.logs = logsHandler.getLogs()

		/*log.debug("2. Take a screenshot.")
		val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS")
		this.screenshot = device.takeScreenshot(app, timestamp.format(formatter)).toUri()*/

		log.debug("3. Close monitor servers, if any.")
		device.closeMonitorServers()

		log.debug("4. Clear package ${app.packageName}}.")
		device.clearPackage(app.packageName)

		log.debug("5. Assert app is not running.")
		assertAppIsNotRunning(device, app)

		log.debug("6. Ensure home screen is displayed.")
		this.snapshot = device.ensureHomeScreenIsDisplayed()

	}
}