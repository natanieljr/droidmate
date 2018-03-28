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

import org.droidmate.device.android_sdk.DeviceException
import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.deviceInterface.DeviceLogsHandler
import org.droidmate.device.deviceInterface.IRobustDevice
import org.slf4j.LoggerFactory

import java.time.LocalDateTime

class RunnableWaitForWidget(private val action: WaitA, timestamp: LocalDateTime, takeScreenShot: Boolean)
	: RunnableExplorationAction(action, timestamp, takeScreenShot) {

	@Throws(DeviceException::class)
	override fun performDeviceActions(app: IApk, device: IRobustDevice) {
		val log = LoggerFactory.getLogger(this.javaClass)
		val logsHandler = DeviceLogsHandler(device)

		log.debug("1. Wait for the widget (until load-screen is finished)")
		device.perform(this.action.action)

		log.debug("2. Read and clear API logs if any, then seal logs reading.")
		logsHandler.readAndClearApiLogs()
		this.logs = logsHandler.getLogs()


		/*if (this.takeScreenshot) {
			log.debug("3. Get GUI screenshot.")
			val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS")
			val screenshotsPath = device.takeScreenshot(app, timestamp.format(formatter) + "__WAIT")
			this.screenshot = screenshotsPath.toUri()
		}*/

		log.debug("4. Get GUI snapshot.")
		this.snapshot = device.getGuiSnapshot()
	}

	companion object {
		private const val serialVersionUID = 4343512671602419674L
	}

}
