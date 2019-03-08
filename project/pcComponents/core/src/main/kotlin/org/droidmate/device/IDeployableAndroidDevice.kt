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

import org.droidmate.device.error.DeviceException
import org.droidmate.exploration.IApk
import org.droidmate.deviceInterface.DeviceConstants

import java.nio.file.Path

interface IDeployableAndroidDevice {
	@Throws(DeviceException::class)
	fun pushFile(jar: Path)

	@Throws(DeviceException::class)
	fun pushFile(jar: Path, targetFileName: String)

	fun pullFile(fileName:String, dstPath: Path, srcPath: String = DeviceConstants.imgPath)

	fun removeFile(fileName:String,srcPath: String = DeviceConstants.imgPath)

	@Throws(DeviceException::class)
	fun removeJar(jar: Path)

	@Throws(DeviceException::class)
	fun installApk(apk: Path)

	@Throws(DeviceException::class)
	fun installApk(apk: IApk)

	@Throws(DeviceException::class)
	fun isApkInstalled(apkPackageName: String): Boolean

	@Throws(DeviceException::class)
	fun uninstallApk(apkPackageName: String, ignoreFailure: Boolean)

	@Throws(DeviceException::class)
	fun closeMonitorServers()

	@Throws(DeviceException::class)
	fun clearPackage(apkPackageName: String)

	@Throws(DeviceException::class)
	fun appProcessIsRunning(appPackageName: String): Boolean

	@Throws(DeviceException::class)
	fun clearLogcat()

	@Throws(DeviceException::class)
	fun closeConnection()

	@Throws(DeviceException::class)
	fun reboot()

	@Throws(DeviceException::class)
	fun stopUiaDaemon(uiaDaemonThreadIsNull: Boolean)

	@Throws(DeviceException::class)
	fun isAvailable(): Boolean

	@Throws(DeviceException::class)
	fun uiaDaemonClientThreadIsAlive(): Boolean

	@Throws(DeviceException::class)
	fun restartUiaDaemon(uiaDaemonThreadIsNull: Boolean)

	@Throws(DeviceException::class)
	fun startUiaDaemon()

	@Throws(DeviceException::class)
	fun removeLogcatLogFile()

	@Throws(DeviceException::class)
	fun pullLogcatLogFile()

	@Throws(DeviceException::class)
	fun reinstallUiAutomatorDaemon()

	@Throws(DeviceException::class)
	fun pushMonitorJar()

	@Throws(DeviceException::class)
	fun setupConnection()

	@Throws(DeviceException::class)
	fun reconnectAdb()

	@Throws(DeviceException::class)
	fun executeAdbCommand(command: String, successfulOutput: String, commandDescription: String)

	@Throws(DeviceException::class)
	fun uiaDaemonIsRunning(): Boolean

	@Throws(DeviceException::class)
	fun isPackageInstalled(packageName: String): Boolean
}
