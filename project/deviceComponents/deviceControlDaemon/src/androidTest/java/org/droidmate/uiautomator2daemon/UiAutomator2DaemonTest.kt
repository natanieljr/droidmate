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

package org.droidmate.uiautomator2daemon

import android.support.test.InstrumentationRegistry
import android.support.test.filters.SdkSuppress
import android.support.test.runner.AndroidJUnit4
import android.util.Log
import org.droidmate.deviceInterface.UiautomatorDaemonConstants.instrumentation_redirectionTag
import org.droidmate.deviceInterface.UiautomatorDaemonConstants.logcatLogFileName
import org.droidmate.deviceInterface.UiautomatorDaemonConstants.uiaDaemonParam_waitForInteractableTimeout
import org.droidmate.deviceInterface.UiautomatorDaemonConstants.uiaDaemonParam_waitForIdleTimeout
import org.junit.Test
import org.junit.runner.RunWith

import java.io.File
import java.io.IOException

import org.droidmate.deviceInterface.UiautomatorDaemonConstants.uiaDaemonParam_tcpPort
import org.droidmate.deviceInterface.UiautomatorDaemonConstants.uiaDaemon_logcatTag

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 18)
class UiAutomator2DaemonTest {

	@Test
	fun init() {
		val extras = InstrumentationRegistry.getArguments()

		val tcpPort = if (extras.containsKey(uiaDaemonParam_tcpPort))
			extras.get(uiaDaemonParam_tcpPort).toString().toInt()
		else
			-1
		val waitForIdleTimeout = if (extras.containsKey(uiaDaemonParam_waitForIdleTimeout))
			extras.get(uiaDaemonParam_waitForIdleTimeout).toString().toLong()
		else
			-1
		val waitForInteractableTimeout = if (extras.containsKey(uiaDaemonParam_waitForInteractableTimeout))
			extras.get(uiaDaemonParam_waitForInteractableTimeout).toString().toLong()
		else
			-1

		Log.v(uiaDaemon_logcatTag, "$uiaDaemonParam_tcpPort=$tcpPort")

		val uiAutomatorDaemonDriver = UiAutomator2DaemonDriver(waitForIdleTimeout, waitForInteractableTimeout)
		val uiAutomator2DaemonServer = UiAutomator2DaemonServer(uiAutomatorDaemonDriver)

		Log.d(uiaDaemon_logcatTag, "uiAutomator2DaemonServer.start($tcpPort)")
		var serverThread: Thread? = null
		try {
			serverThread = uiAutomator2DaemonServer.start(tcpPort)
		} catch (t: Throwable) {
			Log.e(uiaDaemon_logcatTag, "uiAutomator2DaemonServer.start($tcpPort) / FAILURE", t)
		}

		if (serverThread == null) throw AssertionError()
		Log.i(uiaDaemon_logcatTag, "uiAutomator2DaemonServer.start($tcpPort) / SUCCESS")

		try {
			// Postpone process termination until the server thread finishes.
			serverThread.join()
		} catch (e: InterruptedException) {
			Log.wtf(uiaDaemon_logcatTag, e)
		}

		if (!uiAutomator2DaemonServer.isClosed) throw AssertionError()

		Log.i(uiaDaemon_logcatTag, "init: Shutting down UiAutomatorDaemon.")
	}
}