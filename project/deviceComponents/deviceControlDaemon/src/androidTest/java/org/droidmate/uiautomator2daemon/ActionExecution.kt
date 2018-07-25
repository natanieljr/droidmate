package org.droidmate.uiautomator2daemon

import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.support.test.uiautomator.*
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.deviceInterface.DeviceResponse
import org.droidmate.deviceInterface.UiautomatorDaemonConstants
import org.droidmate.deviceInterface.guimodel.*
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.SelectorCondition
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.UiHierarchy
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.UiSelector.actableAppElem
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.UiSelector.isWebView
import kotlin.math.max
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

var idleTimeout: Long = 100
var interactableTimeout: Long = 1000

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

private const val logTag = UiautomatorDaemonConstants.deviceLogcatTagPrefix + "ActionExecution"

fun ExplorationAction.execute(device: UiDevice, context: Context, automation: UiAutomation): Any{
	Log.d(logTag, "START execution ${toString()}")
	val success: Any = when(this){ // REMARK this has to be an assignment for when to check for exhausiveness
		is Click ->{
			device.verifyCoordinate(x,y)
			device.click(x, y,interactableTimeout)
			runBlocking { delay(delay) }
		}
		is LongClick ->{
			device.verifyCoordinate(x,y)
			device.longClick(x, y,interactableTimeout)
			runBlocking { delay(delay) }
		}
		is SimulationAdbClearPackage, EmptyAction -> false /* should not be called on device */
		is GlobalAction ->
			when(actionType) {
				ActionType.PressBack -> device.pressBack()
				ActionType.PressHome -> device.pressHome()
				ActionType.EnableWifi -> {
					val wfm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
					wfm.setWifiEnabled(true).also {
						if (!it) Log.w(logTag, "Failed to ensure WiFi is enabled!")
					}
				}
				ActionType.MinimizeMaximize -> device.minimizeMaximize()
				ActionType.FetchGUI -> fetchDeviceData(device, idleTimeout)
				ActionType.Terminate -> false /* should never be transfered to the device */
				ActionType.PressEnter -> device.pressEnter()
				ActionType.CloseKeyboard ->
					if(UiHierarchy.any(device){node,_ -> node.packageName == "com.google.android.inputmethod.latin"})
						device.pressBack()
					else false
			}
		is TextInsert -> {
			val idMatch: SelectorCondition = { node, xPath ->
				idHash == xPath.hashCode()+rootIndex
			}
			UiHierarchy.findAndPerform(device,idMatch) { // do this for API Level above 19 (exclusive)
				val args = Bundle()
				args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
				it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args).also {
					if(it) runBlocking { delay(100) } // wait for display update
					Log.d(logTag, "perfom successfull=$it")
				} }.also {
				Log.d(logTag,"action was sucessfull=$it")
			}
		}
		is RotateUI -> device.rotate(rotation,automation)
		is LaunchApp -> device.launchApp(appLaunchIconName,context,timeout)
		is Swipe -> {
			val (x0,y0) = start
			val (x1,y1) = end
			device.verifyCoordinate(x0,y0)
			device.verifyCoordinate(x1,y1)
			device.swipe(x0, y0, x1, y1, 35)
		}
		is ActionQueue -> runBlocking {
			actions.forEach{ it.execute(device,context,automation); delay(delay)	 }
		}
	}
	Log.d(logTag, "END execution of ${toString()}")
	return success
}

private var time: Long = 0
private var cnt = 0
private var wt = 0.0
private var wc = 0
fun fetchDeviceData(device: UiDevice, timeout: Long =200): DeviceResponse {
	debugT("wait for IDLE avg = ${time / max(1,cnt)} ms", {
		device.waitForIdle(timeout)
		if (UiHierarchy.any(device,cond = isWebView)){ // waitForIdle is insufficient for WebView's therefore we need to handle the stabalize separately
			Log.d(logTag,"WebView detected wait for actable element with different package name")
			UiHierarchy.waitFor(device, interactableTimeout,actableAppElem)
		}
	},inMillis = true,
			timer = {
				Log.d(logTag,"time=${it/1000000}")
				time += it/1000000
				cnt += 1}) // this sometimes really sucks in perfomance but we do not yet have any reliable alternative

	val img = async{ debugT("img capture time", {
		delay(timeout/2) // try to ensure rendering really was complete (avoid half-transparent overlays or getting 'load-screens')
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

private val deviceModel: String by lazy {
		Log.d(UiautomatorDaemonConstants.uiaDaemon_logcatTag, "getDeviceModel()")
		val model = Build.MODEL
		val manufacturer = Build.MANUFACTURER
		val api = Build.VERSION.SDK_INT
		val fullModelName = "$manufacturer-$model/$api"
		Log.d(UiautomatorDaemonConstants.uiaDaemon_logcatTag, "Device model: $fullModelName")
		fullModelName
	}

private fun UiDevice.verifyCoordinate(x:Int,y:Int){
	assert(x in 0..(displayWidth - 1)) { "Error on click coordinate invalid x:$x" }
	assert(y in 0..(displayHeight - 1)) { "Error on click coordinate invalid y:$y" }
}

private fun UiDevice.minimizeMaximize(){
	val currentPackage = currentPackageName
	Log.d(logTag, "Original package name $currentPackage")

	pressRecentApps()
	// Cannot use wait for changes because it crashes UIAutomator
	runBlocking { delay(100) } // avoid idle 0 which get the wait stuck for multiple seconds
	measureTimeMillis { waitForIdle(idleTimeout) }.let { Log.d(logTag, "waited $it millis for IDLE") }

	for (i in (0 until 10)) {
		pressRecentApps()

		// Cannot use wait for changes because it waits some interact-able element
		runBlocking { delay(100) } // avoid idle 0 which get the wait stuck for multiple seconds
		measureTimeMillis { waitForIdle(idleTimeout) }.let { Log.d(logTag, "waited $it millis for IDLE") }

		Log.d(logTag, "Current package name $currentPackageName")
		if (currentPackageName == currentPackage)
			break
	}
}

private fun UiDevice.launchApp(appPackageName: String,context: Context,waitTime: Long){
	// Launch the app
	val intent = context.packageManager
			.getLaunchIntentForPackage(appPackageName)
	// Clear out any previous instances
	intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

	measureTimeMillis {
		context.startActivity(intent)

		// Wait for the app to appear
		wait(Until.hasObject(By.pkg(appPackageName).depth(0)),
				waitTime)
		val waitCnd = { node:AccessibilityNodeInfo, xpath:String ->
			UiSelector.ignoreSystemElem(node,xpath) &&
					(UiSelector.isActable(node,xpath) || UiSelector.permissionRequest(node,xpath))
		}
		UiHierarchy.waitFor(this, interactableTimeout,waitCnd)
	}.let { Log.d(logTag, "load-time $it millis") }
}

private fun UiDevice.rotate(rotation: Int,automation: UiAutomation){
	val currRotation = (displayRotation * 90)
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
	unfreezeRotation()
	automation.setRotation(newRotation)
}
