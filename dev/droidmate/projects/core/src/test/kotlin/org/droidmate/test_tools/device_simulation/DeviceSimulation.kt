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
import org.droidmate.uiautomator_daemon.IGuiStatus
import org.droidmate.uiautomator_daemon.guimodel.Action

class DeviceSimulation /*private constructor(guiScreensBuilder: IGuiScreensBuilder,
                                           override val packageName: String)*/ // TODO Fix tests
    : IDeviceSimulation {
    override val packageName: String
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun updateState(deviceAction: Action) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getCurrentGuiSnapshot(): IGuiStatus {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getCurrentLogs(): List<ITimeFormattedLogcatMessage> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val guiScreens: List<IGuiScreen>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun assertEqual(other: IDeviceSimulation) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAppIsRunning(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    // TODO Fix tests
    /*override val guiScreens: List<IGuiScreen> = guiScreensBuilder.build()
    private val initialScreen: IGuiScreen

    private var currentTransitionResult: IScreenTransitionResult? = null

    private var lastAction: Action? = null


    constructor(timeGenerator: ITimeGenerator, packageName: String, specString: String) :
            this(GuiScreensBuilderFromSpec(timeGenerator, specString, packageName), packageName)

    constructor(out: AbstractContext) :
            this(GuiScreensBuilderFromApkExplorationOutput2(out), out.packageName)

    init {
        this.initialScreen = guiScreens.single { it.getId() == GuiScreen.idHome }
    }

    override fun updateState(deviceAction: Action) {
        this.currentTransitionResult = this.getCurrentScreen().perform(deviceAction)
        this.lastAction = deviceAction
    }

    override fun getAppIsRunning(): Boolean {
        if ((this.lastAction == null) || (this.lastAction is SimulationAdbClearPackage))
            return false

        if (this.getCurrentGuiSnapshot().guiState.belongsToApp(this.packageName)) {
            assert(this.lastAction !is SimulationAdbClearPackage)
            return true
        }

        return false
    }

    override fun getCurrentGuiSnapshot(): IDeviceGuiSnapshot {
        if ((this.currentTransitionResult == null) || (this.lastAction is SimulationAdbClearPackage))
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
    }*/
}
