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

import android.os.Build
import android.support.test.InstrumentationRegistry
import android.support.test.filters.SdkSuppress
import android.support.test.runner.AndroidJUnit4
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.DeviceConstants.uiaDaemonParam_delayedImgFetch
import org.droidmate.deviceInterface.DeviceConstants.uiaDaemonParam_enablePrintOuts
import org.droidmate.deviceInterface.DeviceConstants.uiaDaemonParam_imgQuality
import org.droidmate.deviceInterface.DeviceConstants.uiaDaemonParam_waitForInteractableTimeout
import org.droidmate.deviceInterface.DeviceConstants.uiaDaemonParam_waitForIdleTimeout
import org.junit.Test
import org.junit.runner.RunWith

import org.droidmate.deviceInterface.DeviceConstants.uiaDaemonParam_tcpPort
import org.droidmate.deviceInterface.DeviceConstants.uiaDaemon_logcatTag
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.backgroundScope
import android.support.test.InstrumentationRegistry.getInstrumentation
import org.droidmate.deviceInterface.DeviceConstants
import org.junit.Before


@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 19)
class UiAutomator2DaemonTest {

	@Before
	fun grantPermissions() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
		{
			getInstrumentation().uiAutomation.executeShellCommand(
					"pm grant " + DeviceConstants.uia2Daemon_packageName
							+ " android.permission.WRITE_EXTERNAL_STORAGE")
			getInstrumentation().uiAutomation.executeShellCommand(  // for whatever reason 'write' storage is not enough to write files to /sdcard
					"pm grant " + DeviceConstants.uia2Daemon_packageName
							+ " android.permission.READ_EXTERNAL_STORAGE")
			getInstrumentation().uiAutomation.executeShellCommand(
					"pm grant " + DeviceConstants.uia2Daemon_packageName
							+ " android.permission.MODIFY_AUDIO_SETTINGS")

			getInstrumentation().uiAutomation.executeShellCommand(
					"pm grant ${DeviceConstants.uia2Daemon_packageName}.test"
							+ " android.permission.WRITE_EXTERNAL_STORAGE")
			getInstrumentation().uiAutomation.executeShellCommand(  // for whatever reason 'write' storage is not enough to write files to /sdcard
					"pm grant ${DeviceConstants.uia2Daemon_packageName}.test"
							+ " android.permission.READ_EXTERNAL_STORAGE")
			getInstrumentation().uiAutomation.executeShellCommand(
					"pm grant ${DeviceConstants.uia2Daemon_packageName}.test"
							+ " android.permission.MODIFY_AUDIO_SETTINGS")
		}
	}

	@Test
	fun init() {

		Log.d(uiaDaemon_logcatTag, "start device driver")
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
		val enableDelayedImgFetch = if(extras.containsKey(uiaDaemonParam_delayedImgFetch))
			extras.get(uiaDaemonParam_delayedImgFetch).toString().toBoolean()
		else false
		val imgQuality = if(extras.containsKey(uiaDaemonParam_imgQuality))
			extras.get(uiaDaemonParam_imgQuality).toString().toInt()
		else 100

		Log.v(uiaDaemon_logcatTag, "$uiaDaemonParam_tcpPort=$tcpPort")


		val uiAutomatorDaemonDriver =
				if(extras.containsKey(uiaDaemonParam_enablePrintOuts)) {
					Log.d(uiaDaemon_logcatTag, "create automation")

					UiAutomator2DaemonDriver(waitForIdleTimeout, waitForInteractableTimeout,
							imgQuality, enableDelayedImgFetch,
							extras.get(uiaDaemonParam_enablePrintOuts).toString().toBoolean())
				}
				else UiAutomator2DaemonDriver(waitForIdleTimeout, waitForInteractableTimeout, imgQuality, enableDelayedImgFetch)

		val uiAutomator2DaemonServer = UiAutomator2DaemonServer(uiAutomatorDaemonDriver)

		Log.d(uiaDaemon_logcatTag, "uiAutomator2DaemonServer.start($tcpPort)")
		var serverThread: Thread? = null
		try {
			serverThread = uiAutomator2DaemonServer.start(tcpPort)
		} catch (t: Throwable) {
			Log.e(uiaDaemon_logcatTag, "uiAutomator2DaemonServer.start($tcpPort) / FAILURE", t)
		}

		if (serverThread == null)
			throw AssertionError()

		Log.i(uiaDaemon_logcatTag, "uiAutomator2DaemonServer.start($tcpPort) / SUCCESS")

		try {
			// Postpone process termination until the server thread finishes.
			serverThread.join()
			runBlocking {
				backgroundScope.coroutineContext[Job]!!.cancelAndJoin()
			}
		} catch (e: InterruptedException) {
			Log.wtf(uiaDaemon_logcatTag, e)
		}

		if (!uiAutomator2DaemonServer.isClosed)
			throw AssertionError()

		Log.i(uiaDaemon_logcatTag, "init: Shutting down UiAutomatorDaemon.")
	}
}