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

class UiautomatorWindowDumpTestHelper {
    // TODO Fix tests
    /*companion object {


        private val deviceModel = DeviceModel.buildDefault()

        //region Fixture dumps

        @JvmStatic
        fun newEmptyWindowDump(): UiautomatorWindowDump =
                UiautomatorWindowDump("", deviceModel.getDeviceDisplayDimensionsForTesting(), deviceModel.getAndroidLauncherPackageName())

        @JvmStatic
        fun newEmptyActivityWindowDump(): UiautomatorWindowDump =
                UiautomatorWindowDump(windowDump_tsa_emptyAct, deviceModel.getDeviceDisplayDimensionsForTesting(), deviceModel.getAndroidLauncherPackageName())

        @JvmStatic
        fun newAppHasStoppedDialogWindowDump(): UiautomatorWindowDump =
                UiautomatorWindowDump(windowDump_app_stopped_dialogbox, deviceModel.getDeviceDisplayDimensionsForTesting(), deviceModel.getAndroidLauncherPackageName())

        @JvmStatic
        fun newAppHasStoppedDialogOKDisabledWindowDump(): UiautomatorWindowDump =
                UiautomatorWindowDump(windowDump_app_stopped_OK_disabled, deviceModel.getDeviceDisplayDimensionsForTesting(), deviceModel.getAndroidLauncherPackageName())

        @JvmStatic
        fun newSelectAHomeAppWindowDump(): UiautomatorWindowDump =
                UiautomatorWindowDump(windowDump_selectAHomeApp, deviceModel.getDeviceDisplayDimensionsForTesting(), deviceModel.getAndroidLauncherPackageName())


        @JvmStatic
        fun newCompleteActionUsingWindowDump(): UiautomatorWindowDump =
                UiautomatorWindowDump(windowDump_complActUsing_dialogbox, deviceModel.getDeviceDisplayDimensionsForTesting(), deviceModel.getAndroidLauncherPackageName())


        @JvmStatic
        fun newHomeScreenWindowDump(id: String = ""): UiautomatorWindowDump =
                UiautomatorWindowDump(windowDump_nexus7_home_screen, deviceModel.getDeviceDisplayDimensionsForTesting(), deviceModel.getAndroidLauncherPackageName(), id)


        @JvmStatic
        fun newAppOutOfScopeWindowDump(id: String = ""): UiautomatorWindowDump =
                UiautomatorWindowDump(windowDump_chrome_offline, deviceModel.getDeviceDisplayDimensionsForTesting(), deviceModel.getAndroidLauncherPackageName(), id)

        //endregion Fixture dumps


        @JvmStatic
        fun newWindowDump(windowHierarchyDump: String): UiautomatorWindowDump =
                UiautomatorWindowDump(windowHierarchyDump, deviceModel.getDeviceDisplayDimensionsForTesting(), deviceModel.getAndroidLauncherPackageName())


        @Suppress("unused")
        @JvmStatic
        fun newEmptyActivityWithPackageWindowDump(appPackageName: String): UiautomatorWindowDump {
            val payload = ""
            return skeletonWithPayload(topNode(appPackageName, payload))
        }

        @Suppress("unused")
        @JvmStatic
        fun new1ButtonWithPackageWindowDump(appPackageName: String): UiautomatorWindowDump =
                skeletonWithPayload(defaultButtonDump(appPackageName))

        @JvmStatic
        private fun skeletonWithPayload(payload: String, id: String = ""): UiautomatorWindowDump =
                UiautomatorWindowDump(createDumpSkeleton(payload), deviceModel.getDeviceDisplayDimensionsForTesting(), deviceModel.getAndroidLauncherPackageName(), id)

        @JvmStatic internal fun createDumpSkeleton(payload: String): String
                =
                """<?xml version = '1.0' encoding = 'UTF-8' standalone = 'yes' ?><hierarchy rotation = "0">$payload</hierarchy>"""

        @JvmStatic
        private fun topNode(appPackageName: String = apkFixture_simple_packageName, payload: String): String {
            return """<node index="0" text="" resource-uid="" class="android.widget.FrameLayout" package="$appPackageName"
content-contentDesc="" check="false" check="false" clickable="false" enabled="true" focusable="false" focus="false"
scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,0][800,1205]">$payload</node>"""
        }

        @JvmStatic
        private fun defaultButtonDump(packageName: String = apkFixture_simple_packageName): String {
            val buttonBounds = createConstantButtonBounds()
            return  createButtonDump(0, "dummyText", buttonBounds, packageName)
        }

        @JvmStatic
        private fun createConstantButtonBounds(): String {
            val x = 10
            val y = 20
            val width = 100
            val height = 200
            return "[$x,$y][${x + width},${y + height}]"
        }

        @JvmStatic
        fun dump(w: Widget): String {
            val idString = if (w.id.isNotEmpty()) "uid=\"${w.id}\"" else ""
            return """<node
 index="${w.index}"
 text="${w.text}"
 resource-uid="${w.resourceId}"
 class="${w.className}"
 package="${w.packageName}"
 content-contentDesc="${w.contentDesc}"
 check="${w.checkable}"
 check="${w.checked}"
 clickable="${w.clickable}"
 enabled="${w.enabled}"
 focusable="${w.focusable}"
 focus="${w.focused}"
 scrollable="${w.scrollable}"
 long-clickable="${w.longClickable}"
 password="${w.password}"
 selected="${w.selected}"
 bounds="${rectShortString(w.bounds)}"
 $idString
 />"""
        }

        // WISH deprecated as well as calling methods. Instead, use org.droidmate.test.device.datatypes.UiautomatorWindowDumpTestHelper.dump
        @JvmStatic
        private fun createButtonDump(index: Int, text: String, bounds: String, packageName: String = apkFixture_simple_packageName): String =
                """<node index="$index" text="$text" resource-uid="dummy.package.ExampleApp:uid/button_$text"
class="android.widget.Button" package="$packageName" content-contentDesc="" check="false" check="false"
clickable="true" enabled="true" focusable="true" focus="false" scrollable="false" long-clickable="false" password="false"
selected="false" bounds="$bounds"/>"""

        /**
         * Returns the same value as {@code android.graphics.Rect.toShortString (java.lang.StringBuilder)}
         */
        @JvmStatic
        private fun rectShortString(r: Rectangle): String {
            with(r) {
                return "[${minX.toInt()},${minY.toInt()}][${maxX.toInt()},${maxY.toInt()}]"
            }
        }

        @JvmStatic
        fun fromGuiState(guiStatus: IGuiStatus): IDeviceGuiSnapshot {
            return skeletonWithPayload(
                    guiStatus.widgets.joinToString(System.lineSeparator()) { dump(it) }, guiStatus.id)
        }
    }*/
}