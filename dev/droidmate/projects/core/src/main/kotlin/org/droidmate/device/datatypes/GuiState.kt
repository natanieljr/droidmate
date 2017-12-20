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

package org.droidmate.device.datatypes

open class GuiState constructor(final override val topNodePackageName: String,
                                override val id: String,
                                override val widgets: List<IWidget>,
                                final override val androidLauncherPackageName: String) : IGuiState {
    companion object {
        private const val serialVersionUID: Long = 1
    }

    private val androidPackageName = "android"
    private val resIdRuntimePermissionDialog = "com.android.packageinstaller:id/dialog_container"


    init {
        assert(!this.topNodePackageName.isEmpty())
        assert(!this.androidLauncherPackageName.isEmpty())
    }

    override fun getActionableWidgets(): List<IWidget> = widgets.filter { it.canBeActedUpon() }

    override fun toString(): String {

        if (this.isHomeScreen)
            return "<GUI state: home screen>"

        if (this is AppHasStoppedDialogBoxGuiState)
            return "<GUI state of \"App has stopped\" dialog box. OK widget enabled: ${this.okWidget.enabled}>"

        if (this is RuntimePermissionDialogBoxGuiState)
            return "<GUI state of \"Runtime permission\" dialog box. Allow widget enabled: ${this.allowWidget.enabled}>"

        return "<GuiState " + (if (id.isNotEmpty()) "id=$id " else "") + "pkg=$topNodePackageName Widgets count = ${widgets.size}>"
    }

    override val isHomeScreen: Boolean
        get() = topNodePackageName == androidLauncherPackageName && !widgets.any { it.text == "Widgets" }

    override val isAppHasStoppedDialogBox: Boolean
        get() = topNodePackageName == androidPackageName &&
                widgets.any { it.text == "OK" } &&
                !widgets.any { it.text == "Just once" }

    override val isCompleteActionUsingDialogBox: Boolean
        get() = !isSelectAHomeAppDialogBox &&
                !isUseLauncherAsHomeDialogBox &&
                topNodePackageName == androidPackageName &&
                widgets.any { it.text == "Just once" }

    override val isSelectAHomeAppDialogBox: Boolean
        get() = topNodePackageName == androidPackageName &&
                widgets.any { it.text == "Just once" } &&
                widgets.any { it.text == "Select a Home app" }

    override val isUseLauncherAsHomeDialogBox: Boolean
        get() = topNodePackageName == androidPackageName &&
                widgets.any { it.text == "Use Launcher as Home" } &&
                widgets.any { it.text == "Just once" } &&
                widgets.any { it.text == "Always" }

    override val isRequestRuntimePermissionDialogBox: Boolean
        get() = widgets.any { it.resourceId == resIdRuntimePermissionDialog }

    override fun belongsToApp(appPackageName: String): Boolean = this.topNodePackageName == appPackageName

    override fun debugWidgets(): String {

        val sb = StringBuilder()
        sb.appendln("widgets (${widgets.size}):")
        widgets.forEach { sb.appendln(it.toString()) }

        val actionableWidgets = this.getActionableWidgets()
        sb.appendln("actionable widgets (${actionableWidgets.size}):")
        actionableWidgets.forEach { sb.appendln(it.toString()) }

        return sb.toString()
    }
}
