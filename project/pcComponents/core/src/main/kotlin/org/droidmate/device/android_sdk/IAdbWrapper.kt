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

package org.droidmate.device.android_sdk

import java.nio.file.Path

interface IAdbWrapper {

	@Throws(AdbWrapperException::class)
	fun startAdbServer()

	@Throws(AdbWrapperException::class)
	fun killAdbServer()

	@Throws(AdbWrapperException::class)
	fun getAndroidDevicesDescriptors(): List<AndroidDeviceDescriptor>

	@Throws(AdbWrapperException::class)
	fun waitForMessagesOnLogcat(deviceSerialNumber: String, messageTag: String, minMessagesCount: Int, waitTimeout: Int, queryDelay: Int): List<String>

	@Throws(AdbWrapperException::class)
	fun forwardPort(deviceSerialNumber: String, port: Int)

	@Throws(AdbWrapperException::class)
	fun reverseForwardPort(deviceSerialNumber: String, port: Int)

	@Throws(AdbWrapperException::class)
	fun pushFile(deviceSerialNumber: String, jarFile: Path)

	@Throws(AdbWrapperException::class)
	fun pushFile(deviceSerialNumber: String, jarFile: Path, targetFileName: String)

	@Throws(AdbWrapperException::class)
	fun removeJar(deviceSerialNumber: String, jarFile: Path)

	@Throws(AdbWrapperException::class)
	fun installApk(deviceSerialNumber: String, apkToInstall: Path)

	@Throws(AdbWrapperException::class)
	fun installApk(deviceSerialNumber: String, apkToInstall: IApk)

	@Throws(AdbWrapperException::class)
	fun uninstallApk(deviceSerialNumber: String, apkPackageName: String, ignoreFailure: Boolean)

	@Throws(AdbWrapperException::class)
	fun launchMainActivity(deviceSerialNumber: String, launchableActivityName: String)

	@Throws(AdbWrapperException::class)
	fun clearLogcat(deviceSerialNumber: String)

	fun clearPackage(deviceSerialNumber: String, apkPackageName: String): Boolean

	fun readMessagesFromLogcat(deviceSerialNumber: String, messageTag: String): List<String>

	@Throws(AdbWrapperException::class)
	fun listPackages(deviceSerialNumber: String): String

	@Throws(AdbWrapperException::class)
	fun listPackage(deviceSerialNumber: String, packageName: String): String

	@Throws(AdbWrapperException::class)
	fun ps(deviceSerialNumber: String): String

	@Throws(AdbWrapperException::class)
	fun reboot(deviceSerialNumber: String)

	@Throws(AdbWrapperException::class)
	fun startUiautomatorDaemon(deviceSerialNumber: String, port: Int)

	//void stopUiautomatorDaemon(deviceSerialNumber: String) throws AdbWrapperException

	@Throws(AdbWrapperException::class)
	fun removeFile_api23(deviceSerialNumber: String, fileName: String, shellPackageName: String)

	@Throws(AdbWrapperException::class)
	fun pullFile_api23(deviceSerialNumber: String, pulledFileName: String, destinationFilePath: String, shellPackageName: String)

	@Throws(AdbWrapperException::class)
	fun takeScreenshot(deviceSerialNumber: String, targetPath: String)

	@Throws(AdbWrapperException::class)
	fun executeCommand(deviceSerialNumber: String, successfulOutput: String, commandDescription: String, vararg cmdLineParams: String): String

	@Throws(AdbWrapperException::class)
	fun reconnect(deviceSerialNumber: String)
}
