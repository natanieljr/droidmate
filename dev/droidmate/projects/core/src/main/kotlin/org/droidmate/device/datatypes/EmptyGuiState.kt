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
package org.droidmate.device.datatypes

class EmptyGuiState : IGuiState {
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

    override val widgets: List<IWidget>
        get() = ArrayList()

    override val isAppHasStoppedDialogBox: Boolean
        get() = false

    override fun getActionableWidgets(): MutableList<IWidget> = ArrayList()

    override fun debugWidgets(): String = ""

    override fun belongsToApp(appPackageName: String): Boolean = false

    override val isRequestRuntimePermissionDialogBox: Boolean
        get() = false

    override val isSelectAHomeAppDialogBox: Boolean
        get() = false

    override val androidLauncherPackageName: String
        get() = this.javaClass.name
}