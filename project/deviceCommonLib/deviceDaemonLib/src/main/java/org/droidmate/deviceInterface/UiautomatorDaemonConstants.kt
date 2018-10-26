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

// WISH Borges: Check if the package should be kept with the previous name or updated to org.droidmate.uiautomator2daemon
package org.droidmate.deviceInterface

object UiautomatorDaemonConstants {

	// To understand why this is constant and not a cmd line parameter, see comment in
	// org.droidmate.configuration.ConfigurationBuilder.bindAndValidate()
	const val UIADAEMON_SERVER_PORT = 59800

	const val logcatLogFileName = "droidmate_logcat.txt"

	const val deviceLogcatTagPrefix = "droidmate/"
	const val uiaDaemon_logcatTag = deviceLogcatTagPrefix + "uiad"

	// End of DUPLICATION WARNING

	const val UIADAEMON_SERVER_START_TAG = "$uiaDaemon_logcatTag/notify"
	const val UIADAEMON_SERVER_START_MSG = "uiad server start success"

	//val DEVICE_COMMAND_GET_UIAUTOMATOR_WINDOW_HIERARCHY_DUMP = "get_uiautomator_window_hierarchy_dump"
	//val DEVICE_COMMAND_PERFORM_ACTION = "perform_action"
	//val DEVICE_COMMAND_STOP_UIADAEMON = "stop_uiadaemon"

	const val uiaDaemon_packageName = "org.droidmate.uiautomator_daemon"
	/**
	 * Method name to be called when initializing `UiAutomatorDaemon` through adb.<br></br>
	 * <br></br>
	 * Name format according to help obtained by issuing `adb shell uiautomator runtest` in terminal.
	 */
	val uiaDaemon_initMethodName = "$uiaDaemon_packageName.UiAutomatorDaemon#init"
	const val uia2Daemon_packageName = "org.droidmate.uiautomator2daemon.UiAutomator2Daemon"
	const val uia2Daemon_testPackageName = "$uia2Daemon_packageName.test"
	const val uia2Daemon_testRunner = "android.support.test.runner.AndroidJUnitRunner"

	const val uiaDaemonParam_tcpPort = "uiadaemon_server_tcp_port"
	const val uiaDaemonParam_waitForIdleTimeout = "uiadaemon_wait_for_idle_timeout"
	const val uiaDaemonParam_waitForInteractableTimeout = "uiadaemon_wait_for_interactable_timeout"
	const val uiaDaemonParam_socketTimeout = "uiadaemon_server_socket_timeout"

	const val deviceLogcatLogDir_api23 = "/data/user/0/$uia2Daemon_packageName/files/"

	// !!! DUPLICATION WARNING !!!
	// These values are duplicated in Instrumentation library from Philipp.
	// Has to be equivalent to:
	// - de.uds.infsec.instrumentation.Instrumentation#TAG and
	// - <Instrumentation project dir>/jni/utils/logcat.h#_LOG_TAG
	const val instrumentation_redirectionTag = "Instrumentation"
	// end of DUPLICATION WARNING

	// !!! DUPLICATION WARNING !!!
	// org.droidmate.uieventstologcat.UIEventsToLogcatOutputter#tag
	const val uiEventTag = "UIEventsToLogcat"
	// end of DUPLICATION WARNING

	// !!! DUPLICATION WARNING !!!
	// org.droidmate.uia_manual_test_cases.TestCases#tag
	const val uiaTestCaseTag = "UiaTestCase"
	// end of DUPLICATION WARNING
}
