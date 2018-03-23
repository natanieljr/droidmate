// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Konrad Jamrozik
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

package org.droidmate.uiautomator_daemon

import java.io.Serializable

/**
 * this is used to describe the device state after any device action
 * in particular DroidMate distinguishes what screen is reached
 * however it is (and should only) be used to parse the device data (i.e. the windowDump to WidgetData)
 * any 'real' state is to be created in ExplorationContext
 */
interface IGuiStatus : Serializable {
    val windowHierarchyDump: String

    val topNodePackageName: String
    val widgets: List<WidgetData>
    val androidLauncherPackageName: String  //TODO check if this is required at all
    // Originally used java.awt.Rectangle, but Swing classes are not available on Android
    val deviceDisplayWidth: Int
    val deviceDisplayHeight: Int

    val isHomeScreen: Boolean

    val isAppHasStoppedDialogBox: Boolean

    val isRequestRuntimePermissionDialogBox: Boolean

    val isCompleteActionUsingDialogBox: Boolean

    val isSelectAHomeAppDialogBox: Boolean

    val isUseLauncherAsHomeDialogBox: Boolean

    fun belongsToApp(appPackageName: String): Boolean

    fun debugWidgets(): String
}