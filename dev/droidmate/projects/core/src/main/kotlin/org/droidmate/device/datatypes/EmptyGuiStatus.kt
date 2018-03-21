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

import org.droidmate.device.datatypes.statemodel.WidgetData
import java.awt.Rectangle

class EmptyGuiStatus : IGuiStatus {
    override val deviceDisplayBounds: Rectangle
        get() = Rectangle(0,0,0,0)
    override val topNodePackageName: String
        get() = this.javaClass.name

    override val id: String
        get() = "EMPTY"

    override val isCompleteActionUsingDialogBox: Boolean
        get() = false

    override val isUseLauncherAsHomeDialogBox: Boolean
        get() = false

    override val isHomeScreen: Boolean
        get() = true

    override val widgets: List<WidgetData>
        get() = ArrayList()

    override val isAppHasStoppedDialogBox: Boolean
        get() = false

    override fun debugWidgets(): String = ""

    override fun belongsToApp(appPackageName: String): Boolean = false

    override val isRequestRuntimePermissionDialogBox: Boolean
        get() = false

    override val isSelectAHomeAppDialogBox: Boolean
        get() = false

    override val androidLauncherPackageName: String
        get() = this.javaClass.name
}