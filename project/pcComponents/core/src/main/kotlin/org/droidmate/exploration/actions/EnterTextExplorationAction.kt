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
import org.droidmate.exploration.statemodel.Widget
import org.droidmate.uiautomator_daemon.WidgetData
import org.droidmate.uiautomator_daemon.guimodel.TextAction

open class EnterTextExplorationAction constructor(val textToEnter: String,
                                                  override val widget: Widget) : AbstractExplorationAction() {
	constructor( textToEnter: String, resId: String, xPath: String = ""): this(textToEnter,Widget(WidgetData(resId = resId, xPath = xPath)))

	companion object {
		private const val serialVersionUID: Long = 1
	}

	override fun performDeviceActions(app: IApk, device: IRobustDevice) {
		log.debug("1. Assert only background API logs are present, if any.")
		val logsHandler = DeviceLogsHandler(device)
		logsHandler.readClearAndAssertOnlyBackgroundApiLogsIfAny()

		//FIXME: In some cases the setting of text does open the keyboard and is hiding some widgets
		// 					but these widgets are still in the uiautomator dump. Therefore it may be that droidmate
		//					clicks on the keyboard thinking it clicked one of the widgets below it.
		//				  http://stackoverflow.com/questions/17223305/suppress-keyboard-after-setting-text-with-android-uiautomator
		//					-> It seems there is no reliable way to suppress the keyboard.
		log.debug("2. Perform widget text input: ${this::class.simpleName}")
		this.snapshot = device.perform(TextAction(widget.xpath,	widget.resourceId, textToEnter))

		log.debug("3. Read and clear API logs if any, then seal logs reading.")
		logsHandler.readAndClearApiLogs()
		this.logs = logsHandler.getLogs()
	}


	override fun toShortString(): String {
		val paddedTextToEnter = textToEnter.padEnd(22, ' ')
		return "EnterTxt: $paddedTextToEnter / resourceId: ${widget.resourceId} / xpath: ${widget.xpath}"
	}

	override fun toTabulatedString(): String {
		val paddedTextToEnter = textToEnter.padEnd(22, ' ')
		return "EnterTxt: $paddedTextToEnter / " + widget.toTabulatedString(false)
	}
}
