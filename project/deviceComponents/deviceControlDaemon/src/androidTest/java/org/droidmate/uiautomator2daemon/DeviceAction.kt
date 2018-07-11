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

package org.droidmate.uiautomator2daemon

import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.wifi.WifiManager
import android.support.test.runner.screenshot.Screenshot
import android.support.test.uiautomator.*
import android.util.Log
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.uiautomator_daemon.DeviceResponse
import org.droidmate.uiautomator_daemon.UiAutomatorDaemonException
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants.uiaDaemon_logcatTag
import org.droidmate.uiautomator_daemon.guimodel.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

/**
 * Created by J.H. on 05.02.2018.
 */
const val measurePerformance = true

@Suppress("ConstantConditionIf")
inline fun <T> nullableDebugT(msg: String, block: () -> T?, timer: (Long) -> Unit = {}, inMillis: Boolean = false): T? {
	var res: T? = null
	if (measurePerformance) {
		measureNanoTime {
			res = block.invoke()
		}.let {
			timer(it)
			Log.d(uiaDaemon_logcatTag,"TIME: ${if (inMillis) "${(it / 1000000.0).toInt()} ms" else "${it / 1000.0} ns/1000"} \t $msg")
		}
	} else res = block.invoke()
	return res
}

inline fun <T> debugT(msg: String, block: () -> T, timer: (Long) -> Unit = {}, inMillis: Boolean = false): T {
	return nullableDebugT(msg, block, timer, inMillis)!!
}

/**
 * Triggers an action on the device.
 *
 * Known issue: In some cases the setting of text does open the keyboard and is hiding some widgets
 * but these widgets are still in the uiautomator dump. Therefore it may be that DroidMate
 * clicks on the keyboard thinking it clicked one of the widgets below it.
 * http://stackoverflow.com/questions/17223305/suppress-keyboard-after-setting-text-with-android-uiautomator
 * -> It seems there is no reliable way to suppress the keyboard.
 *
 * Even when triggering a "widget.click()" action, UIAutomator internally locates the center coordinates
 * of the widget and clicks it.
 */
internal sealed class DeviceAction {

    var waitForIdleTimeout: Long = 100
    var waitForInteractableTimeout: Long = 1000

	@Throws(UiAutomatorDaemonException::class)
	abstract fun execute(device: UiDevice, context: Context, automation: UiAutomation)

	companion object {

		@JvmStatic fun fromAction(a: Action, _waitForIdleTimeout: Long, _waitForInteractableTimeout: Long): DeviceAction? = with(a) {
			return when (this) {
				is WaitAction -> DeviceWaitAction(target, criteria)
				is LongClickAction -> DeviceLongClickAction(xPath, resId)
				is CoordinateLongClickAction -> DeviceCoordinateLongClickAction(x, y)
				is SwipeAction -> {
					if (start == null || dst == null) throw NotImplementedError("swipe executions currently only support point to point execution (TODO)")
					else DeviceSwipeAction(start!!, dst!!)
				}
				is TextAction -> DeviceTextAction(xPath, resId, text)
				is ClickAction -> DeviceClickAction(xPath, resId)
				is CoordinateClickAction -> DeviceCoordinateClickAction(x, y)
				is PressBack -> DevicePressBack()
				is PressHome -> DevicePressHome()
				is EnableWifi -> DeviceEnableWifi()
				is LaunchApp -> DeviceLaunchApp(appLaunchIconName)
				is FetchGUI -> DeviceFetchGUIAction()
				is RotateUI -> DeviceRotateUIAction(rotation)
				is MinimizeMaximize -> DeviceMinimizeMaximizeAction()
				is SimulationAdbClearPackage -> {
					null /* There's no equivalent device action */
				}
			}?.apply {
				waitForIdleTimeout = _waitForIdleTimeout
				waitForInteractableTimeout = _waitForInteractableTimeout
			}
		}

		@JvmStatic private var time: Long = 0
		@JvmStatic private var cnt = 1
		@JvmStatic private var lastDump:String = "ERROR"

		@Suppress("unused")
		@JvmStatic
		private fun getWindowHierarchyDump(device: UiDevice):String{
			return debugT(" fetching gui Dump ", {
				val os = ByteArrayOutputStream()
				try {
//					device.dumpWindowHierarchy(os)
					android.support.test.uiautomator.UiHierarchy.dump(device,os) // fixed version including invisible nodes in the dump
					os.flush()
					lastDump = os.toString(StandardCharsets.UTF_8.name())
					os.close()
				} catch (e: IOException) {
					os.close()
					throw UiAutomatorDaemonException(e)
				}
				lastDump
			}, inMillis = true)
		}

		private fun Int.binaryColor():Int = if (this > 127) 1 else 0

		private fun Bitmap.simplifyImg(): Bitmap =	Bitmap.createBitmap(width,height, config).let{ newImg ->
			for(y in 0 until height)
			for(x in 0 until width){
				getPixel(x,y).let { c ->
//					Log.d(uiaDaemon_logcatTag,"pixel $x,$y = $c DEBUG")
					val binColor = Color.red(c).binaryColor()+Color.green(c).binaryColor()+Color.blue(c).binaryColor() + Color.alpha(c).binaryColor()
//					Log.d(uiaDaemon_logcatTag,"binColorSum $binColor")
					if(binColor>1) Color.WHITE
					else Color.BLACK
				}.let{ newColor ->
//					Log.d(uiaDaemon_logcatTag,"newColor = $newColor")
					try {
						newImg.setPixel(x, y, newColor)
					}catch (e:Throwable){
						Log.e(uiaDaemon_logcatTag,e.message,e)
						throw e
					}
				}
			}
				newImg
		}

		@JvmStatic
		private suspend fun getScreenShot(simplify: Boolean = false): Bitmap {
			var screenshot = Screenshot.capture().bitmap
			//automation.takeScreenshot()
//					if(simplify) debugT("img modification", {screenshot = screenshot.simplifyImg()},inMillis = true)

			if (screenshot != null){
				Log.d(uiaDaemon_logcatTag,"screenshot failed")
				delay(100)
//				screenshot = automation.takeScreenshot()
				screenshot = Screenshot.capture().bitmap
			}
			return screenshot
		}

		private fun compressScreenshot(screenshot: Bitmap?): ByteArray {
			var bytes = ByteArray(0)
			val stream = ByteArrayOutputStream()
			try {
				debugT("compress time", {screenshot?.compress(Bitmap.CompressFormat.PNG, 100, stream)},inMillis = true)
				stream.flush()

				bytes = stream.toByteArray()

				stream.close()

			} catch (e: Exception) {
				Log.e(uiaDaemon_logcatTag, "Failed to compress screenshot: ${e.message}. Stacktrace: ${e.stackTrace}")
			}

			return bytes
		}

		@JvmStatic
		fun fetchDeviceData(device: UiDevice, deviceModel: String, simplify: Boolean = true): DeviceResponse {
			val image = async {
				val img = debugT("img capture time", { DeviceAction.getScreenShot(simplify)	},inMillis = true)
				Pair(img,DeviceAction.compressScreenshot(img))
			}
			val widgets = debugT(" compute UiNodes", {async{UiHierarchy.fetch(device)}}, inMillis = true)

			return debugT("compute UI-dump", {
				debugT("wait for screen",{ runBlocking { image.await()}}, inMillis = true)
						.let { (img, imgBytes) ->
							DeviceResponse.create(widgets, deviceModel, device.displayWidth, device.displayHeight, imgBytes,
									img.width, img.height)
						}
			}, inMillis = true)
		}

	}

	protected fun waitForChanges(device: UiDevice, actionSuccessful: Boolean = true) {
		if (actionSuccessful) {
			debugT("UI-stab avg = ${time / cnt} ms", {
				//            device.waitForWindowUpdate(null,defaultTimeout)
				runBlocking { delay(10) } // avoid idle 0 which get the wait stuck for multiple seconds
				measureTimeMillis { device.waitForIdle(waitForIdleTimeout) }.let { Log.d(uiaDaemon_logcatTag, "waited $it millis for IDLE") }
//					do {
//						val res = device.wait(hasInteractive, waitTimeout)  // this seams to sometimes take extremely long maybe because the dump is unstable?
//						Log.d(uiaDaemon_logcatTag, "wait-condition: $res")
//						getWindowHierarchyDump(device)
//					}while (res==null && lastDump == preDump) // we wait until we found something to interact with or the dump changed

				// if there is a permission dialogue we continue to handle it otherwise we try to wait for some interact-able app elements
				if(device.findObject(By.res("com.android.packageinstaller:id/permission_allow_button")) == null) {
					// exclude android internal elements
					nullableDebugT("wait for interactable",
						// this only checks for clickable but is much more reliable than a custom Search-Condition
						{device.wait(Until.findObject(By.clickable(true).pkg(Pattern.compile("^((?!com.android.systemui).)*$"))), waitForInteractableTimeout)},
						inMillis = true)
					// so if we need more we would have to implement something similar to `Until`
// DEBUG_CODE:
//					val c = device.findObjects(By.clickable(true).pkg(Pattern.compile("^((?!com.android.systemui).)*$"))).size
//					val cc = device.findObjects(By.clickable(true)).size
//					Log.e(uiaDaemon_logcatTag," found $c non-systemui clickable elements out of $cc")

					debugT("idle", {device.waitForIdle(waitForIdleTimeout)}, inMillis = true)  // even though one interactive element was found, the device may still be rendering the others -> wait for idle
				}
			}, timer = {
				time += it/1000000
				cnt += 1
			}, inMillis = true)
		}
	}
}

private class DevicePressBack : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		waitForChanges(device, device.pressBack())
	}
}

private class DevicePressHome : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		waitForChanges(device, device.pressHome())
	}
}

private class DeviceEnableWifi : DeviceAction() {

	/**
	 * Based on: http://stackoverflow.com/a/12420590/986533
	 */
	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		Log.d(uiaDaemon_logcatTag, "Ensuring WiFi is turned on.")
		val wfm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
		val wifiEnabled = wfm.setWifiEnabled(true)

		if (!wifiEnabled) Log.w(uiaDaemon_logcatTag, "Failed to ensure WiFi is enabled!")
	}
}

private data class DeviceLaunchApp(val appPackageName: String) : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		// Launch the app
		val intent = context.packageManager
				.getLaunchIntentForPackage(appPackageName)
		// Clear out any previous instances
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

		measureTimeMillis {
			context.startActivity(intent)

			// Wait for the app to appear
			device.wait(Until.hasObject(By.pkg(appPackageName).depth(0)),
					10000)
			device.wait(hasInteractive, 100)
		}.let { Log.d(uiaDaemon_logcatTag, "load-time $it millis") }
	}
}

private data class DeviceSwipeAction(val start: Pair<Int, Int>,
                                     val dst: Pair<Int, Int>,
                                     val xPath: String = "",
                                     val direction: String = "") : DeviceAction() {

	private val x0: Int inline get() = start.first
	private val y0: Int inline get() = start.second
	private val x1: Int inline get() = dst.first
	private val y1: Int inline get() = dst.second
	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		Log.d(uiaDaemon_logcatTag, "Swiping from (x,y) coordinates ($x0,$y0) to ($x1,$y1)")
		assert(x0 >= 0 && x0 < device.displayWidth) { "Error on swipe invalid x0:$x0" }
		assert(y0 >= 0 && y0 < device.displayHeight) { "Error on swipe invalid y0:$y0" }
		assert(x1 >= 0 && x1 < device.displayWidth) { "Error on swipe invalid x1:$x1" }
		assert(y1 >= 0 && y1 < device.displayHeight) { "Error on swipe invalid y1:$y1" }

		val success = device.swipe(x0, y0, x1, y1, 35)
		if (!success) Log.e(uiaDaemon_logcatTag, "Swipe failed: from (x,y) coordinates ($x0,$y0) to ($x1,$y1)")
		// we could issue a wait action on failure (e.g. waitExist for widget at position)
	}
}

private data class DeviceWaitAction(private val id: String, private val criteria: WidgetSelector) : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		Log.d(uiaDaemon_logcatTag, "Wait for element to exist" + this.toString())
		when (criteria) {
			WidgetSelector.ResourceId -> findByResId(id)
			WidgetSelector.ClassName -> findByClassName(id)
			WidgetSelector.ContentDesc -> findByDescription(id)
			WidgetSelector.XPath -> findByXPath(id)
		}.let {
			device.findObject(it).let {
				// REMARK this wait is necessary to avoid StackOverflowError in the QueryController, which would happen depending on when the UI view stabilizes
				measureTimeMillis { device.wait(hasInteractive, 20000) }.let { Log.d(uiaDaemon_logcatTag, "waited $it millis for interactive element") }
				var success = false
				measureTimeMillis { success = it.waitForExists(10000) }.let { Log.d(uiaDaemon_logcatTag, "waited for exists $it millis with result $success") }
				if (!success) {
					Log.w(uiaDaemon_logcatTag, "WARN element $id not found")
					val clickable = device.findObjects(By.clickable(true)).map { o -> o.resourceName + ": ${o.visibleCenter}" }
					Log.d(uiaDaemon_logcatTag, "clickable elements: $clickable")
				}
			}
		} // wait up to 10 seconds
	}
}

private var eTime: Long =0
private var eCnt: Int =1
private sealed class DeviceObjectAction : DeviceAction() {

	abstract val xPath: String
	abstract val resId: String


	protected fun executeAction(device: UiDevice, action: (UiObject) -> Boolean, action2: (UiObject2) -> Unit) {
		debugT("executeAction avg = ${eTime / eCnt} ms ${this.javaClass.simpleName}", {
			Log.d(uiaDaemon_logcatTag, "execute action on target element with resId=$resId xPath=$xPath")
			val success = if (xPath.isNotEmpty()) executeAction(device, action, xPath) else {
				Log.d(uiaDaemon_logcatTag, "select element by resourceId")
				executeAction(device, action, resId, findByResId)
			}
			if (!success) {
				Log.w(uiaDaemon_logcatTag, "action on UiObject failed, try to perform on UiObject2 By.resourceId")
				executeAction2(device, action2, resId)
			}
		}, timer = {
			eTime += it/1000000
			eCnt += 1
		}, inMillis = true)
	}
}

private data class DeviceClickAction(override val xPath: String, override val resId: String) : DeviceObjectAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		executeAction(device,
				{ o -> o.click() },
				{ o -> o.click() })
		waitForChanges(device)
	}
}

private data class DeviceCoordinateClickAction(val x: Int, val y: Int) : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		debugT("executeAction avg = ${eTime / eCnt} ms ${this.javaClass.simpleName}", {

			Log.d(uiaDaemon_logcatTag, "Clicking coordinates ($x,$y)")
			assert(x >= 0 && x < device.displayWidth, { "Error on uncoveredCoord click invalid x:$x" })
			assert(y >= 0 && y < device.displayHeight, { "Error on uncoveredCoord click invalid y:$y" })
			device.click(x, y)
			Log.d(uiaDaemon_logcatTag, "Clicked coordinates $x, $y")
		},timer = {
			eTime += it/1000000
			eCnt += 1
		},inMillis = true)
		waitForChanges(device)
	}
}

private data class DeviceLongClickAction(override val xPath: String, override val resId: String) : DeviceObjectAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		executeAction(device, { o -> o.longClick() }, { o -> o.longClick() })
		waitForChanges(device)
	}
}

private data class DeviceCoordinateLongClickAction(val x: Int, val y: Int) : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		debugT("executeAction ${this.javaClass.simpleName}", {
			Log.d(uiaDaemon_logcatTag, "Long clicking coordinates ($x,$y)")

			assert(x >= 0 && x < device.displayWidth, { "Error on uncoveredCoord long click invalid x:$x" })
			assert(y >= 0 && y < device.displayHeight, { "Error on uncoveredCoord long click invalid y:$y" })

			device.swipe(x, y, x, y, 100) // 100 ~ 2s. Empirical evaluation.
			Log.d(uiaDaemon_logcatTag, "Long clicked coordinates ($x, $y)")
		},inMillis = true)
		waitForChanges(device)
	}
}

private data class DeviceTextAction(override val xPath: String,
                                    override val resId: String,
                                    val text: String) : DeviceObjectAction() {

	val selector by lazy { if (xPath.isNotEmpty()) findByXPath(xPath) else findByResId(resId) }
	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		executeAction(device, { o -> o.setText(text) },
				{ o ->
					@Suppress("UsePropertyAccessSyntax")
					o.setText(text)
				})
		device.findObject(selector.text(text)).waitForExists(waitForIdleTimeout)  // wait until the text is set
	}
}

private class DeviceFetchGUIAction : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		waitForChanges(device)
		// do nothing
	}
}

private class DeviceRotateUIAction(val rotation: Int) : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		val currRotation = (device.displayRotation * 90)
		Log.d(uiaDaemon_logcatTag, "Current rotation $currRotation")

		// Android supports the following rotations:
		// ROTATION_0 = 0;
		// ROTATION_90 = 1;
		// ROTATION_180 = 2;
		// ROTATION_270 = 3;
		// Thus, instead of 0-360 we have 0-3
		// The rotation calculations is: [(current rotation in degrees + rotation) / 90] % 4
		// Ex: curr = 90, rotation = 180 => [(90 + 360) / 90] % 4 => 1
		val newRotation = ((currRotation + rotation) / 90) % 4
		Log.d(uiaDaemon_logcatTag, "New rotation $newRotation")
		device.unfreezeRotation()
		automation.setRotation(newRotation)

		waitForChanges(device)
	}
}

private class DeviceMinimizeMaximizeAction : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		val currentPackage = device.currentPackageName
		Log.d(uiaDaemon_logcatTag, "Original package name $currentPackage")

		device.pressRecentApps()
		// Cannot use wait for changes because it crashes UIAutomator
		runBlocking { delay(100) } // avoid idle 0 which get the wait stuck for multiple seconds
		measureTimeMillis { device.waitForIdle(waitForIdleTimeout) }.let { Log.d(uiaDaemon_logcatTag, "waited $it millis for IDLE") }

		for (i in (0 until 10)) {
			device.pressRecentApps()
			//waitForChanges(device)

			// Cannot use wait for changes because it waits some interact-able element
			runBlocking { delay(100) } // avoid idle 0 which get the wait stuck for multiple seconds
			measureTimeMillis { device.waitForIdle(waitForIdleTimeout) }.let { Log.d(uiaDaemon_logcatTag, "waited $it millis for IDLE") }

			Log.d(uiaDaemon_logcatTag, "Current package name ${device.currentPackageName}")
			if (device.currentPackageName == currentPackage)
				break
		}
	}

}