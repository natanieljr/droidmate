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

import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.error.DeviceException
import org.droidmate.deviceInterface.DeviceConstants

import java.nio.file.Path

interface IDeployableAndroidDevice {
	@Throws(DeviceException::class)
	suspend fun pushFile(jar: Path)

	@Throws(DeviceException::class)
	suspend fun pushFile(jar: Path, targetFileName: String)

	suspend fun pullFile(fileName:String, dstPath: Path, srcPath: String = DeviceConstants.imgPath)

	suspend fun removeFile(fileName:String,srcPath: String = DeviceConstants.imgPath)

	@Throws(DeviceException::class)
	suspend fun removeJar(jar: Path)

	@Throws(DeviceException::class)
	suspend fun installApk(apk: Path)

	@Throws(DeviceException::class)
	suspend fun installApk(apk: IApk)

	@Throws(DeviceException::class)
	suspend fun isApkInstalled(apkPackageName: String): Boolean

	@Throws(DeviceException::class)
	suspend fun uninstallApk(apkPackageName: String, ignoreFailure: Boolean)

	@Throws(DeviceException::class)
	suspend fun closeMonitorServers()

	@Throws(DeviceException::class)
	suspend fun clearPackage(apkPackageName: String)

	@Throws(DeviceException::class)
	suspend fun appProcessIsRunning(appPackageName: String): Boolean

	@Throws(DeviceException::class)
	suspend fun clearLogcat()

	@Throws(DeviceException::class)
	suspend fun closeConnection()

	@Throws(DeviceException::class)
	suspend fun reboot()

	@Throws(DeviceException::class)
	suspend fun stopUiaDaemon(uiaDaemonThreadIsNull: Boolean)

	@Throws(DeviceException::class)
	suspend fun isAvailable(): Boolean

	@Throws(DeviceException::class)
	suspend fun uiaDaemonClientThreadIsAlive(): Boolean

	@Throws(DeviceException::class)
	suspend fun restartUiaDaemon(uiaDaemonThreadIsNull: Boolean)

	@Throws(DeviceException::class)
	suspend fun startUiaDaemon()

	@Throws(DeviceException::class)
	suspend fun removeLogcatLogFile()

	@Throws(DeviceException::class)
	suspend fun pullLogcatLogFile()

	@Throws(DeviceException::class)
	suspend fun reinstallUiAutomatorDaemon()

	@Throws(DeviceException::class)
	suspend fun pushAuxiliaryFiles()

	@Throws(DeviceException::class)
	suspend fun setupConnection()

	@Throws(DeviceException::class)
	suspend fun reconnectAdb()

	@Throws(DeviceException::class)
	suspend fun executeAdbCommand(command: String, successfulOutput: String, commandDescription: String)

	@Throws(DeviceException::class)
	suspend fun uiaDaemonIsRunning(): Boolean

	@Throws(DeviceException::class)
	suspend fun isPackageInstalled(packageName: String): Boolean
}
