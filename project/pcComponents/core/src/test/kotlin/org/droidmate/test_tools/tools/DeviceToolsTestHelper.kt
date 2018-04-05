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
package org.droidmate.test_tools.tools

import org.droidmate.device.android_sdk.IAaptWrapper
import org.droidmate.device.android_sdk.IAdbWrapper
import org.droidmate.configuration.Configuration
import org.droidmate.device.IAndroidDevice
import org.droidmate.android_sdk.AdbWrapperStub
import org.droidmate.test_tools.device_simulation.AndroidDeviceSimulator
import org.droidmate.tools.DeviceTools
import org.droidmate.tools.IAndroidDeviceFactory
import org.droidmate.tools.IDeviceTools

class DeviceToolsTestHelper {
	companion object {
		@JvmStatic
		fun buildForTesting(
				deviceToolsCfg: Configuration = Configuration.getDefault(),
				aaptWrapper: IAaptWrapper?,
				simulator: AndroidDeviceSimulator): IDeviceTools {
			val substitutes = mutableMapOf(
					IAdbWrapper::class to AdbWrapperStub(),
					IAndroidDeviceFactory::class to SimulatorFactory(simulator))

			if (aaptWrapper != null)
				substitutes[IAaptWrapper::class] = aaptWrapper

			return DeviceTools(deviceToolsCfg, substitutes.toMap())
		}
	}

	class SimulatorFactory(private val simulator: AndroidDeviceSimulator) : IAndroidDeviceFactory {
		override fun create(serialNumber: String): IAndroidDevice = simulator
	}
}
