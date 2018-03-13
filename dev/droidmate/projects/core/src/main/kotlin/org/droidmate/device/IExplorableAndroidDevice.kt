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

package org.droidmate.device

import org.droidmate.android_sdk.DeviceException
import org.droidmate.android_sdk.IApk
import org.droidmate.apis.ITimeFormattedLogcatMessage
import org.droidmate.device.datatypes.IDeviceGuiSnapshot
import org.droidmate.uiautomator_daemon.guimodel.Action
import java.nio.file.Path

import java.time.LocalDateTime

interface IExplorableAndroidDevice {
    @Throws(DeviceException::class)
    fun hasPackageInstalled(packageName: String): Boolean

    @Throws(DeviceException::class)
    fun getGuiSnapshot(): IDeviceGuiSnapshot

    @Throws(DeviceException::class)
    fun perform(action: Action)

    @Throws(DeviceException::class)
    fun readLogcatMessages(messageTag: String): List<ITimeFormattedLogcatMessage>

    @Throws(DeviceException::class)
    fun waitForLogcatMessages(messageTag: String, minMessagesCount: Int, waitTimeout: Int, queryDelay: Int): List<ITimeFormattedLogcatMessage>

    @Throws(DeviceException::class)
    fun clearLogcat()

    @Throws(DeviceException::class)
    fun readAndClearMonitorTcpMessages(): List<List<String>>

    @Throws(DeviceException::class)
    fun getCurrentTime(): LocalDateTime

    @Throws(DeviceException::class)
    fun anyMonitorIsReachable(): Boolean

    @Throws(DeviceException::class)
    fun launchMainActivity(launchableActivityComponentName: String)

    @Throws(DeviceException::class)
    fun appIsRunning(appPackageName: String): Boolean

    @Throws(DeviceException::class)
    fun clickAppIcon(iconLabel: String)

    @Throws(DeviceException::class)
    fun takeScreenshot(app: IApk, suffix: String): Path
}

