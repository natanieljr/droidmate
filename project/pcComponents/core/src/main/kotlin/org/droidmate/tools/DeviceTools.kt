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

import org.droidmate.device.android_sdk.AaptWrapper
import org.droidmate.device.android_sdk.AdbWrapper
import org.droidmate.device.android_sdk.IAaptWrapper
import org.droidmate.device.android_sdk.IAdbWrapper
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.misc.SysCmdExecutor
import java.util.*

class DeviceTools @JvmOverloads constructor(cfg: ConfigurationWrapper = ConfigurationWrapper.getDefault(),
                                            substitutes: Map<Any, Any> = HashMap()) : IDeviceTools {

	override val aapt: IAaptWrapper
	override val adb: IAdbWrapper
	override val deviceDeployer: IAndroidDeviceDeployer
	override val apkDeployer: IApkDeployer
	override val deviceFactory: IAndroidDeviceFactory

	init {
		val sysCmdExecutor = SysCmdExecutor()
		aapt = if (substitutes.containsKey(IAaptWrapper::class))
			substitutes[IAaptWrapper::class] as IAaptWrapper
		else
			AaptWrapper(cfg, sysCmdExecutor)
		adb = if (substitutes.containsKey(IAdbWrapper::class))
			substitutes[IAdbWrapper::class] as IAdbWrapper
		else
			AdbWrapper(cfg, sysCmdExecutor)
		deviceFactory = if (substitutes.containsKey(IAndroidDeviceFactory::class))
			substitutes[IAndroidDeviceFactory::class] as IAndroidDeviceFactory
		else
			AndroidDeviceFactory(cfg, adb)
		deviceDeployer = AndroidDeviceDeployer(cfg, adb, deviceFactory)
		apkDeployer = ApkDeployer(cfg)
	}

}