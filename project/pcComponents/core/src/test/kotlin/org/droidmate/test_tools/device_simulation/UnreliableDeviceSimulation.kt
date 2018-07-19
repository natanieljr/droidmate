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
package org.droidmate.test_tools.device_simulation

import org.droidmate.apis.ITimeFormattedLogcatMessage
import org.droidmate.deviceInterface.DeviceResponse
import org.droidmate.deviceInterface.guimodel.ExplorationAction

class UnreliableDeviceSimulation /*(timeGenerator: ITimeGenerator,
                                 packageName: String,
                                 specString: String,
                                 private val simulation: IDeviceSimulation = DeviceSimulation(timeGenerator, packageName, specString))*/ // TODO Fix tests
	: IDeviceSimulation /*by simulation*/ // TODO Fix tests
{
	override fun updateState(deviceAction: ExplorationAction) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun getCurrentGuiSnapshot(): DeviceResponse {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun getCurrentLogs(): List<ITimeFormattedLogcatMessage> {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override val packageName: String
		get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
	override val guiScreens: List<IGuiScreen>
		get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

	override fun assertEqual(other: IDeviceSimulation) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun getAppIsRunning(): Boolean {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}
	// TODO Fix tests
	/*private var unreliableGuiSnapshotProvider: IUnreliableDeviceGuiSnapshotProvider

	init {
			this.unreliableGuiSnapshotProvider = UnreliableDeviceGuiSnapshotProvider(this.simulation.getCurrentGuiSnapshot())
	}

	override fun updateState(deviceAction: ExplorationAction) {
			// WISH later on support for failing calls to AndroidDevice.clearPackage would be nice. Currently,
			// org.droidmate.test_tools.device_simulation.UnreliableDeviceSimulation.transitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(ExplorationAction)
			// just updates state of the underlying simulation and that's it.

			if (this.unreliableGuiSnapshotProvider.getCurrentWithoutChange().validationResult.valid
							&& !(this.unreliableGuiSnapshotProvider.getCurrentWithoutChange().guiState.isAppHasStoppedDialogBox)
							) {
					this.simulation.updateState(deviceAction)
					this.unreliableGuiSnapshotProvider = UnreliableDeviceGuiSnapshotProvider(this.simulation.getCurrentGuiSnapshot())
			} else {
					transitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(deviceAction)
			}
	}

	override fun getAppIsRunning(): Boolean {
			val gs = this.unreliableGuiSnapshotProvider.getCurrentWithoutChange()
			return if (gs.validationResult.valid && gs.guiState.isAppHasStoppedDialogBox)
					false
			else
					this.simulation.getAppIsRunning()
	}

	override fun getCurrentGuiSnapshot(): IDeviceGuiSnapshot = this.unreliableGuiSnapshotProvider.provide()

	private fun transitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(action: ExplorationAction) {
			when (action) {
					is LaunchAppAction -> failWithForbiddenActionOnInvalidGuiSnapshot(action)
					is SimulationAdbClearPackageAction -> this.simulation.updateState(action)
					is ClickAction -> onTransitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(action)
					is CoordinateClickAction -> onTransitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(action)
					is LongClickAction -> onTransitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(action)
					is CoordinateLongClickAction -> onTransitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(action)
					else -> throw UnexpectedIfElseFallthroughError()
			}
	}

	private fun failWithForbiddenActionOnInvalidGuiSnapshot(action: ExplorationAction) {
			assert(
							false, {
					"DroidMate attempted to perform a device action that is forbidden while the device displays " +
									"empty GUI snapshot or GUI snapshot with 'app has stopped' dialog box. The action: $action"
			}
			)
	}

	private fun onTransitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(action: ExplorationAction) {
			if (this.unreliableGuiSnapshotProvider.getCurrentWithoutChange().guiState.isAppHasStoppedDialogBox) {
					val appHasStopped = this.unreliableGuiSnapshotProvider.getCurrentWithoutChange().guiState as AppHasStoppedDialogBoxGuiStatus
					val singleMatchingWiddget = if (action is ClickAction)
							GuiScreen.getSingleMatchingWidget(action, appHasStopped.getActionableWidgets())
					else if (action is CoordinateClickAction)
							GuiScreen.getSingleMatchingWidget(action, appHasStopped.getActionableWidgets())
					else
							throw UnexpectedIfElseFallthroughError()

					assert(singleMatchingWiddget == appHasStopped.okWidget,
									{ "DroidMate attempted to click on 'app has stopped' dialog box on a widget different than 'OK'. The action: $action" })

					this.unreliableGuiSnapshotProvider.pressOkOnAppHasStopped()

			} else {
					assert(false, {
							"DroidMate attempted to perform a click while the device displays an empty GUI snapshot that is " +
											"not 'app has stopped' dialog box. The forbidden action: $action"
					})
			}
	}*/
}