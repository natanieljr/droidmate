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

package org.droidmate.deviceInterface

import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.io.Serializable
import org.droidmate.deviceInterface.guimodel.WidgetData


open class DeviceResponse private constructor(val windowHierarchyDump: String,
                                              val topNodePackageName: String,
                                              val widgets: List<WidgetData>,
                                              val androidLauncherPackageName: String,
                                              val androidPackageName: String,
                                              val deviceDisplayWidth: Int,
                                              val deviceDisplayHeight: Int,
                                              val screenshot: ByteArray,
                                              val screenshotWidth: Int,
                                              val screenshotHeight: Int,
                                              val appSize: Pair<Int,Int>,
                                              val statusBarSize: Int) : Serializable {

	var throwable: Throwable? = null
	private val resIdRuntimePermissionDialog = "com.android.packageinstaller:id/dialog_container"

	init {
		assert(!this.topNodePackageName.isEmpty())
	}

	companion object {
		private val log by lazy { LoggerFactory.getLogger(DeviceResponse::class.java) }

		@JvmStatic
		val empty: DeviceResponse by lazy {
			DeviceResponse("",
					"empty",
					emptyList(),
					"",
					"",
					0,
					0,
					ByteArray(0),
					0,
					0,
					Pair(0,0),0)
		}

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
					log.warn("Unrecognized device model of $deviceModel. Using the default.")
				"com.android.launcher"
				}
			}
		}}

		@JvmStatic
		private val getAndroidPackageName:(deviceModel: String)-> String by lazy {{ deviceModel:String ->
			when {
				deviceModel.startsWith("OnePlus-A0001") -> "com.cyanogenmod.trebuchet"
				else -> {
					"android"
				}
			}
		}}

		fun create(uiHierarchy: Deferred<List<WidgetData>>, uiDump: String, deviceModel: String, displayWidth: Int, displayHeight: Int, screenshot: ByteArray, width: Int, height: Int, appArea: Pair<Int,Int>, sH: Int): DeviceResponse = runBlocking{
			val widgets = uiHierarchy.await()
			DeviceResponse(windowHierarchyDump = uiDump,
					topNodePackageName = widgets.findLast { it.packageName != "com.google.android.inputmethod.latin" } // avoid the keyboard to be falesly recognized as packagename
							?.packageName ?: "No Widgets",
					widgets = widgets, androidPackageName = getAndroidPackageName(deviceModel),
					deviceDisplayWidth = displayWidth, deviceDisplayHeight = displayHeight, screenshot = screenshot, screenshotWidth = width, screenshotHeight = height,
					appSize = appArea, statusBarSize = sH)
		}
	}

	override fun toString(): String {
		return when {
			this.isHomeScreen -> "<GUI state: home screen>"
			this.isAppHasStoppedDialogBox -> "<GUI state of \"App has stopped\" dialog box.>"// OK widget enabled: ${this.okWidget.enabled}>"
			this.isRequestRuntimePermissionDialogBox -> "<GUI state of \"Runtime permission\" dialog box.>"// Allow widget enabled: ${this.allowWidget.enabled}>"
			this.isCompleteActionUsingDialogBox -> "<GUI state of \"Complete action using\" dialog box.>"
			this.isSelectAHomeAppDialogBox -> "<GUI state of \"Select a home app\" dialog box.>"
			this.isUseLauncherAsHomeDialogBox -> "<GUI state of \"Use Launcher as Home\" dialog box.>"
			else -> "<GuiState pkg=$topNodePackageName Widgets count = ${widgets.size}>"
		}
	}

	val isHomeScreen: Boolean
		get() = topNodePackageName.startsWith(androidLauncherPackageName) && !widgets.any { it.text == "Widgets" }

	val isAppHasStoppedDialogBox: Boolean
		get() = topNodePackageName == androidPackageName &&
				(widgets.any { it.resourceId == "android:id/aerr_close" } &&
						widgets.any { it.resourceId == "android:id/aerr_wait" }) ||
				(widgets.any { it.text == "OK" } &&
						!widgets.any { it.text == "Just once" })

	val isCompleteActionUsingDialogBox: Boolean
		get() = !isSelectAHomeAppDialogBox &&
				!isUseLauncherAsHomeDialogBox &&
				topNodePackageName == androidPackageName &&
				widgets.any { it.text == "Just once" }

	val isSelectAHomeAppDialogBox: Boolean
		get() = topNodePackageName == androidPackageName &&
				widgets.any { it.text == "Just once" } &&
				widgets.any { it.text == "Select a Home app" }

	val isUseLauncherAsHomeDialogBox: Boolean
		get() = topNodePackageName == androidPackageName &&
				widgets.any { it.text == "Use Launcher as Home" } &&
				widgets.any { it.text == "Just once" } &&
				widgets.any { it.text == "Always" }

	val isRequestRuntimePermissionDialogBox: Boolean
		get() = widgets.any { it.resourceId == resIdRuntimePermissionDialog }

	fun belongsToApp(appPackageName: String): Boolean = this.topNodePackageName == appPackageName
}