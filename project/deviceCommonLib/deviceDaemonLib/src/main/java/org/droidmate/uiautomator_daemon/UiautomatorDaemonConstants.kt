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
package org.droidmate.uiautomator_daemon

object UiautomatorDaemonConstants {

	// To understand why this is constant and not a cmd line parameter, see comment in
	// org.droidmate.configuration.ConfigurationBuilder.bindAndValidate()
	val UIADAEMON_SERVER_PORT = 59800

	val logcatLogFileName = "droidmate_logcat.txt"

	val deviceLogcatTagPrefix = "droidmate/"
	val uiaDaemon_logcatTag = deviceLogcatTagPrefix + "uiad"

	// End of DUPLICATION WARNING

	val UIADAEMON_SERVER_START_TAG = "$uiaDaemon_logcatTag/notify"
	val UIADAEMON_SERVER_START_MSG = "uiad server start success"

	val DEVICE_COMMAND_GET_UIAUTOMATOR_WINDOW_HIERARCHY_DUMP = "get_uiautomator_window_hierarchy_dump"
	val DEVICE_COMMAND_PERFORM_ACTION = "perform_action"
	val DEVICE_COMMAND_STOP_UIADAEMON = "stop_uiadaemon"

	val uiaDaemon_packageName = "org.droidmate.uiautomator_daemon"
	/**
	 * Method name to be called when initializing `UiAutomatorDaemon` through adb.<br></br>
	 * <br></br>
	 * Name format according to help obtained by issuing `adb shell uiautomator runtest` in terminal.
	 */
	val uiaDaemon_initMethodName = "$uiaDaemon_packageName.UiAutomatorDaemon#init"
	val uia2Daemon_packageName = "org.droidmate.uiautomator2daemon.UiAutomator2Daemon"
	val uia2Daemon_testPackageName = "$uia2Daemon_packageName.test"
	val uia2Daemon_testRunner = "android.support.test.runner.AndroidJUnitRunner"

	val uiaDaemonParam_waitForGuiToStabilize = "wait_for_gui_to_stabilize"
	val uiaDaemonParam_waitForWindowUpdateTimeout = "wait_for_window_update_timeout"
	val uiaDaemonParam_tcpPort = "uiadaemon_server_tcp_port"

	val deviceLogcatLogDir_api23 = "/data/user/0/$uia2Daemon_packageName/files/"

	// !!! DUPLICATION WARNING !!!
	// These values are duplicated in Instrumentation library from Philipp.
	// Has to be equivalent to:
	// - de.uds.infsec.instrumentation.Instrumentation#TAG and
	// - <Instrumentation project dir>/jni/utils/log.h#_LOG_TAG
	val instrumentation_redirectionTag = "Instrumentation"
	// end of DUPLICATION WARNING

	// !!! DUPLICATION WARNING !!!
	// org.droidmate.uieventstologcat.UIEventsToLogcatOutputter#tag
	val uiEventTag = "UIEventsToLogcat"
	// end of DUPLICATION WARNING

	// !!! DUPLICATION WARNING !!!
	// org.droidmate.uia_manual_test_cases.TestCases#tag
	val uiaTestCaseTag = "UiaTestCase"
	// end of DUPLICATION WARNING
}
