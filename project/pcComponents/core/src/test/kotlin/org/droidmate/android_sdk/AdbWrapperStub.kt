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

package org.droidmate.android_sdk

import org.droidmate.device.android_sdk.AndroidDeviceDescriptor
import org.droidmate.device.android_sdk.IAdbWrapper
import org.droidmate.device.android_sdk.IApk
import org.droidmate.misc.ISysCmdExecutor

import java.nio.file.Path

class AdbWrapperStub : IAdbWrapper {
	override fun forceStop(deviceSerialNumber: String, apk: IApk) {
	}

	override fun isApkInstalled(deviceSerialNumber: String, packageName: String): Boolean {
		return false
	}

	override fun startAdbServer() {
	}


	override fun killAdbServer() {
		assert(false, { "Not yet implemented!" })
	}

	override fun getAndroidDevicesDescriptors(): List<AndroidDeviceDescriptor> {
		return arrayListOf(AndroidDeviceDescriptor("fake-serial-number", false))
	}

	override fun waitForMessagesOnLogcat(deviceSerialNumber: String, messageTag: String, minMessagesCount: Int, waitTimeout: Int, queryDelay: Int): List<String> {
		assert(false, { "Not yet implemented!" })
		return ArrayList()
	}


	override fun forwardPort(deviceSerialNumber: String, port: Int) {
		assert(false, { "Not yet implemented!" })
	}

	override fun reverseForwardPort(deviceSerialNumber: String, port: Int) {
		assert(false, { "Not yet implemented!" })
	}

	override fun pushFile(deviceSerialNumber: String, jarFile: Path) {
		assert(false, { "Not yet implemented!" })
	}

	override fun pushFile(deviceSerialNumber: String, jarFile: Path, targetFileName: String) {
		assert(false, { "Not yet implemented!" })
	}

	override fun removeJar(deviceSerialNumber: String, jarFile: Path) {
		assert(false, { "Not yet implemented!" })
	}

	override fun installApk(deviceSerialNumber: String, apkToInstall: IApk) {
		assert(false, { "Not yet implemented!" })
	}

	override fun installApk(deviceSerialNumber: String, apkToInstall: Path) {
		assert(false, { "Not yet implemented!" })
	}

	override fun uninstallApk(deviceSerialNumber: String, apkPackageName: String, ignoreFailure: Boolean) {
		assert(false, { "Not yet implemented!" })
	}

	override fun launchMainActivity(deviceSerialNumber: String, launchableActivityName: String) {
		assert(false, { "Not yet implemented!" })
	}

	override fun clearLogcat(deviceSerialNumber: String) {
		assert(false, { "Not yet implemented!" })
	}

	override fun clearPackage(deviceSerialNumber: String, apkPackageName: String): Boolean {
		assert(false, { "Not yet implemented!" })
		return false
	}

	override fun readMessagesFromLogcat(deviceSerialNumber: String, messageTag: String): List<String> {
		assert(false, { "Not yet implemented!" })
		return ArrayList()
	}

	override fun listPackage(deviceSerialNumber: String, packageName: String): String {
		assert(false, { "Not yet implemented!" })
		return ""
	}

	override fun listPackages(deviceSerialNumber: String): String {
		assert(false, { "Not yet implemented!" })
		return ""
	}

	override fun ps(deviceSerialNumber: String): String {
		assert(false, { "Not yet implemented!" })
		return ""
	}

	override fun reboot(deviceSerialNumber: String) {

		assert(false, { "Not yet implemented!" })
	}

	override fun startUiautomatorDaemon(deviceSerialNumber: String, port: Int) {
	}

	override fun removeFileApi23(deviceSerialNumber: String, filePath: String, shellPackageName: String) {
	}

	override fun pullFileApi23(deviceSerialNumber: String, pulledFilePath: String, destinationFilePath: Path, shellPackageName: String) {

	}

	override fun takeScreenshot(deviceSerialNumber: String, targetPath: String) {

	}

	override fun executeCommand(deviceSerialNumber: String, successfulOutput: String, commandDescription: String, vararg cmdLineParams: String): String {

		return ""
	}

	override fun executeCommand(sysCmdExecutor: ISysCmdExecutor, deviceSerialNumber: String, successfulOutput: String, commandDescription: String, vararg cmdLineParams: String): String {
		assert(false, { "Not yet implemented!" })
		return ""
	}

	override fun reconnect(deviceSerialNumber: String) {

	}
}
