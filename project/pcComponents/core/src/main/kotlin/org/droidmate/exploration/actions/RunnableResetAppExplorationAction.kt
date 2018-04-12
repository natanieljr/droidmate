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

import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.deviceInterface.DeviceLogsHandler
import org.droidmate.device.deviceInterface.IRobustDevice
import org.droidmate.uiautomator_daemon.guimodel.EnableWifi
import java.time.LocalDateTime

class RunnableResetAppExplorationAction(action: ResetAppExplorationAction, timestamp: LocalDateTime)
	: RunnableExplorationAction(action, timestamp) {
	companion object {
		private const val serialVersionUID: Long = 1
	}

	override fun performDeviceActions(app: IApk, device: IRobustDevice) {
		log.debug("1. Clear package ${app.packageName}.")

		device.clearPackage(app.packageName)

		log.debug("2. Clear logcat.")
		// This is made to clean up the logcat if previous app exploration failed. If the clean would not be made, it might be
		// possible some API logs will be read from it, wreaking all kinds of havoc, e.g. having timestamp < than the current
		// exploration start time.
		device.clearLogcat()

		log.debug("3. Ensure home screen is displayed.")
		device.ensureHomeScreenIsDisplayed()

		log.debug("4. Turn wifi on.")
		device.perform(EnableWifi())

		log.debug("5. Ensure app is not running.")
		if (device.appIsRunning(app.packageName)) {
			log.trace("App is still running. Clearing package again.")
			device.clearPackage(app.packageName)
		}

		log.debug("6. Launch app $app.packageName.")
		this.snapshot = device.launchApp(app)

		log.debug("7. Try to read API logs.")
		val logsHandler = DeviceLogsHandler(device)
		logsHandler.readAndClearApiLogs()
		this.logs = logsHandler.getLogs()

	}
}

