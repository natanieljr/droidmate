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

package org.droidmate.test_tools.device.datatypes

import org.droidmate.device.datatypes.GuiState
import org.droidmate.device.datatypes.IGuiState
import org.droidmate.device.model.DeviceModel
import org.droidmate.test_tools.ApkFixtures.Companion.apkFixture_simple_packageName
import org.droidmate.test_tools.device.datatypes.WidgetTestHelper.Companion.newTopLevelWidget

class GuiStateTestHelper
{

    companion object {
        @JvmStatic
        @JvmOverloads
        @Suppress("unused")
        fun newEmptyGuiState(appPackageName: String = apkFixture_simple_packageName, id: String = ""): IGuiState =
                GuiState(appPackageName, id, ArrayList(), DeviceModel.buildDefault().getAndroidLauncherPackageName())

        @JvmStatic
        @JvmOverloads
        fun newGuiStateWithTopLevelNodeOnly(appPackageName: String = apkFixture_simple_packageName, id: String = ""): IGuiState
                =
                GuiState(appPackageName, id, arrayListOf(newTopLevelWidget(appPackageName)), DeviceModel.buildDefault().getAndroidLauncherPackageName())

        @JvmStatic
        fun newGuiStateWithDisabledWidgets(widgetCount: Int): IGuiState
                = newGuiStateWithWidgets(widgetCount, apkFixture_simple_packageName, false)

        @JvmStatic
        @JvmOverloads
        fun newGuiStateWithWidgets(widgetCount: Int,
                                   packageName: String = apkFixture_simple_packageName,
                                   enabled: Boolean = true,
                                   guiStateId: String = "",
                                   widgetIds: List<String> = ArrayList()): IGuiState {
            assert(widgetCount >= 0, { "Widget count cannot be zero. To create GUI state without widgets, call newEmptyGuiState()" })
            assert(widgetIds.isEmpty() || widgetIds.size == widgetCount)

            val gs = GuiState(packageName, guiStateId, WidgetTestHelper.newWidgets(
                    widgetCount,
                    packageName,
                    mapOf(
                            "idsList" to widgetIds,
                            "enabledList" to (0 until widgetCount).map { enabled }
                    ),
                    if (guiStateId.isEmpty()) guiStateId else getNextGuiStateName()),
                    DeviceModel.buildDefault().getAndroidLauncherPackageName())
            assert(gs.widgets.all { it.packageName == gs.topNodePackageName })
            return gs
        }

        @JvmStatic
        fun newAppHasStoppedGuiState(): IGuiState
                = UiautomatorWindowDumpTestHelper.newAppHasStoppedDialogWindowDump().guiState

        @JvmStatic
        fun newCompleteActionUsingGuiState(): IGuiState
                = UiautomatorWindowDumpTestHelper.newCompleteActionUsingWindowDump().guiState


        @JvmStatic
        fun newHomeScreenGuiState(): IGuiState
                = UiautomatorWindowDumpTestHelper.newHomeScreenWindowDump().guiState

        @JvmStatic
        fun newOutOfAppScopeGuiState(): IGuiState
                = UiautomatorWindowDumpTestHelper.newAppOutOfScopeWindowDump().guiState

        @JvmStatic
        var nextGuiStateIndex = 0

        @JvmStatic
        private fun getNextGuiStateName(): String {
            nextGuiStateIndex++
            return "GS$nextGuiStateIndex"
        }
    }
}
