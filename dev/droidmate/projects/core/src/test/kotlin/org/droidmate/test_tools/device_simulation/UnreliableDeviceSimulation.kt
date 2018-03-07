// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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
package org.droidmate.test_tools.device_simulation

import org.droidmate.device.datatypes.*
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.test_tools.device.datatypes.IUnreliableDeviceGuiSnapshotProvider
import org.droidmate.test_tools.device.datatypes.UnreliableDeviceGuiSnapshotProvider
import org.droidmate.uiautomator_daemon.guimodel.*

class UnreliableDeviceSimulation(timeGenerator: ITimeGenerator,
                                 packageName: String,
                                 specString: String,
                                 private val simulation : IDeviceSimulation = DeviceSimulation(timeGenerator, packageName, specString)) : IDeviceSimulation by simulation {
    private var unreliableGuiSnapshotProvider: IUnreliableDeviceGuiSnapshotProvider

    init {
        this.unreliableGuiSnapshotProvider = UnreliableDeviceGuiSnapshotProvider(this.simulation.getCurrentGuiSnapshot())
    }

    override fun updateState(deviceAction: Action) {
        // WISH later on support for failing calls to AndroidDevice.clearPackage would be nice. Currently,
        // org.droidmate.test_tools.device_simulation.UnreliableDeviceSimulation.transitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(Action)
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

    private fun transitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(action: Action) {
        when (action) {
            is LaunchApp -> failWithForbiddenActionOnInvalidGuiSnapshot(action)
            is SimulationAdbClearPackage -> this.simulation.updateState(action)
            is ClickAction -> onTransitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(action)
            is CoordinateClickAction -> onTransitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(action)
            is LongClickAction -> onTransitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(action)
            is CoordinateLongClickAction -> onTransitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(action)
            else -> throw UnexpectedIfElseFallthroughError()
        }
    }

    private fun failWithForbiddenActionOnInvalidGuiSnapshot(action: Action) {
        assert(
                false, {
            "DroidMate attempted to perform a device action that is forbidden while the device displays " +
                    "invalid GUI snapshot or GUI snapshot with 'app has stopped' dialog box. The action: $action"
        }
        )
    }

    private fun onTransitionClickGuiActionOnInvalidOrAppHasStoppedDialogBoxSnapshot(action: Action) {
        if (this.unreliableGuiSnapshotProvider.getCurrentWithoutChange().guiState.isAppHasStoppedDialogBox) {
            val appHasStopped = this.unreliableGuiSnapshotProvider.getCurrentWithoutChange().guiState as AppHasStoppedDialogBoxGuiState
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
                "DroidMate attempted to perform a click while the device displays an invalid GUI snapshot that is " +
                        "not 'app has stopped' dialog box. The forbidden action: $action"
            })
        }
    }
}
