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

package org.droidmate.uiautomator_daemon

import org.slf4j.LoggerFactory

class GuiStatusResponse(windowHierarchyDump: String,
                        deviceModel: String,
                        displayWidth: Int,
                        displayHeight: Int) : DeviceResponse() {
    companion object {
        private val log = LoggerFactory.getLogger(GuiStatusResponse::class.java)

        /**
         * <p>
         * Launcher name for the currently used device model.
         *
         * </p><p>
         * @param deviceModel Device manufacturer + model as returned by {@link org.droidmate.uiautomator_daemon.UiAutomatorDaemonDriver#getDeviceModel()}
         *
         * </p>
         */
        @JvmStatic
        fun androidLauncher(deviceModel: String): String = when (deviceModel) {
        /*
              Obtained from emulator with following settings->
                 Name-> Nexus_7_2012_API_19
                 CPU/ABI-> Intel Atom (x86)
                 Target-> Android 4.4.2 (API level 19)
                 Skin-> nexus_7
                 hw.device.name-> Nexus 7
                 hw.device.manufacturer-> Google
                 AvdId-> Nexus_7_2012_API_19
                 avd.ini.displayname-> Nexus 7 (2012) API 19
                 hw.ramSize-> 1024
                 hw.gpu.enabled-> yes
              */
            "unknown-Android SDK built for x86" -> "com.android.launcher3"
            "samsung-GT-I9300" -> "com.android.launcher"
            "LGE-Nexus 5X" -> "com.google.android.googlequicksearchbox"
            "motorola-Nexus 6" -> "com.google.android.googlequicksearchbox"
            "asus-Nexus 7" -> "com.android.launcher"
            "htc-Nexus 9" -> "com.google.android.googlequicksearchbox"
            "samsung-Nexus 10" -> "com.android.launcher"
            "google-Pixel C" -> "com.android.launcher"
            "Google-Pixel C" -> "com.google.android.apps.pixelclauncher"
            "HUAWEI-FRD-L09" -> "com.huawei.android.launcher"
            else -> {
                log.warn("Unrecognized device model of $deviceModel. Using the default.")
                "com.android.launcher"
            }
        }
    }

    /**
     * This field contains string representing the contents of the file returned by
     * `android.support.test.uiautomator.UiAutomatorTestCase.getUiDevice().dumpWindowHierarchy();`<br></br>
     * as well as <br></br>
     * `android.support.test.uiautomator.UiAutomatorTestCase.getUiDevice().dumpDisplayWidth();`<br></br>
     * `android.support.test.uiautomator.UiAutomatorTestCase.getUiDevice().dumpDisplayHeight();`
     */
    val guiStatus: IGuiStatus = GuiStatus.fromUIDump(windowHierarchyDump,
            androidLauncher(deviceModel), displayWidth, displayHeight)
}
