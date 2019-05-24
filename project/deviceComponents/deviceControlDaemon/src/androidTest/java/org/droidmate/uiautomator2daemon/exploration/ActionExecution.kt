package org.droidmate.uiautomator2daemon.exploration

import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.support.test.uiautomator.*
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import org.droidmate.deviceInterface.DeviceConstants
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.*
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.UiParser.Companion.computeIdHash
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.UiSelector.actableAppElem
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.UiSelector.isWebView
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

var idleTimeout: Long = 100
var interactiveTimeout: Long = 1000

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

var lastId = 0
var isWithinQueue = false
@Suppress("DEPRECATION")
suspend fun ExplorationAction.execute(env: UiAutomationEnvironment): Any {
	val idMatch: (Int) -> SelectorCondition = {idHash ->{ n: AccessibilityNodeInfo, xPath ->
		val layer = env.lastWindows.find { it.w.windowId == n.windowId }?.layer ?: n.window?.layer
		layer != null && idHash == computeIdHash(xPath, layer)
	}}
	Log.d(logTag, "START execution ${toString()}($id)")
	val result: Any = when(this) { // REMARK this has to be an assignment for when to check for exhaustiveness
		is Click -> {
			env.device.verifyCoordinate(x, y)
			env.device.click(x, y, interactiveTimeout).apply {
				delay(delay)
			}
		}
		is Tick -> {
			var success = UiHierarchy.findAndPerform(env, idMatch(idHash)){
				val newStatus = !it.isChecked
				it.isChecked = newStatus
				it.isChecked == newStatus
			}
			if(!success){
				env.device.verifyCoordinate(x, y)
				success = env.device.click(x, y, interactiveTimeout)
			}
			delay(delay)
			success
		}
		is ClickEvent ->
			UiHierarchy.findAndPerform(env, idMatch(idHash)) { nodeInfo ->				// do this for API Level above 19 (exclusive)
//				Log.d(logTag, "looking for click target, windows are ${env.getDisplayedWindows()}")
				nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)}.also {
					if(it) { delay(delay) } // wait for display update
					Log.d(logTag, "perform successful=$it")
				}
		is LongClickEvent -> UiHierarchy.findAndPerform(env, idMatch(idHash)) { nodeInfo ->				// do this for API Level above 19 (exclusive)
			nodeInfo.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)}.also {
			if(it) { delay(delay) } // wait for display update
			Log.d(logTag, "perform successful=$it")
		}
		is LongClick -> {
			env.device.verifyCoordinate(x, y)
			env.device.longClick(x, y, interactiveTimeout).apply {
				delay(delay)
			}
		}
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
				else true
			}//.also { if (it is Boolean && it) { delay(idleTimeout) } }// wait for display update (if no Fetch action)
		is TextInsert ->
			UiHierarchy.findAndPerform(env, idMatch(idHash)) { nodeInfo ->
				if(nodeInfo.isFocusable){
					nodeInfo.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
					Log.d(logTag,"focus input-field")
				}else if(nodeInfo.isClickable){
					nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
					Log.d(logTag, "click non-focusable input-field")
				}
				// do this for API Level above 19 (exclusive)
				val args = Bundle()
				args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
				nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args).also {
					//					if(it) { delay(idleTimeout) } // wait for display update
					Log.d(logTag, "perform successful=$it")
					if(sendEnter && !isWithinQueue){
						Log.d(logTag, "trigger enter")
						env.device.pressEnter()
					}  // when doing multiple action sending enter may trigger a continue button but not all elements are yet filled
					if(nodeInfo.isFocusable){
						nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
					}
					delay(delay)
				} }
		is RotateUI -> env.device.rotate(rotation, env.automation)
		is LaunchApp -> {
			env.device.launchApp(packageName, env, launchActivityDelay, timeout)
		}
		is Swipe -> env.device.twoPointAction(start,end){
			x0, y0, x1, y1 ->  env.device.swipe(x0, y0, x1, y1, stepSize)
		}
		is TwoPointerGesture ->	TODO("this requires a call on UiObject, which we currently do not match to our ui-extraction")
		is PinchIn -> TODO("this requires a call on UiObject, which we currently do not match to our ui-extraction")
		is PinchOut -> TODO("this requires a call on UiObject, which we currently do not match to our ui-extraction")
		is Scroll -> // TODO we may trigger the UiObject2 method instead
			UiHierarchy.findAndPerform(env, idMatch(idHash)) { nodeInfo ->				// do this for API Level above 19 (exclusive)
			nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)}.also {
			if(it) { delay(idleTimeout) } // wait for display update
			Log.d(logTag, "perform successful=$it")
		}
		is ActionQueue -> {
			var success = true
			isWithinQueue = true
			actions.forEachIndexed { i,action ->
				if(i==actions.size-1) isWithinQueue = false // reset var at the end of queue
				success = success &&
					action.execute(env).also{
						if(i<actions.size-1 &&
								((action is TextInsert && actions[i+1] is Click)
										|| action is Swipe)) {
							if(action is Swipe){
								Log.d(logTag, "delay after swipe")
								delay(delay)
							}
							getOrStoreImgPixels(env.captureScreen(), env, action.id)
						}
					}.let{ if(it is Boolean) it else true } }.apply{
				delay(delay)
				getOrStoreImgPixels(env.captureScreen(),env)
			}
		}
		else -> throw DeviceDaemonException("not implemented action $name was called in exploration/ActionExecution")
	}
	Log.d(logTag, "END execution of ${toString()} ($id)")
	return result
}


//REMARK keep the order of first wait for windowUpdate, then wait for idle, then extract windows to minimize synchronization issues with opening/closing keyboard windows
private suspend fun waitForSync(env: UiAutomationEnvironment, afterAction: Boolean) {
	try {
		env.lastWindows.firstOrNull { it.isApp() && !it.isKeyboard && !it.isLauncher }?.let {
			env.device.waitForWindowUpdate(it.w.pkgName, env.interactiveTimeout) //wait sync on focused window
		}

		debugT("wait for IDLE avg = ${time / max(1, cnt)} ms", {
			env.automation.waitForIdle(100, env.idleTimeout)
//		env.device.waitForIdle(env.idleTimeout) // this has a minimal delay of 500ms between events until the device is considered idle
		}, inMillis = true,
				timer = {
					Log.d(logTag, "time=${it / 1000000}")
					time += it / 1000000
					cnt += 1
				}) // this sometimes really sucks in performance but we do not yet have any reliable alternative
		debugOut("check if we have a webView", debugFetch)
		if (afterAction && UiHierarchy.any(env, cond = isWebView)) { // waitForIdle is insufficient for WebView's therefore we need to handle the stabilize separately
			debugOut("WebView detected wait for interactive element with different package name", debugFetch)
			UiHierarchy.waitFor(env, interactiveTimeout, actableAppElem)
		}
	} catch(e: java.util.concurrent.TimeoutException) {
		Log.e(logTag, "No idle state with idle timeout: 100ms within global timeout: ${env.idleTimeout}ms", e)
	}
}


/** compressing an image no matter the quality, takes long time therefore the option of storing these asynchronous
 * and transferring them later is available via configuration
 */
private fun getOrStoreImgPixels(bm: Bitmap?, env: UiAutomationEnvironment, actionId: Int = lastId): ByteArray = debugT("wait for screen avg = ${wt / max(1, wc)}",{
	when{ // if we couldn't capture screenshots
		bm == null ->{
			Log.w(logTag,"create empty image")
			ByteArray(0)
		}
		env.delayedImgTransfer ->{
			backgroundScope.launch(Dispatchers.IO){ // we could use an actor getting id and bitmap via channel, instead of starting another coroutine each time
				debugOut("create screenshot for action $actionId")
				val os = FileOutputStream(env.imgDir.absolutePath+ "/"+actionId+".jpg")
				bm.compress(Bitmap.CompressFormat.JPEG, env.imgQuality, os)
				os.close()
				bm.recycle()
			}
			ByteArray(0)
		}
		else -> UiHierarchy.compressScreenshot(bm).also{
			bm.recycle()
		}
	}
}, inMillis = true, timer = { wt += it / 1000000.0; wc += 1 })

private fun List<DisplayedWindow>.isHomeScreen() = count { it.isApp() }.let { nAppW ->
	nAppW == 0 || (nAppW==1 && any { it.isLauncher && it.isApp() })
}

private var time: Long = 0
private var cnt = 0
private var wt = 0.0
private var wc = 0
private const val debugFetch = false
private val isInteractive = { w: UiElementPropertiesI -> w.clickable || w.longClickable || w.checked!=null || w.isInputField}
suspend fun fetchDeviceData(env: UiAutomationEnvironment, afterAction: Boolean = false): DeviceResponse = coroutineScope{
	debugOut("start fetch execution",debugFetch)
	waitForSync(env,afterAction)

	var windows: List<DisplayedWindow> = env.getDisplayedWindows()
	var isSuccessful = true

	// fetch the screenshot if available
	var img = env.captureScreen() // could maybe use Espresso View.DecorativeView to fetch screenshot instead

	debugOut("start element extraction",debugFetch)
	// we want the ui fetch first as it is fast but will likely solve synchronization issues
	val uiHierarchy = UiHierarchy.fetch(windows,img).let{
		if(it == null || (!windows.isHomeScreen() && it.none (isInteractive)) ) {
			Log.w(logTag, "first ui extraction failed or no interactive elements were found \n $it, \n ---> start a second try")
			windows = env.getDisplayedWindows()
			img = env.captureScreen()
			UiHierarchy.fetch( windows, img ).also{ secondRes ->
				Log.d(logTag, "second try resulted in ${secondRes?.size} elements")
			}  //retry once for the case that AccessibilityNode tree was not yet stable
		} else it
	} ?: emptyList<UiElementPropertiesI>()	.also {
		isSuccessful = false
		Log.e(logTag, "could not parse current UI screen ( $windows )")
		throw java.lang.RuntimeException("UI extraction failed for windows: $windows")
	}
//	Log.d(logTag, "uiHierarchy = $uiHierarchy")
	uiHierarchy.also {
		debugOut("INTERACTIVE Element in UI = ${it.any (isInteractive)}")
	}

//			val xmlDump = UiHierarchy.getXml(device)
	val focusedWindow = windows.filter { it.isExtracted() && !it.isKeyboard }.let { appWindows ->
		( appWindows.firstOrNull{ it.w.hasFocus || it.w.hasInputFocus } ?: appWindows.firstOrNull())
	}
	val focusedAppPkg = focusedWindow	?.w?.pkgName ?: "no AppWindow detected"
	debugOut("determined focused window $focusedAppPkg inputF=${focusedWindow?.w?.hasInputFocus}, focus=${focusedWindow?.w?.hasFocus}")

	debugOut("started async ui extraction",debugFetch)

	debugOut("compute img pixels",debugFetch)
	val imgPixels =	getOrStoreImgPixels(img,env)

	var xml: String = "TODO parse widget list on Pc if we need the XML or introduce a debug property to enable parsing" +
			", because (currently) we would have to traverse the tree a second time"
	if(debugEnabled) xml = UiHierarchy.getXml(env)

	env.lastResponse = DeviceResponse.create( isSuccessful = isSuccessful, uiHierarchy = uiHierarchy,
		uiDump = xml,
		launchedActivity = env.launchedMainActivity,
		capturedScreen = img != null,
		screenshot = imgPixels,
		appWindows = windows.mapNotNull { if(it.isExtracted()) it.w else null },
		isHomeScreen = windows.isHomeScreen()
	)

	env.lastResponse
}


//private val deviceModel: String by lazy {
//		Log.d(DeviceConstants.uiaDaemon_logcatTag, "getDeviceModel()")
//		val model = Build.MODEL
//		val manufacturer = Build.MANUFACTURER
//		val fullModelName = "$manufacturer-$model/$api"
//		Log.d(DeviceConstants.uiaDaemon_logcatTag, "Device model: $fullModelName")
//		fullModelName
//	}

private fun UiDevice.verifyCoordinate(x:Int,y:Int){
	assert(x in 0 until displayWidth) { "Error on click coordinate invalid x:$x" }
	assert(y in 0 until displayHeight) { "Error on click coordinate invalid y:$y" }
}

private typealias twoPointStepableAction = (x0:Int,y0:Int,x1:Int,y1:Int)->Boolean
private fun UiDevice.twoPointAction(start: Pair<Int,Int>, end: Pair<Int,Int>, action: twoPointStepableAction):Boolean{
	val (x0,y0) = start
	val (x1,y1) = end
	verifyCoordinate(x0,y0)
	verifyCoordinate(x1,y1)
	return action(x0, y0, x1, y1)
}

private suspend fun UiDevice.minimizeMaximize(){
	val currentPackage = currentPackageName
	Log.d(logTag, "Original package name $currentPackage")

	pressRecentApps()
	// Cannot use wait for changes because it crashes UIAutomator
	delay(100) // avoid idle 0 which get the wait stuck for multiple seconds
	measureTimeMillis { waitForIdle(idleTimeout) }.let { Log.d(logTag, "waited $it millis for IDLE") }

	for (i in (0 until 10)) {
		pressRecentApps()

		// Cannot use wait for changes because it waits some interact-able element
		delay(100) // avoid idle 0 which get the wait stuck for multiple seconds
		measureTimeMillis { waitForIdle(idleTimeout) }.let { Log.d(logTag, "waited $it millis for IDLE") }

		Log.d(logTag, "Current package name $currentPackageName")
		if (currentPackageName == currentPackage)
			break
	}
}

private suspend fun UiDevice.launchApp(appPackageName: String, env: UiAutomationEnvironment, launchActivityDelay: Long, waitTime: Long): Boolean {
	var success = false
	// Launch the app
	val intent = env.context.packageManager
			.getLaunchIntentForPackage(appPackageName)

	// Clear out any previous instances
	intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

	// Update environment
	env.launchedMainActivity = try {
		intent?.component?.className ?: ""
	} catch (e: IllegalStateException) {
		""
	}
	debugOut("determined launch-able main activity for pkg=${env.launchedMainActivity}",debugFetch)

	measureTimeMillis {

		env.context.startActivity(intent)

		// Wait for the app to appear
		wait(Until.hasObject(By.pkg(appPackageName).depth(0)),
				waitTime)

		delay(launchActivityDelay)
		success = UiHierarchy.waitFor(env, interactiveTimeout, actableAppElem)
		// mute audio after app launch (for very annoying apps we may need a contentObserver listening on audio setting changes)
		val audio = env.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
		audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE,0)
		audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE,0)
		audio.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE,0)

	}.also { Log.d(logTag, "TIME: load-time $it millis") }
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
