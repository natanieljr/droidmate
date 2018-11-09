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

package org.droidmate.deviceInterface.exploration

import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.runBlocking
import java.io.Serializable


open class DeviceResponse private constructor(
		val isSuccessful: Boolean,
		val windowHierarchyDump: String,
		val widgets: List<UiElementPropertiesI>,
		val launchedMainActivityName: String,
		val isHomeScreen: Boolean,
		val screenshot: ByteArray,
		val appWindows: List<AppWindow>	//  to know the scrollable area dimensions
) : Serializable {

	var throwable: Throwable? = null

	init {
		assert(!this.appWindows.isEmpty())
	}

	companion object {

		fun create(isSuccessful: Boolean,
		           uiHierarchy: Deferred<List<UiElementPropertiesI>>, uiDump: String, launchedActivity: String,
		           screenshot: ByteArray,
		           appWindows: List<AppWindow>, isHomeScreen: Boolean
		): DeviceResponse = runBlocking{
			val widgets = uiHierarchy.await()

			DeviceResponse( isSuccessful = isSuccessful&&appWindows.isNotEmpty(), windowHierarchyDump = uiDump,
					widgets = widgets,
					launchedMainActivityName = launchedActivity,
					isHomeScreen = isHomeScreen,
					screenshot = screenshot,
					appWindows = appWindows)
		}

		@JvmStatic
		val empty: DeviceResponse by lazy {
			DeviceResponse( true,
					"empty",
					emptyList(),
					"",
					false,
					ByteArray(0),
					emptyList())
		}

		/*
		/**
		 * <p>
		 * Launcher name for the currently used device model.
		 *
		 * </p><p>
		 * @param deviceModel Device manufacturer + model as returned by {@link org.droidmate.uiautomator_daemon.UiAutomatorDaemonDriver#getDeviceModel()}
		 *
		 * </p>
		 */
		@Suppress("KDocUnresolvedReference")
		@JvmStatic
		private val androidLauncher:(deviceModel: String)-> String by lazy {{ deviceModel:String ->
			when {
				deviceModel.startsWith("Google-Pixel XL/") -> "com.google.android.apps.nexuslauncher"
				deviceModel.startsWith("Google-Android SDK built for x86/26") -> "com.google.android.apps.nexuslauncher"
				deviceModel.startsWith("Google-Android SDK built for x86/25") -> "com.google.android.apps.nexuslauncher"
				deviceModel.startsWith("Google-Android SDK built for x86") -> "com.android.launcher"
				deviceModel.startsWith("Google-AOSP on dragon/24") -> "com.android.launcher"
				deviceModel.startsWith("unknown-Android SDK built for x86") -> "com.android.launcher3"
				deviceModel.startsWith("samsung-GT-I9300") -> "com.android.launcher"
				deviceModel.startsWith("LGE-Nexus 5X") -> "com.google.android.googlequicksearchbox"
				deviceModel.startsWith("motorola-Nexus 6") -> "com.google.android.googlequicksearchbox"
				deviceModel.startsWith("asus-Nexus 7") -> "com.android.launcher"
				deviceModel.startsWith("htc-Nexus 9") -> "com.google.android.googlequicksearchbox"
				deviceModel.startsWith("samsung-Nexus 10") -> "com.android.launcher"
				deviceModel.startsWith("google-Pixel C") -> "com.android.launcher"
				deviceModel.startsWith("Google-Pixel C") -> "com.google.android.apps.pixelclauncher"
				deviceModel.startsWith("HUAWEI-FRD-L09") -> "com.huawei.android.launcher"
				deviceModel.startsWith("OnePlus-A0001") -> "com.cyanogenmod.trebuchet"
				else -> {
					logger.warn("Unrecognized device model of $deviceModel. Using the default.")
				"com.android.launcher"
				}
			}
		}}

		@JvmStatic
		private val getAndroidPackageName:(deviceModel: String) -> String by lazy {{ deviceModel:String ->
			when {
				deviceModel.startsWith("OnePlus-A0001") -> "com.cyanogenmod.trebuchet"
				else -> {
					"android"
				}
			}
		}}
		*/

	}

	override fun toString(): String {
		return when {
			this.isHomeScreen -> "<GUI state: home screen>"
			else -> "<GuiState windows=${appWindows.map { it.pkgName + "[${it.boundaries}]"}} Widgets count = ${widgets.size}>"
		}
	}

}
