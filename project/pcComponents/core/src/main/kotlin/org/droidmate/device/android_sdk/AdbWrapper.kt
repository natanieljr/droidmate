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

import com.google.common.base.Splitter
import com.google.common.collect.Iterables
import org.droidmate.configuration.Configuration
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.misc.BuildConstants
import org.droidmate.misc.ISysCmdExecutor
import org.droidmate.misc.SysCmdExecutorException
import org.droidmate.misc.Utils
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants
import org.slf4j.LoggerFactory
import java.io.FileWriter
import java.io.IOException

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

/**
 * Provides clean interface for communication with the Android SDK's Android Debug Bridge (ADB) tool.<br/>
 * <br/>
 * <b>Technical notes</b><br/>
 * The ADB tool is usually located in {@code <android sdk path>/platform-tools/adb.}<br/>
 * Reference: http://developer.android.com/tools/help/adb.html
 *
 * @author Konrad Jamrozik
 */
// WISH for commands using sysCmdExecutor.execute, use instead this.executeCommand
class AdbWrapper constructor(private val cfg: Configuration,
                             private val sysCmdExecutor: ISysCmdExecutor) : IAdbWrapper {
	companion object {
		private val log = LoggerFactory.getLogger(AdbWrapper::class.java)

		@JvmStatic
		fun removeAdbStartedMsgIfPresent(stdStreams: Array<String>) {
			var stdoutLines = stdStreams[0].split(Pattern.compile(System.lineSeparator()))
			stdoutLines = stdoutLines.filter { it ->
				!it.startsWith("* daemon not running") && !it.startsWith("* daemon started successfully")
			}
			stdStreams[0] = stdoutLines.joinToString(System.lineSeparator())
		}

		/**
		 * @param adbDevicesCmdStdout Standard output of call to {@code "<android sdk>/platform-tools/adb devices"}
		 *
		 * @return List of pairs describing the serial number and type (real device/emulator) of each device visible to adb.
		 */
		private fun parseDeviceInformation(adbDevicesCmdStdout: String): List<AndroidDeviceDescriptor> {
			var entries = Splitter.on('\n').omitEmptyStrings().trimResults().split(adbDevicesCmdStdout)
			entries = Iterables.skip(entries, 1) // Remove the "List of devices attached" header.

			val deviceDescriptors: MutableList<AndroidDeviceDescriptor> = mutableListOf()
			entries.forEach { entry ->
				val deviceSerialNumber = Splitter.on('\t').split(entry).first().orEmpty()

				if (deviceSerialNumber.startsWith("emulator"))
					deviceDescriptors.add(AndroidDeviceDescriptor(deviceSerialNumber, true))
				else
					deviceDescriptors.add(AndroidDeviceDescriptor(deviceSerialNumber, false))
			}

			return deviceDescriptors
		}
	}

	private fun internalGetAndroidDevicesDescriptors(): List<AndroidDeviceDescriptor> {
		val commandDescription = String
				.format("Executing adb (Android Debug Bridge) to get the list of available Android (Virtual) Devices.")

		val stdStreams: Array<String>
		try {
			stdStreams = sysCmdExecutor.execute(commandDescription, cfg.adbCommand, "devices")
		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException("Calling 'adb devices' failed.", e)
		}

		removeAdbStartedMsgIfPresent(stdStreams)

		return parseDeviceInformation(stdStreams[0])
	}

	override fun getAndroidDevicesDescriptors(): List<AndroidDeviceDescriptor> {
		var deviceDescriptors = internalGetAndroidDevicesDescriptors()

		if (cfg.waitForDevice) {
			log.warn("No devices connected. Waiting for device.")
			while (deviceDescriptors.isEmpty()) {
				Thread.sleep(1000)
				deviceDescriptors = internalGetAndroidDevicesDescriptors()
			}
		}

		if (deviceDescriptors.isEmpty())
			throw NoAndroidDevicesAvailableException()

		assert(deviceDescriptors.isNotEmpty())
		return (deviceDescriptors)
	}

	@Throws(AdbWrapperException::class)
	override fun installApk(deviceSerialNumber: String, apkToInstall: Path) {
		val commandDescription = "Executing adb (Android Debug Bridge) to install ${apkToInstall.fileName} on Android (Virtual) Device."

		try {
			val stdStreams = sysCmdExecutor.execute(commandDescription, cfg.adbCommand, "-s", deviceSerialNumber, "install -r",
					apkToInstall.toAbsolutePath().toString())

			if (stdStreams[0].contains("[INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES]"))
				throw AdbWrapperException("Execution of 'adb -s $deviceSerialNumber install -r ${apkToInstall.toAbsolutePath()}' " +
						"resulted in [INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES] being output to stdout. Thus, no app was actually " +
						"installed. Likely reason for the problem: you are trying to install a built in Google app that cannot be uninstalled" +
						"or reinstalled. DroidMate doesn't support such apps.")

			if (stdStreams[0].contains("Failure"))
				throw AdbWrapperException("Execution of 'adb -s $deviceSerialNumber install -r ${apkToInstall.toAbsolutePath()}' " +
						"resulted in stdout containing 'Failure'. The full stdout:\n$stdStreams[0]")

		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException("Executing 'adb install' failed. Oh my.", e)
		}

	}

	override fun installApk(deviceSerialNumber: String, apkToInstall: IApk) {
		val apkFile = Paths.get(apkToInstall.absolutePath)
		this.installApk(deviceSerialNumber, apkFile)
	}

	/**
	 * Android 8 throws an exception, if an APK is tried to be uninstalled, although
	 * it is not installed.
	 */
	override fun uninstallApk(deviceSerialNumber: String, apkPackageName: String, ignoreFailure: Boolean) {
		try {
			val commandDescription = "Executing adb (Android Debug Bridge) to uninstall $apkPackageName from Android Device with s/n $deviceSerialNumber."

			val stdStreams = sysCmdExecutor.execute(commandDescription, cfg.adbCommand, "-s",
					deviceSerialNumber, "uninstall", apkPackageName)
			removeAdbStartedMsgIfPresent(stdStreams)

			val stdout = stdStreams[0]

			// "Failure" is what the adb's "uninstall" command outputs when it fails.
			if (!ignoreFailure && stdout.contains("Failure"))
				throw AdbWrapperException("Failed to uninstall the apk package $apkPackageName.")

			if (ignoreFailure && stdout.contains("Failure"))
				log.trace("Ignored failure of uninstalling of $apkPackageName.")

		} catch (e: SysCmdExecutorException) {
			if (!ignoreFailure || e.message?.contains("Unknown package:") == false)
				throw AdbWrapperException("Calling 'adb uninstall' failed. Oh my.", e)
		}
	}

	override fun isApkInstalled(deviceSerialNumber: String, packageName: String): Boolean {
		try {
			val packages = listPackages(deviceSerialNumber)
			return packages.contains(packageName)
		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException(e)
		}
	}

	override fun forwardPort(deviceSerialNumber: String, port: Int) {
//    log.trace("forwardPort(deviceSerialNumber:$deviceSerialNumber, port:$port)")

		try {
			val commandDescription = "Executing adb (Android Debug Bridge) to forward port $port to android device with s/n $deviceSerialNumber."

			sysCmdExecutor.execute(commandDescription, cfg.adbCommand, "-s", deviceSerialNumber, "forward",
					"tcp:" + port,
					"tcp:" + port)

		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException("Executing 'adb forward' failed. Oh my.", e)
		}

	}

	override fun reverseForwardPort(deviceSerialNumber: String, port: Int) {
		log.debug("reverseForwardPort($deviceSerialNumber, $port)")

		try {
			val commandDescription = "Executing adb (Android Debug Bridge) to reverse-forward port $port to android device with s/n $deviceSerialNumber."

			sysCmdExecutor.execute(commandDescription, cfg.adbCommand, "-s", deviceSerialNumber, "reverse",
					"tcp:" + port,
					"tcp:" + port)

		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException("Executing 'adb forward' failed. Oh my.", e)
		}

	}

	override fun reboot(deviceSerialNumber: String) {
		try {
			val commandDescription = "Executing adb (Android Debug Bridge) to reboot android device with s/n $deviceSerialNumber."

			sysCmdExecutor.execute(commandDescription, cfg.adbCommand, "-s", deviceSerialNumber, "reboot")

		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException("Executing 'adb reboot' failed. Oh my.", e)
		}
	}

	override fun readMessagesFromLogcat(deviceSerialNumber: String, messageTag: String): List<String> {

		try {
			val commandDescription = "Executing adb (Android Debug Bridge) to read from logcat messages tagged: $messageTag"

			val stdStreams = sysCmdExecutor.execute(commandDescription, cfg.adbCommand,
					"-s", deviceSerialNumber,
					/*
Command line explanation:
-d      : Dumps the log to the screen and exits.
-b main : Loads the "main" buffer.
-v time : Sets the message output format to time (see [2]).
*:s     : Suppresses all messages, besides the ones having messageTag.
				Detailed explanation of the "*:s* filter:
				* : all messages // except messageTag, overridden by next param, "messageTag"
				S : SILENT: suppress all messages

Logcat reference:
[1] http://developer.android.com/tools/help/logcat.html
[2] http://developer.android.com/tools/debugging/debugging-log.html#outputFormat

*/
					"logcat -d -b main -v time *:s", messageTag)

			return stdStreams[0]
					.split(System.lineSeparator())
					.map { it.trim() }
					.filter { it.isNotEmpty() }

		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException(e)
		}
	}

	override fun listPackages(deviceSerialNumber: String): String {
		try {
			val commandDescription = "Executing adb (Android Debug Bridge) to list packages."

			val stdStreams = sysCmdExecutor.execute(commandDescription, cfg.adbCommand,
					"-s", deviceSerialNumber,
					"shell pm list packages")

			return stdStreams[0]

		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException(e)
		}
	}

	override fun listPackage(deviceSerialNumber: String, packageName: String): String {

		try {
			val commandDescription = "Executing adb (Android Debug Bridge) to list package $packageName."

			val stdStreams = sysCmdExecutor.execute(commandDescription, cfg.adbCommand,
					"-s", deviceSerialNumber,
					"shell pm list packages $packageName")

			return stdStreams[0]

		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException(e)
		}
	}

	override fun ps(deviceSerialNumber: String): String {

		try {
			val commandDescription = "Executing adb (Android Debug Bridge) to list processes (ps)."

			val stdStreams = sysCmdExecutor.execute(commandDescription, cfg.adbCommand,
					"-s", deviceSerialNumber,
					"shell ps")

			return stdStreams[0]

		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException(e)
		}
	}

	override fun clearLogcat(deviceSerialNumber: String) {

		try {
			val commandDescription = "Executing adb (Android Debug Bridge) to clear logcat output."

			sysCmdExecutor.execute(commandDescription, cfg.adbCommand,
					"-s", deviceSerialNumber,
					"logcat -c")

		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException(e)
		}

	}

	override fun waitForMessagesOnLogcat(deviceSerialNumber: String, messageTag: String, minMessagesCount: Int, waitTimeout: Int, queryDelay: Int): List<String> {
		var readMessages: List<String> = ArrayList()

		try {
			var timeLeftToQuery = waitTimeout
			while (timeLeftToQuery >= 0 && readMessages.size < minMessagesCount) {
//        log.verbose("waitForMessagesOnLogcat.sleep(queryDelay=$queryDelay)")
				Thread.sleep(queryDelay.toLong())
				timeLeftToQuery -= queryDelay
//        log.verbose("waitForMessagesOnLogcat.readMessagesFromLogcat(messageTag=$messageTag) " +
//          "timeLeftToQuery=$timeLeftToQuery readMessages.size()=${readMessages.size()} minMessagesCount=$minMessagesCount")
				readMessages = this.readMessagesFromLogcat(deviceSerialNumber, messageTag)
			}
		} catch (e: InterruptedException) {
			throw AdbWrapperException(e)
		}
//    log.verbose("waitForMessagesOnLogcat loop finished. readMessages.size()=${readMessages.size()}")

		if (readMessages.size < minMessagesCount) {
			throw AdbWrapperException("Failed waiting for at least $minMessagesCount messages on logcat. " +
					"actual messages count before timeout: ${readMessages.size},  " +
					"s/n: $deviceSerialNumber, " +
					"messageTag: $messageTag, " +
					"minMessageCount: $minMessagesCount, " +
					"waitTimeout: $waitTimeout, " +
					"queryDelay: $queryDelay")

		}

		assert(readMessages.size >= minMessagesCount)
		return readMessages
	}

	override fun killAdbServer() {
		try {
			val commandDescription = "Executing adb (Android Debug Bridge) to kill adb server."

			sysCmdExecutor.execute(commandDescription, cfg.adbCommand,
					"kill-server")

		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException("Executing 'adb kill-server' failed. Oh my.", e)
		}
	}

	override fun startAdbServer() {
		val p: Process
		try {
			/* Calling ProcessBuilder() instead of SysCmdExecutor.execute() as it behaves in strange ways, namely:
 - if the server doesn't need to be started, it returns 0
 - if the server needs to be started and timeout is set to 1000ms, it throws exception caused by exit code -1
 - if the server needs to be started and timeout is set to 5000, it hangs, so it seems the timeout has no effect.

 My question on Stack Overflow with some discussion:
 http://stackoverflow.com/questions/17282081/adb-start-server-java-gradle-and-apache-commons-exec-how-to-make
 -it-right/

 Other references:
 http://stackoverflow.com/questions/931536/how-do-i-launch-a-completely-independent-process-from-a-java-program
 http://www.javaworld.com/jw-12-2000/jw-1229-traps.html?page=1
*/

			// .inheritIO() causes the command to write out to stdout if it indeed had to start the server.
			p = ProcessBuilder(Utils.quoteIfIsPathToExecutable(cfg.adbCommand),
					"start-server").inheritIO().start()

			p.waitFor()

		} catch (e: IOException) {
			throw AdbWrapperException("Starting adb server failed, oh my!", e)
		} catch (e: InterruptedException) {
			throw AdbWrapperException("Interrupted starting adb server. Oh my!", e)
		}
	}

	override fun pushFile(deviceSerialNumber: String, jarFile: Path) {
		val targetFileName = jarFile.fileName.toString()
		pushFile(deviceSerialNumber, jarFile, targetFileName)
	}

	override fun pushFile(deviceSerialNumber: String, jarFile: Path, targetFileName: String) {
		// A new path must be created, otherwise this will result in a ProviderMismatchException
		// More information here: http://stackoverflow.com/questions/22611919/why-do-i-get-providermismatchexception-when-i-try-to-relativize-a-path-agains
		val path = Paths.get(jarFile.toUri())
		assert(Files.exists(path) && !Files.isDirectory(path))

		val commandDescription = "Executing adb to push ${jarFile.fileName} on Android Device with s/n $deviceSerialNumber."

		try {
			// Executed command based on step 4 from:
			// http://developer.android.com/tools/testing/testing_ui.html#builddeploy
			sysCmdExecutor.execute(commandDescription, cfg.adbCommand,
					"-s", deviceSerialNumber,
					"push", jarFile.toAbsolutePath().toString(), BuildConstants.AVD_dir_for_temp_files + targetFileName)

		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException("Executing 'adb push ...' failed. Oh my.", e)
		}
	}

	override fun removeJar(deviceSerialNumber: String, jarFile: Path) {
		val commandDescription = "Executing adb to remove ${jarFile.fileName} from Android Device with s/n deviceSerialNumber>"

		try {
			// Executed command based on:
			// http://forum.xda-developers.com/showthread.php?t=517874
			//
			// Hint: to list files to manually check if the file was deleted, use: adb shell ls
			sysCmdExecutor.execute(commandDescription, cfg.adbCommand,
					"-s", deviceSerialNumber,
					"shell", "rm", BuildConstants.AVD_dir_for_temp_files + jarFile.fileName.toString())

		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException("Executing 'adb shell rm ...' failed. Oh my.", e)
		}
	}

	override fun launchMainActivity(deviceSerialNumber: String, launchableActivityName: String) {

		try {
			val commandDescription = "Executing adb (Android Debug Bridge) to start main activity on the Android Device."

			// Reference:
			// http://developer.android.com/tools/help/adb.html#am
			val stdStreams = sysCmdExecutor.executeWithTimeout(commandDescription, cfg.launchActivityTimeout, cfg.adbCommand,
					"-s", deviceSerialNumber,
					"shell am start", // start an activity using Activity Manager (am)
					"-W", // wait for launch to complete
					"-S", // force stop before starting activity
					"-a", "android.intent.action.MAIN", // from package android.content.Intent.ACTION_MAIN
					"-c", "android.intent.category.LAUNCHER", // from package android.content.Intent.CATEGORY_LAUNCHER
					"-n", launchableActivityName)

			val stdout = stdStreams[0]
			val launchMainActivityFailureString = "Error: "

			if (stdout.contains(launchMainActivityFailureString)) {
				val failureLine = stdout.reader().readLines().filter { line -> line.contains(launchMainActivityFailureString) }

				throw AdbWrapperException("AdbWrapper.launchMainActivity successfully executed the underlying adb shell command, " +
						"but its stdout contains the failure string of: '$launchMainActivityFailureString'. Full line from the command " +
						"stdout with the failure string:\n" +
						"$failureLine")
			}

		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException("Executing 'adb shell am start' of '$launchableActivityName' failed. Oh my.", e)
		}
	}

	override fun clearPackage(deviceSerialNumber: String, apkPackageName: String): Boolean {

		try {
			val commandDescription = "Executing adb (Android Debug Bridge) to clear package on the Android Device."

			// WISH what about softer alternative of am force-stop ? See http://stackoverflow.com/questions/3117095/stopping-an-android-app-from-console
			// Reference:
			// http://stackoverflow.com/questions/3117095/stopping-an-android-app-from-console/3117310#3117310
			val stdStreams = sysCmdExecutor.execute(commandDescription, cfg.adbCommand,
					"-s", deviceSerialNumber,
					"shell pm clear", // clear everything associated with a package
					apkPackageName)

			val stdout = stdStreams[0].trim()
			val adbClearPackageFailureStdout = "Failed"
			if (stdout == adbClearPackageFailureStdout)
				throw AdbWrapperException("adb returned '$adbClearPackageFailureStdout' on stdout when supplied with command 'shell pm clear $apkPackageName'")

			return true

		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException("Executing 'adb shell pm clear <PACKAGE_NAME>' failed. Oh my.", e)
		}
	}

	override fun startUiautomatorDaemon(deviceSerialNumber: String, port: Int) {
		if (cfg.androidApi == Configuration.api23)
			startUiautomatorDaemon_api23(deviceSerialNumber, port)
		else throw UnexpectedIfElseFallthroughError()
	}

	@Throws(AdbWrapperException::class)
	private fun startUiautomatorDaemon_api23(deviceSerialNumber: String, port: Int) {
		val commandDescription = "Executing adb to start UiAutomatorDaemon service on Android Device with s/n $deviceSerialNumber"

		val uiaDaemonCmdLine = String.format("-e %s %s -e %s %s -e %s %s",
				UiautomatorDaemonConstants.uiaDaemonParam_waitForGuiToStabilize, cfg.uiautomatorDaemonWaitForGuiToStabilize,
				UiautomatorDaemonConstants.uiaDaemonParam_waitForWindowUpdateTimeout, cfg.uiautomatorDaemonWaitForWindowUpdateTimeout,
				UiautomatorDaemonConstants.uiaDaemonParam_tcpPort, port)

		val testRunner = UiautomatorDaemonConstants.uia2Daemon_testPackageName + "/" + UiautomatorDaemonConstants.uia2Daemon_testRunner
		val failureString = "'adb shell -s $deviceSerialNumber instrument --user 0 $uiaDaemonCmdLine -w $testRunner' failed. Oh my. "

		try {
			val stdStreams = this.sysCmdExecutor.executeWithoutTimeout(commandDescription, cfg.adbCommand,
					"-s", deviceSerialNumber,
					"shell am instrument",
					"--user 0",
					uiaDaemonCmdLine,
					"-w",
					testRunner)

			validateInstrumentation(stdStreams, failureString)
		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException(failureString, e)
		}
	}

	private fun validateInstrumentation(stdStreams: Array<String>, failureString: String) {
		if (stdStreams[0].contains("INSTRUMENTATION_FAILED")) {
			throw AdbWrapperException("Executing " +
					failureString +
					"Reason: stdout contains 'INSTRUMENTATION_FAILED' line. The full stdout:\n${stdStreams[0]}")
		}
	}

	override fun pullFile_api23(deviceSerialNumber: String, pulledFileName: String, destinationFilePath: String, shellPackageName: String) {
		assert(pulledFileName.isNotEmpty())
		assert(destinationFilePath.isNotEmpty())
		assert(shellPackageName.isNotEmpty())

		assert(Files.notExists(Paths.get(destinationFilePath)))

		val pulledFilePath = UiautomatorDaemonConstants.deviceLogcatLogDir_api23 + pulledFileName

		val stdout = this.executeCommand(deviceSerialNumber, "", "Pull file (API23 compatibility)",
				"exec-out run-as", shellPackageName, "cat", pulledFilePath)

		val writer = FileWriter(destinationFilePath)
		writer.write(stdout)
		writer.close()
	}

	override fun removeFile_api23(deviceSerialNumber: String, fileName: String, shellPackageName: String) {
		assert(fileName.isNotEmpty())
		assert(shellPackageName.isNotEmpty())

		try {
			val filePath = UiautomatorDaemonConstants.deviceLogcatLogDir_api23 + fileName
			this.executeCommand(deviceSerialNumber, "", "Delete file (API23 compatibility).",
					"shell run-as", shellPackageName, "rm", filePath)
		} catch (e: Exception) {
			// Logcat file does not exist on new devices, therefore it crashes on the first attempt
			if (!fileName.contains("droidmate_logcat"))
				throw e
		}
	}

	override fun takeScreenshot(deviceSerialNumber: String, targetPath: String) {
		assert(targetPath.isNotEmpty())

		val devicePath = "sdcard/temp_screenshot.png"

		this.executeCommand(deviceSerialNumber, "", "Take screenshot step 1: take screenshot.", "shell screencap -p", devicePath)
		this.executeCommand(deviceSerialNumber, "", "Take screenshot step 2: pull screenshot.", "pull", devicePath, targetPath)
		this.executeCommand(deviceSerialNumber, "", "Take screenshot step 3: remove screenshot on device.", "shell rm", devicePath)
	}

	override fun reconnect(deviceSerialNumber: String) {
		// Sometimes (roughly 50% of cases) instead of "done" it prints out "error: no devices/emulators found"
		this.executeCommand(deviceSerialNumber, "", "reconnect", "reconnect")
		this.executeCommand(deviceSerialNumber, "", "wait-for-device", "wait-for-device")
	}

	override fun executeCommand(deviceSerialNumber: String, successfulOutput: String, commandDescription: String, vararg cmdLineParams: String): String {
		val allCmdLineParams = arrayListOf(cfg.adbCommand, "-s", deviceSerialNumber)
		try {
			allCmdLineParams.addAll(cmdLineParams)
			val stdStreams = sysCmdExecutor.execute(commandDescription, *allCmdLineParams.toTypedArray())

			assert(stdStreams.size == 2)
			if (!stdStreams[0].startsWith(successfulOutput))
				throw AdbWrapperException("After executing adb command of '${allCmdLineParams.joinToString(" ")}', " +
						"expected stdout to have '$successfulOutput'. " +
						"Instead, stdout had '${stdStreams[0].trim()}' and stderr had '${stdStreams[1].trim()}'.")

			return stdStreams[0]
		} catch (e: SysCmdExecutorException) {
			throw AdbWrapperException("Executing adb command '${allCmdLineParams.joinToString(" ")}' failed", e)
		}
	}

	@Suppress("unused")
	private fun debugStdStreams(stdStreams: Array<String>) {
		val stdout = stdStreams[0]
		val stderr = stdStreams[1]
		println("==========")
		println("DEBUG STD STREAMS")
		println("===== STD OUT =====")
		println(stdout)
		println("===== STD ERR =====")
		println(stderr)
		println("==========")
	}
}
