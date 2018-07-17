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
import android.net.wifi.WifiManager
import android.support.test.uiautomator.*
import android.util.Log
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.UiHierarchy
import org.droidmate.uiautomator_daemon.DeviceResponse
import org.droidmate.uiautomator_daemon.UiAutomatorDaemonException
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants
import org.droidmate.uiautomator_daemon.guimodel.*
import kotlin.math.max
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis


/**
 * Created by J.H. on 05.02.2018.
 */
const val measurePerformance =
		true
//		false

@Suppress("ConstantConditionIf")
inline fun <T> nullableDebugT(msg: String, block: () -> T?, timer: (Long) -> Unit = {}, inMillis: Boolean = false): T? {
	var res: T? = null
	if (measurePerformance) {
		measureNanoTime {
			res = block.invoke()
		}.let {
			timer(it)
			Log.d(UiautomatorDaemonConstants.deviceLogcatTagPrefix + "performance","TIME: ${if (inMillis) "${(it / 1000000.0).toInt()} ms" else "${it / 1000.0} ns/1000"} \t $msg")
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
 * but these widgets are still in the uiautomator getXml. Therefore it may be that DroidMate
 * clicks on the keyboard thinking it clicked one of the widgets below it.
 * http://stackoverflow.com/questions/17223305/suppress-keyboard-after-setting-text-with-android-uiautomator
 * -> It seems there is no reliable way to suppress the keyboard.
 * -> TODO check if this is still an issue, as we re-fetch after each DeviceAction
 *
 * Even when triggering a "widget.click()" action, UIAutomator internally locates the center coordinates
 * of the widget and clicks it.
 */
internal sealed class DeviceAction {
	protected val logTag: String
		get() = "$LOGTAG-${this::class.simpleName}"

    var waitForIdleTimeout: Long = 2000
    var waitForInteractableTimeout: Long = 5000

	@Throws(UiAutomatorDaemonException::class)
	abstract fun execute(device: UiDevice, context: Context, automation: UiAutomation)

	companion object {
		const val LOGTAG = UiautomatorDaemonConstants.deviceLogcatTagPrefix + "DeviceAction"

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

		/*
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
		*/

		@JvmStatic private var time: Long = 0
		@JvmStatic private var cnt = 0
		@JvmStatic private var wt = 0.0
		@JvmStatic private var wc = 0
		fun fetchDeviceData(device: UiDevice, deviceModel: String, timeout: Long =200): DeviceResponse {
			debugT("wait for IDLE avg = ${time / max(1,cnt)} ms", {
				device.waitForIdle(timeout)
			},inMillis = true,
					timer = {
				Log.d(LOGTAG,"time=${it/1000000}")
				time += it/1000000
				cnt += 1}) // this sometimes really sucks in perfomance but we do not yet have any reliable alternative

			val img = async{ debugT("img capture time", {
				UiHierarchy.getScreenShot() },inMillis = true ) } // could maybe use Espresso View.DecorativeView to fetch screenshot instead
			val imgProcess = async { img.await().let{  s ->
				Triple(
//						ByteArray(0)
						UiHierarchy.compressScreenshot(s)
						, s.width, s.height).apply { s.recycle() }
			}}
			val uiHierarchy = async{ UiHierarchy.fetch(device)}
//			val xmlDump = runBlocking { UiHierarchy.getXml(device) }

			val (imgPixels,w,h) = debugT("wait for screen avg = ${wt/ max(1,wc)}",{ runBlocking {
				imgProcess.await()}
			}, inMillis = true, timer = { wt += it / 1000000.0; wc += 1} )
			return debugT("compute UI-dump", {

				DeviceResponse.create(uiHierarchy = uiHierarchy,
						uiDump =
						"TODO parse widget list on Pc if we need the XML or introduce a debug property to enable parsing" +
								", because (currently) we would have to traverse the tree a second time"
//									xmlDump
						, deviceModel = deviceModel,
						displayWidth = device.displayWidth, displayHeight = device.displayHeight,
						screenshot = imgPixels,
						width = w, height = h)
			},inMillis = true)
		}

	}

}

private class DevicePressBack : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		device.pressBack()
	}
}

private class DevicePressHome : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		device.pressHome()
	}
}

private class DeviceEnableWifi : DeviceAction() {

	/**
	 * Based on: http://stackoverflow.com/a/12420590/986533
	 */
	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		Log.d(logTag, "Ensuring WiFi is turned on.")
		val wfm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
		val wifiEnabled = wfm.setWifiEnabled(true)

		if (!wifiEnabled) Log.w(logTag, "Failed to ensure WiFi is enabled!")
	}
}


internal data class DeviceLaunchApp(val appPackageName: String) : DeviceAction() {

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
			device.waitForIdle(100)
			try{
				sleep(5000)
			}catch(e: Exception){
				e.printStackTrace()
			}
		}.let { Log.d(logTag, "load-time $it millis") }
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
		Log.d(logTag, "Swiping from (x,y) coordinates ($x0,$y0) to ($x1,$y1)")
		assert(x0 >= 0 && x0 < device.displayWidth) { "Error on swipe invalid x0:$x0" }
		assert(y0 >= 0 && y0 < device.displayHeight) { "Error on swipe invalid y0:$y0" }
		assert(x1 >= 0 && x1 < device.displayWidth) { "Error on swipe invalid x1:$x1" }
		assert(y1 >= 0 && y1 < device.displayHeight) { "Error on swipe invalid y1:$y1" }

		val success = device.swipe(x0, y0, x1, y1, 35)
		if (!success) Log.e(logTag, "Swipe failed: from (x,y) coordinates ($x0,$y0) to ($x1,$y1)")
		// we could issue a wait action on failure (e.g. waitExist for widget at position)
	}
}

private data class DeviceWaitAction(private val id: String, private val criteria: WidgetSelector) : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		Log.d(logTag, "Wait for element to exist" + this.toString())
		when (criteria) {
			WidgetSelector.ResourceId -> findByResId(id)
			WidgetSelector.ClassName -> findByClassName(id)
			WidgetSelector.ContentDesc -> findByDescription(id)
			WidgetSelector.XPath -> findByXPath(id)
		}.let {
			device.findObject(it).let {
				// REMARK this wait is necessary to avoid StackOverflowError in the QueryController, which would happen depending on when the UI view stabilizes
				measureTimeMillis { TODO("refactor to use new UiHierarchy")
					//device.wait(hasInteractive, 20000)
				}.let { Log.d(logTag, "waited $it millis for interactive element") }
				var success = false
				measureTimeMillis { success = it.waitForExists(10000) }.let { Log.d(logTag, "waited for exists $it millis with result $success") }
				if (!success) {
					Log.w(logTag, "WARN element $id not found")
					val clickable = device.findObjects(By.clickable(true)).map { o -> o.resourceName + ": ${o.visibleCenter}" }
					Log.d(logTag, "clickable elements: $clickable")
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
			Log.d(logTag, "execute action on target element with resId=$resId xPath=$xPath")
			val success = if (xPath.isNotEmpty()) executeAction(device, action, xPath) else {
				Log.d(logTag, "select element by resourceId")
				executeAction(device, action, resId, findByResId)
			}
			if (!success) {
				Log.w(logTag, "action on UiObject failed, try to perform on UiObject2 By.resourceId")
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
	}
}

private data class DeviceCoordinateClickAction(val x: Int, val y: Int) : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		debugT("executeAction avg = ${eTime / eCnt} ms ${this.javaClass.simpleName}", {

			Log.d(logTag, "Clicking coordinates ($x,$y)")
			assert(x >= 0 && x < device.displayWidth, { "Error on uncoveredCoord click invalid x:$x" })
			assert(y >= 0 && y < device.displayHeight, { "Error on uncoveredCoord click invalid y:$y" })
			Log.d(logTag, "Clicked coordinates $x, $y")
			device.click(x, y,waitForInteractableTimeout)
		},timer = {
			eTime += it/1000000
			eCnt += 1
		},inMillis = true)
	}
}

private data class DeviceLongClickAction(override val xPath: String, override val resId: String) : DeviceObjectAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		executeAction(device, { o -> o.longClick() }, { o -> o.longClick() })
	}
}

private data class DeviceCoordinateLongClickAction(val x: Int, val y: Int) : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		debugT("executeAction ${this.javaClass.simpleName}", {
			Log.d(logTag, "Long clicking coordinates ($x,$y)")

			assert(x >= 0 && x < device.displayWidth) { "Error on uncoveredCoord long click invalid x:$x" }
			assert(y >= 0 && y < device.displayHeight) { "Error on uncoveredCoord long click invalid y:$y" }

			Log.d(logTag, "Long clicked coordinates ($x, $y)")
			device.longClick(x,y,waitForInteractableTimeout)

		},inMillis = true)
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
		// do nothing`
	}
}

private class DeviceRotateUIAction(val rotation: Int) : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		val currRotation = (device.displayRotation * 90)
		Log.d(logTag, "Current rotation $currRotation")
		// Android supports the following rotations:
		// ROTATION_0 = 0;
		// ROTATION_90 = 1;
		// ROTATION_180 = 2;
		// ROTATION_270 = 3;
		// Thus, instead of 0-360 we have 0-3
		// The rotation calculations is: [(current rotation in degrees + rotation) / 90] % 4
		// Ex: curr = 90, rotation = 180 => [(90 + 360) / 90] % 4 => 1
		val newRotation = ((currRotation + rotation) / 90) % 4
		Log.d(logTag, "New rotation $newRotation")
		device.unfreezeRotation()
		automation.setRotation(newRotation)
	}
}

private class DeviceMinimizeMaximizeAction : DeviceAction() {

	override fun execute(device: UiDevice, context: Context, automation: UiAutomation) {
		val currentPackage = device.currentPackageName
		Log.d(logTag, "Original package name $currentPackage")

		device.pressRecentApps()
		// Cannot use wait for changes because it crashes UIAutomator
		runBlocking { delay(100) } // avoid idle 0 which get the wait stuck for multiple seconds
		measureTimeMillis { device.waitForIdle(waitForIdleTimeout) }.let { Log.d(logTag, "waited $it millis for IDLE") }

		for (i in (0 until 10)) {
			device.pressRecentApps()

			// Cannot use wait for changes because it waits some interact-able element
			runBlocking { delay(100) } // avoid idle 0 which get the wait stuck for multiple seconds
			measureTimeMillis { device.waitForIdle(waitForIdleTimeout) }.let { Log.d(logTag, "waited $it millis for IDLE") }

			Log.d(logTag, "Current package name ${device.currentPackageName}")
			if (device.currentPackageName == currentPackage)
				break
		}
	}

}