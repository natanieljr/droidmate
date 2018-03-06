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

import org.droidmate.apis.ITimeFormattedLogcatMessage
import org.droidmate.device.datatypes.AdbClearPackageAction
import org.droidmate.device.datatypes.IAndroidDeviceAction
import org.droidmate.device.datatypes.IDeviceGuiSnapshot
import org.droidmate.exploration.data_aggregators.IExplorationLog

class DeviceSimulation private constructor(guiScreensBuilder: IGuiScreensBuilder,
                                           override val packageName: String): IDeviceSimulation {

    override val guiScreens: List<IGuiScreen> = guiScreensBuilder.build()
    private val initialScreen: IGuiScreen

    private var currentTransitionResult: IScreenTransitionResult? = null

    private var lastAction: IAndroidDeviceAction? = null


    constructor(timeGenerator: ITimeGenerator, packageName: String, specString: String) :
            this(GuiScreensBuilderFromSpec(timeGenerator, specString, packageName), packageName)

    constructor(out: IExplorationLog) :
            this(GuiScreensBuilderFromApkExplorationOutput2(out), out.packageName)

    init {
        this.initialScreen = guiScreens.single { it.getId() == GuiScreen.idHome }
    }

    override fun updateState(deviceAction: IAndroidDeviceAction) {
        this.currentTransitionResult = this.getCurrentScreen().perform(deviceAction)
        this.lastAction = deviceAction
    }

    override fun getAppIsRunning(): Boolean {
        if ((this.lastAction == null) || (this.lastAction is AdbClearPackageAction))
            return false

        if (this.getCurrentGuiSnapshot().guiState.belongsToApp(this.packageName)) {
            assert(this.lastAction !is AdbClearPackageAction)
            return true
        }

        return false
    }

    override fun getCurrentGuiSnapshot(): IDeviceGuiSnapshot {
        if (this.currentTransitionResult == null)
            return this.initialScreen.getGuiSnapshot()

        return this.getCurrentScreen().getGuiSnapshot()
    }

    override fun getCurrentLogs(): List<ITimeFormattedLogcatMessage> {
        if (this.currentTransitionResult == null)
            return ArrayList()

        return this.currentTransitionResult!!.logs
    }

    private fun getCurrentScreen(): IGuiScreen {
        if (currentTransitionResult == null)
            return this.initialScreen
        else
            return this.currentTransitionResult!!.screen
    }

    override fun assertEqual(other: IDeviceSimulation) {
        assert(this.guiScreens.map { it.getId() }.sorted() == other.guiScreens.map { it.getId() }.sorted())

        this.guiScreens.forEach { thisScreen ->
            val otherScreen = other.guiScreens.single { thisScreen.getId() == it.getId() }
            assert(thisScreen.getId() == otherScreen.getId())
            assert(thisScreen.getGuiSnapshot().id == otherScreen.getGuiSnapshot().id)
            assert(thisScreen.getGuiSnapshot().guiState.widgets.map { it.id }.sorted() == otherScreen.getGuiSnapshot().guiState.widgets.map { it.id }.sorted())
        }
    }
}
