package org.droidmate.uiautomator2daemon.exploration

import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.support.test.uiautomator.*
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.coroutineScope
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.deviceInterface.DeviceConstants
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.*
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.UiSelector.actableAppElem
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.UiSelector.isWebView
import kotlin.math.max
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

var idleTimeout: Long = 100
var interactableTimeout: Long = 1000

var measurePerformance =	true

@Suppress("ConstantConditionIf")
inline fun <T> nullableDebugT(msg: String, block: () -> T?, timer: (Long) -> Unit = {}, inMillis: Boolean = false): T? {
	var res: T? = null
	if (measurePerformance) {
		measureNanoTime {
			res = block.invoke()
		}.let {
			timer(it)
			Log.d(DeviceConstants.deviceLogcatTagPrefix + "performance","TIME: ${if (inMillis) "${(it / 1000000.0).toInt()} ms" else "${it / 1000.0} ns/1000"} \t $msg")
		}
	} else res = block.invoke()
	return res
}

inline fun <T> debugT(msg: String, block: () -> T?, timer: (Long) -> Unit = {}, inMillis: Boolean = false): T {
	return nullableDebugT(msg, block, timer, inMillis) ?: throw RuntimeException("debugT is non nullable use nullableDebugT instead")
}

private const val logTag = DeviceConstants.deviceLogcatTagPrefix + "ActionExecution"

suspend fun ExplorationAction.execute(env: UiAutomationEnvironment): Any {
	Log.d(logTag, "START execution ${toString()}")
	val result: Any = when(this) { // REMARK this has to be an assignment for when to check for exhaustiveness
		is Click -> {
			env.device.verifyCoordinate(x, y)
			env.device.click(x, y, interactableTimeout).apply {
				runBlocking { delay(delay) }
			}
		}
		is LongClick -> {
			env.device.verifyCoordinate(x, y)
			env.device.longClick(x, y, interactableTimeout).apply {
				runBlocking { delay(delay) }
			}
		}
		is SimulationAdbClearPackage, EmptyAction -> false /* should not be called on device */
		is GlobalAction ->
			when (actionType) {
				ActionType.PressBack -> env.device.pressBack()
				ActionType.PressHome -> env.device.pressHome()
				ActionType.EnableWifi -> {
					val wfm = env.context.getSystemService(Context.WIFI_SERVICE) as WifiManager
					wfm.setWifiEnabled(true).also {
						if (!it) Log.w(logTag, "Failed to ensure WiFi is enabled!")
					}
				}
				ActionType.MinimizeMaximize -> {
					env.device.minimizeMaximize()
					true
				}
				ActionType.FetchGUI -> fetchDeviceData(env = env, afterAction = false)
				ActionType.Terminate -> false /* should never be transferred to the device */
				ActionType.PressEnter -> env.device.pressEnter()
				ActionType.CloseKeyboard -> 	if (env.isKeyboardOpen()) //(UiHierarchy.any(env.device) { node, _ -> env.keyboardPkgs.contains(node.packageName) })
						env.device.pressBack()
					else false
			}.also { if (it is Boolean && it) runBlocking { delay(idleTimeout) } }// wait for display update (if no Fetch action)
		is TextInsert -> {
			val idMatch: SelectorCondition = { _, xPath ->
				idHash == xPath.hashCode() + rootIndex
			}
			UiHierarchy.findAndPerform(env, idMatch) { nodeInfo ->
				// do this for API Level above 19 (exclusive)
				val args = Bundle()
				args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
				nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args).also {
					if(it) runBlocking { delay(idleTimeout) } // wait for display update
					Log.d(logTag, "perform successful=$it")
				} }.also {
				Log.d(logTag,"action was successful=$it")
			}
		}
		is RotateUI -> env.device.rotate(rotation, env.automation)
		is LaunchApp -> {
			env.device.launchApp(packageName, env, launchActivityDelay, timeout)
		}
		is Swipe -> {
			val (x0,y0) = start
			val (x1,y1) = end
			env.device.verifyCoordinate(x0,y0)
			env.device.verifyCoordinate(x1,y1)
			env.device.swipe(x0, y0, x1, y1, 35)
		}
		is ActionQueue -> runBlocking {
			var success = true
			actions.forEach { it -> success = success &&
					it.execute(env).apply{ delay(delay) } as Boolean }
		}
	}
	Log.d(logTag, "END execution of ${toString()}")
	return result
}

private val windowFilter: (window:DisplayedWindow, value: Int) -> Int = { w,v -> if( w.isExtracted() ) v else 0 }
private val windowWidth: (DisplayedWindow?)->Int = { window -> window?.w?.boundaries?.let{ windowFilter(window,it.leftX + it.width) } ?: 0 }
private val windowHeight: (DisplayedWindow?)->Int = { window -> window?.w?.boundaries?.let{ windowFilter(window,it.topY + it.height) } ?: 0 }
private fun Bitmap?.isValid(appWindows:List<DisplayedWindow>): Boolean {
	return if (this != null) {
		try {
			debugOut("check screenshot sanity", debugFetch)
			val maxWidth = windowWidth(appWindows.maxBy(windowWidth))
			val maxHeight = windowHeight(appWindows.maxBy(windowHeight))

			(maxWidth == 0 && maxHeight == 0) || ((maxWidth <= this.width) && (maxHeight <= this.height))
		} catch (e: Exception) {
			Log.e(logTag, "Error on screen validation ${e.message}. Stacktrace: ${e.stackTrace}")
			false
		}
	}
	else
		false
}

private var time: Long = 0
private var cnt = 0
private var wt = 0.0
private var wc = 0
private const val debugFetch = false
private val actableRefetch = {w: UiElementPropertiesI -> w.clickable || w.longClickable || w.isInputField}
suspend fun fetchDeviceData(env: UiAutomationEnvironment, afterAction: Boolean = true): DeviceResponse = coroutineScope{
	debugOut("start fetch execution",debugFetch)
	env.lastWindows.firstOrNull { it.isExtracted() && !it.isKeyboard }?.let {
		env.device.waitForWindowUpdate(it.w.pkgName, env.interactiveTimeout) //wait sync on focused window
	}

		debugT("wait for IDLE avg = ${time / max(1, cnt)} ms", {
			env.device.waitForIdle(env.idleTimeout)
			Log.d(logTag,"check if we have a webView")
			if (afterAction && UiHierarchy.any(env, cond = isWebView)) { // waitForIdle is insufficient for WebView's therefore we need to handle the stabilize separately
				Log.d(logTag, "WebView detected wait for interactive element with different package name")
				UiHierarchy.waitFor(env, interactableTimeout, actableAppElem)
			}
		}, inMillis = true,
				timer = {
					Log.d(logTag, "time=${it / 1000000}")
					time += it / 1000000
					cnt += 1
				}) // this sometimes really sucks in perfomance but we do not yet have any reliable alternative

	//REMARK keep the order of first wait for windowUpdate, then wait for idle, then extract windows to minimize synchronization issues with opening/closing keyboard windows
		var windows: List<DisplayedWindow> = debugT("compute windows",  { env.getDisplayedWindows()}, inMillis = true)

		debugOut("returned to fetch method",debugFetch)
		var isSuccessful = true

		debugOut("started async img capture",debugFetch)
		val uiHierarchy = async{
			debugOut("start element extraction",debugFetch)
			UiHierarchy.fetch(windows).let{
				if(it == null || it.none (actableRefetch) ) {
					Log.d(logTag, "first ui extraction failed, start a second try")
					windows = debugT("compute windows",  { env.getDisplayedWindows()}, inMillis = true)
					UiHierarchy.fetch( windows )  //retry once for the case that AccessibilityNode tree was not yet stable
				}else it
			}.also {
				debugOut("INTERACTIVE Element in UI = ${it?.any (actableRefetch)}")
			} ?:
			emptyList<UiElementPropertiesI>()	.also { isSuccessful = false }
		}
//			val xmlDump = runBlocking { UiHierarchy.getXml(device) }
	val focusedWindow = windows.filter { it.isExtracted() && !it.isKeyboard }.let { appWindows ->
		( appWindows.firstOrNull{ it.w.hasFocus || it.w.hasInputFocus } ?: appWindows.firstOrNull())
	}
	val focusedAppPkg = focusedWindow	?.w?.pkgName ?: "no AppWindow detected"
	debugOut("determined focused window $focusedAppPkg inputF=${focusedWindow?.w?.hasInputFocus}, focus=${focusedWindow?.w?.hasFocus}")

		debugOut("started async ui extraction",debugFetch)
		val imgProcess = async {
			uiHierarchy.await() // we want the ui fetch first as it is fast but will likely solve synchronization issues
			val img = async{
				debugOut("start img capture",debugFetch)
				nullableDebugT("img capture time", {
					//FIXME we need a better detection when the screen was rendered maybe via WindowContentChanged event
//			delay(env.idleTimeout/2) // try to ensure rendering really was complete (avoid half-transparent overlays or getting 'load-screens')
					UiHierarchy.getScreenShot(env.idleTimeout, env.automation).also {
						isSuccessful = it.isValid(windows)
					}
				}, inMillis = true)
			} // could maybe use Espresso View.DecorativeView to fetch screenshot instead
//TODO the compress is to be removed for delayed img transmission
			img.await()?.let{  s ->
				UiHierarchy.compressScreenshot(s).apply {
					s.recycle()
				}
			} ?: ByteArray(0) // if we couldn't capture screenshots
					.also { Log.w(logTag,"create empty image") }
		}
		debugOut("compute img pixels",debugFetch)
		val imgPixels = debugT("wait for screen avg = ${wt / max(1, wc)}",
				{
					imgProcess.await()
				}, inMillis = true, timer = { wt += it / 1000000.0; wc += 1 })

		env.lastResponse = debugT("compute UI-dump", {
			DeviceResponse.create( isSuccessful = isSuccessful, uiHierarchy = uiHierarchy,
					uiDump =
					"TODO parse widget list on Pc if we need the XML or introduce a debug property to enable parsing" +
							", because (currently) we would have to traverse the tree a second time"
//									xmlDump
					, launchedActivity = env.launchedMainActivity,
					screenshot = imgPixels,
					appWindows = windows.mapNotNull { if(it.isExtracted()) it.w else null },
					isHomeScreen = windows.count { it.isApp() } ==1 && windows.any { it.isLauncher && it.isApp() }
			)
		}, inMillis = true)

		return@coroutineScope env.lastResponse

}
//
//private val deviceModel: String by lazy {
//		Log.d(DeviceConstants.uiaDaemon_logcatTag, "getDeviceModel()")
//		val model = Build.MODEL
//		val manufacturer = Build.MANUFACTURER
//		val fullModelName = "$manufacturer-$model/$api"
//		Log.d(DeviceConstants.uiaDaemon_logcatTag, "Device model: $fullModelName")
//		fullModelName
//	}

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

private fun UiDevice.launchApp(appPackageName: String, env: UiAutomationEnvironment, launchActivityDelay: Long, waitTime: Long): Boolean {
	var success = false
	// Launch the app
	val intent = env.context.packageManager
			.getLaunchIntentForPackage(appPackageName)
	// Clear out any previous instances
	intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

	// Update environment
	env.launchedMainActivity = try {
		intent.component.className
	} catch (e: IllegalStateException) {
		""
	}
	debugOut("determined launch-able main activity for pkg=${env.launchedMainActivity}",debugFetch)

	measureTimeMillis {

		env.context.startActivity(intent)

		// Wait for the app to appear
		wait(Until.hasObject(By.pkg(appPackageName).depth(0)),
				waitTime)

		runBlocking { delay(launchActivityDelay) }
		success = UiHierarchy.waitFor(env, interactableTimeout, actableAppElem)
		// mute audio after app launch (for very annoying apps we may need a contentObserver listening on audio setting changes)
		val audio = env.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
		audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE,0)
		audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE,0)
		audio.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE,0)

	}.also { Log.d(logTag, "load-time $it millis") }
	return success
}

private fun UiDevice.rotate(rotation: Int,automation: UiAutomation):Boolean{
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
	return automation.setRotation(newRotation)
}
