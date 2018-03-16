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

package org.droidmate.tools

import org.droidmate.android_sdk.*
import org.droidmate.configuration.Configuration
import org.droidmate.device.IAndroidDevice
import org.droidmate.device.IDeployableAndroidDevice
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.exploration.device.IRobustDevice
import org.droidmate.exploration.device.RobustDevice
import org.droidmate.logging.Markers
import org.droidmate.misc.BuildConstants
import org.droidmate.misc.DroidmateException
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants
import org.slf4j.LoggerFactory

import java.nio.file.Paths

class AndroidDeviceDeployer constructor(private val cfg: Configuration,
                                        private val adbWrapper: IAdbWrapper,
                                        private val deviceFactory: IAndroidDeviceFactory) : IAndroidDeviceDeployer {

    companion object {
        private val log = LoggerFactory.getLogger(AndroidDeviceDeployer::class.java)

        @Throws(DeviceException::class)
        @JvmStatic
        private fun tryResolveSerialNumber(adbWrapper: IAdbWrapper, usedSerialNumbers: List<String>, deviceIndex: Int): String {
            val devicesDescriptors = adbWrapper.getAndroidDevicesDescriptors()
            return getSerialNumber(devicesDescriptors, usedSerialNumbers, deviceIndex)
        }

        @JvmStatic
        private fun getSerialNumber(deviceDescriptors: List<AndroidDeviceDescriptor>, usedSerialNumbers: List<String>, deviceIndex: Int): String {
//    log.trace("Serial numbers of found android devices:")
//    assert deviceDescriptors?.size() > 0
//    deviceDescriptors.each {AndroidDeviceDescriptor add -> log.trace(add.deviceSerialNumber)}

            val unrecognizedNumbers = usedSerialNumbers.minus(deviceDescriptors.map { it.deviceSerialNumber })
            if (unrecognizedNumbers.isNotEmpty())
                throw DroidmateException("While obtaining new A(V)D serial number, DroidMate detected that one or more of the " +
                        "already used serial numbers do not appear on the list of serial numbers returned by the 'adb devices' command. " +
                        "This indicates the device(s) with these number most likely have been disconnected. Thus, DroidMate throws exception. " +
                        "List of the offending serial numbers: $unrecognizedNumbers")

            val unusedDescriptors = deviceDescriptors.filter { add -> add.deviceSerialNumber !in usedSerialNumbers }

            if (unusedDescriptors.isEmpty())
                throw DroidmateException("No unused A(V)D serial numbers have been found. List of all already used serial numbers: $usedSerialNumbers")

            if (unusedDescriptors.size < deviceIndex + 1)
                throw DroidmateException("Requested device with device no. ${deviceIndex + 1} but the no. of available devices is ${unusedDescriptors.size}.")

            val serialNumbers = unusedDescriptors.filter { add -> !add.isEmulator }.toList()

            return if (serialNumbers.size >= deviceIndex)
                serialNumbers[deviceIndex].deviceSerialNumber
            else
                unusedDescriptors.filter { add -> add.isEmulator }[deviceIndex].deviceSerialNumber
        }
    }

    /**
     * <p>
     * <i> --- This doc was last reviewed on 21 Dec 2013.</i>
     * </p><p>
     * Determines if the device accessed through this class is currently setup. The value of this field is modified
     * by {@link #trySetUp(IDeployableAndroidDevice)}  and {@link #tryTearDown(IDeployableAndroidDevice)}.
     *
     * </p><p>
     * Useful to be tested for in preconditions requiring for the device to be set-up.
     *
     */
    private var deviceIsSetup: Boolean = false

    // To make DroidMate work with multiple A(V)D, this list will have to be one for all AndroidDeviceDeployer-s, not one per inst.
    private val usedSerialNumbers: MutableList<String> = mutableListOf()

    /**
     * <p>
     * Setups android device for DroidMate purposes. Starts adb server if necessary, forwards ports, pushes uiautomator-daemon jar,
     * pushes monitor apk, pushes apiPoliciesFile, pushes portFile and starts uiautomator-daemon server.
     *
     * </p><p>
     * Remember to call {@link #tryTearDown} when done with the device.
     *
     * </p>
     * @throws DeviceException if any of the operation fails.
     */
    @Throws(DeviceException::class)
    private fun trySetUp(device: IDeployableAndroidDevice) {
        this.adbWrapper.startAdbServer()

        // Nataniel: Had to invert order, otherwise it crashes on the first time it's executed because the UiAutomator2Daemon was never installed on the device
        if (cfg.installAux) {
            device.reinstallUiautomatorDaemon()
            device.pushMonitorJar()
        }
        device.setupConnection()
        device.initModel()

        this.deviceIsSetup = true
    }

    /** <p>
     * Stops the uiautomator-daemon and removes its jar from the A(V)D. Call it after {@link #trySetUp}
     *
     * </p>
     * @throws DeviceException if any of the operations fails.
     * @see #trySetUp(IDeployableAndroidDevice)
     */
    @Throws(DeviceException::class)
    private fun tryTearDown(device: IDeployableAndroidDevice) {
        this.deviceIsSetup = false

        if (device.isAvailable()) {
            log.trace("Tearing down.")
            device.pullLogcatLogFile()
            device.closeConnection()

            if (cfg.uninstallAux) {
                if (cfg.androidApi == Configuration.api23) {
                    device.uninstallApk(UiautomatorDaemonConstants.uia2Daemon_testPackageName, true)
                    device.uninstallApk(UiautomatorDaemonConstants.uia2Daemon_packageName, true)
                } else
                    throw UnexpectedIfElseFallthroughError()
                device.removeJar(Paths.get(BuildConstants.monitor_on_avd_apk_name))
            }
        } else
            log.trace("Device is not available. Skipping tear down.")
    }

    override fun withSetupDevice(deviceSerialNumber: String, deviceIndex: Int, computation: (IRobustDevice) -> List<ApkExplorationException>): List<ExplorationException> {
        if (deviceSerialNumber.isNotEmpty())
            log.info("Setup device with deviceSerialNumber of $deviceSerialNumber")
        else
            log.info("Setup device with deviceIndex of $deviceIndex")

        val explorationExceptions: MutableList<ExplorationException> = mutableListOf()

        val data = setupDevice(deviceSerialNumber, deviceIndex)
        val throwable = data[2] as Throwable?
        if (throwable != null) {
            explorationExceptions.add(ExplorationException(throwable))
            return explorationExceptions
        }
        val device = data[0] as IRobustDevice
        val serialNumber = data[1] as String

        assert(explorationExceptions.isEmpty())
        try {
            val apkExplorationExceptions = computation(device)
            explorationExceptions.addAll(apkExplorationExceptions)
        } catch (computationThrowable: Throwable) {
            log.error("!!! Caught ${computationThrowable.javaClass.simpleName} in withSetupDevice($deviceIndex)->computation($device). " +
                    "This means ${ApkExplorationException::class.java.simpleName}s have been lost, if any! " +
                    "Adding the exception as a cause to an ${ExplorationException::class.java.simpleName}. " +
                    "Then adding to the collected exceptions list.\n" +
                    "The ${computationThrowable::class.java.simpleName}: $computationThrowable")

            explorationExceptions.add(ExplorationException(computationThrowable))
        } finally {
            log.debug("Finalizing: withSetupDevice($deviceIndex)->finally{} for computation($device)")
            try {
                tryTearDown(device)
                usedSerialNumbers -= serialNumber

            } catch (tearDownThrowable: Throwable) {
                log.warn(Markers.appHealth,
                        "! Caught ${tearDownThrowable.javaClass.simpleName} in withSetupDevice($deviceIndex)->tryTearDown($device). " +
                                "Adding as a cause to an ${ExplorationException::class.java.simpleName}. " +
                                "Then adding to the collected exceptions list.\n" +
                                "The ${tearDownThrowable::class.java.simpleName}: $tearDownThrowable")
                log.error(Markers.appHealth, tearDownThrowable.message, tearDownThrowable)

                explorationExceptions.add(ExplorationException(tearDownThrowable))
            }
            log.debug("Finalizing DONE: withSetupDevice($deviceIndex)->finally{} for computation($device)")
        }
        return explorationExceptions
    }

    private fun setupDevice(deviceSerialNumber: String, deviceIndex: Int): List<Any?> {
        try {
            var serialNumber = deviceSerialNumber
            if (serialNumber.isEmpty())
                serialNumber = AndroidDeviceDeployer.tryResolveSerialNumber(this.adbWrapper, this.usedSerialNumbers, deviceIndex)

            this.usedSerialNumbers.add(serialNumber)

            val device = robustWithReadableLogs(this.deviceFactory.create(serialNumber))

            trySetUp(device)

            return arrayListOf(device, serialNumber, null)

        } catch (setupDeviceThrowable: Throwable) {
            log.warn(Markers.appHealth,
                    "! Caught ${setupDeviceThrowable.javaClass.simpleName} in setupDevice(deviceIndex: $deviceIndex). " +
                            "Adding as a cause to an ${ExplorationException::class.java.simpleName}. Then adding to the collected exceptions list.")
            log.error(Markers.appHealth, setupDeviceThrowable.message, setupDeviceThrowable)

            return arrayListOf(null, null, setupDeviceThrowable)
        }
    }

    private fun robustWithReadableLogs(device: IAndroidDevice): IRobustDevice = RobustDevice(device, this.cfg)
}