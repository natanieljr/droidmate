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

class EmptyGuiState(private val packageName: String) : IGuiState {
    override val topNodePackageName: String
        get() = packageName
    override val widgets: List<IWidget>
        get() = ArrayList()
    override val id: String
        get() = "EMPTY"
    override val androidLauncherPackageName: String
        get() = "EMPTY"

    override fun getActionableWidgets(): List<IWidget> = ArrayList()

    override val isHomeScreen: Boolean
        get() = false
    override val isAppHasStoppedDialogBox: Boolean
        get() = false
    override val isRequestRuntimePermissionDialogBox: Boolean
        get() = false
    override val isCompleteActionUsingDialogBox: Boolean
        get() = false
    override val isSelectAHomeAppDialogBox: Boolean
        get() = false
    override val isUseLauncherAsHomeDialogBox: Boolean
        get() = false

    override fun belongsToApp(appPackageName: String): Boolean = false

    override fun debugWidgets(): String = ""
}