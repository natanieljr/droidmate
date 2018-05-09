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

import org.droidmate.test_tools.DroidmateTestCase
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class DeviceTest : DroidmateTestCase() {
	// TODO Fix tests
	@Test
	fun dummy() {
	}
	/*@Category(RequiresDeviceSlow::class)
@Test
fun `reboots and restores connection`() {
		withApkDeployedOnDevice { device, _ ->

				device.getGuiSnapshot()
				device.rebootAndRestoreConnection()
				device.getGuiSnapshot()
		}
}

/**
 * This test exists for interactive debugging of known, not yet resolved bug. The behavior is as follows.
 *
 * - If everything works fine and the uiAutomatorDaemon server is alive, this test should succeed without any need to reinstall uiad apks
 * and setup connection. You can check if the server is allive as follows:
 *
 * adb shell
 * shell@flo:/ $ ps | grep uia
 * u0_a1027  31550 205   869064 38120 sys_epoll_ 00000000 S org.droidmate.uiautomator2daemon.UiAutomator2Daemon
 *
 * - If the server was somehow corrupted, rerunning this test will hang on the "ObjectInputStream", even if the installApk
 * and setupConnection methods are run. However, if the uninstall commands are run, then the test will succeed again without
 * problems. Not sure which uninstall is the important one, but I guess the one uninstalling.test
 *
 * Symptom observations: sometimes, even though server on the device says he is waiting to accept a socket, actually getting
 * a connected socket to it on client side does nothing. This means the server cannot be even stopped by socket, it has to be
 * killed by reinstalling the package.
 */
@Category(RequiresDevice::class)
@Test
fun `Restarts uiautomatorDaemon2 and communicates with it via TCP`()
{
	val cfg = getConfigurationApi23()
	val deviceTools = DeviceTools(cfg)
	val device = RobustDevice(
		deviceTools.deviceFactory.create(FirstRealDeviceSerialNumber(deviceTools.adb).toString()), cfg)

	if (device.isPackageInstalled(UiautomatorDaemonConstants.uia2Daemon_packageName))
		println("uia-daemon2 is installed.")
	else
	{
		println("uia-daemon2 is not installed: reinstallUiautomatorDaemon")
		device.reinstallUiautomatorDaemon()
	}

	println("setupConnection")
	device.setupConnection()

	println("Socket socket = Socket(\"localhost\", 59800)")
	val socket = Socket("localhost", 59800)

	println("val inputStream = ObjectInputStream(socket.inputStream)")
	val inputStream = ObjectInputStream(socket.inputStream)

	println("val outputStream = ObjectOutputStream(socket.outputStream)")
	val outputStream = ObjectOutputStream(socket.outputStream)

	println("outputStream.writeObject(DeviceCommand(DEVICE_COMMAND_GET_DEVICE_MODEL))")
	outputStream.writeObject(DeviceCommand(DEVICE_COMMAND_GET_DEVICE_MODEL))

	println("outputStream.flush()")
	outputStream.flush()

	println("inputStream.readObject()")
	inputStream.readObject()

	println("socket.close()")
	socket.close()

//    println("stop uiad"
//    device.stopUiaDaemon(false)

	println("END")
}

@Category(RequiresDevice::class)
@Test
fun `Print widgets of current GUI screen`() {
		withSetupDevice(getConfigurationApi23()) { _, _, device ->

				val gs = device.getGuiSnapshot().guiState
				println("widgets (#${gs.widgets.size}):")
				gs.widgets.forEach { println(it) }

				println("actionable widgets (#${gs.getActionableWidgets().size}):")
				gs.getActionableWidgets().forEach { println(it) }

				ArrayList()
		}
}

@Category(RequiresDevice::class)
@Test
fun `Launches app, then checks, clicks, stops and checks it again`() {
		withApkDeployedOnDevice { device, deployedApk ->

				device.launchApp(deployedApk.launchableActivityComponentName)
				assert(device.getGuiSnapshot().guiState.belongsToApp(deployedApk.packageName))

				// Act 1
				assert(device.appIsRunning(deployedApk.packageName))

				// Act 2
				assert(device.anyMonitorIsReachable())

				// Act 3
//          device.perform(newClickGuiDeviceAction(100, 100))
//          assert(device.getGuiSnapshot().guiStatus.belongsToApp(deployedApk.packageName))

				// Act 4
				device.clearPackage(deployedApk.packageName)
				assert(device.getGuiSnapshot().guiState.isHomeScreen)

				// Act 5
				assert(!device.appProcessIsRunning(deployedApk.packageName))

				// Act 6
				assert(!device.anyMonitorIsReachable())
		}
}

@Category(RequiresDevice::class)
@Test
fun `Obtains GUI snapshot for manual inspection`() {
		val deviceTools = DeviceTools(
						ConfigurationForTests()
										.setArgs(arrayListOf(Configuration.pn_androidApi, Configuration.api23))
										.forDevice()
										.get()
		)
		deviceTools.deviceDeployer.withSetupDevice("", 0) { device ->
				println(device.getGuiSnapshot().windowHierarchyDump)
				ArrayList()
		}
}

@Category(RequiresDevice::class)
@Test
fun `Sets up API23 compatible device and turns wifi on`() {
		val deviceTools = DeviceTools(getConfigurationApi23())
		deviceTools.deviceDeployer.withSetupDevice("", 0) { device ->
				device.perform(EnableWifi())
				ArrayList()
		}
}

private fun getConfigurationApi23(): Configuration {
		return ConfigurationForTests()
						.setArgs(arrayListOf(Configuration.pn_androidApi, Configuration.api23))
						.forDevice()
						.get()
}

private fun withApkDeployedOnDevice(computation: (IRobustDevice, IApk) -> Any) {
		val exceptions: MutableList<ApkExplorationException> = mutableListOf()
		val config = getConfigurationApi23monitoredInlinedApk()
		withSetupDevice(config) { cfg, deviceTools, device ->
				val apksProvider = ApksProvider(deviceTools.aapt)
				val apk = apksProvider.getApks(cfg.apksDirPath, cfg.apksLimit, cfg.apksNames, cfg.shuffleApks).first()

				deviceTools.apkDeployer.withDeployedApk(device, apk, { computation(device, apk) })
		}

		exceptions.forEach { it.printStackTrace() }

		assert(exceptions.isEmpty())
}

private fun getConfigurationApi23monitoredInlinedApk(): Configuration = ConfigurationForTests()
				.setArgs(arrayListOf(
								Configuration.pn_androidApi, Configuration.api23,
								Configuration.pn_apksNames, "[$BuildConstants.monitored_inlined_apk_fixture_api23_name]"))
				.forDevice()
				.get()

private fun withSetupDevice(cfg: Configuration, computation: (Configuration, IDeviceTools, IRobustDevice) -> List<ApkExplorationException>) {
		val deviceTools = DeviceTools(cfg)
		deviceTools.deviceDeployer.withSetupDevice("", 0) { device -> computation(cfg, deviceTools, device) }
}*/
}