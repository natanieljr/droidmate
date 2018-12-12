@file:Suppress("UsePropertyAccessSyntax")

package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Instrumentation
import android.app.Service
import android.app.UiAutomation
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.os.Environment
import android.os.RemoteException
import android.support.test.InstrumentationRegistry
import android.support.test.uiautomator.Configurator
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.getBounds
import android.support.test.uiautomator.getWindowRootNodes
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.delay
import org.droidmate.deviceInterface.exploration.DeviceResponse
import org.droidmate.uiautomator2daemon.exploration.measurePerformance
import org.droidmate.uiautomator2daemon.exploration.nullableDebugT
import java.io.File
import java.util.*
import kotlin.collections.HashMap

private const val debug = false

data class UiAutomationEnvironment(val idleTimeout: Long = 100, val interactiveTimeout: Long = 1000, val imgQuality: Int,
                                   val delayedImgTransfer: Boolean, val enablePrintouts: Boolean) {
	// The instrumentation required to run uiautomator2-daemon is
	// provided by the command: adb shell instrument <PACKAGE>/<RUNNER>
	val instr: Instrumentation = InstrumentationRegistry.getInstrumentation() ?: throw AssertionError(" could not get instrumentation")
	val automation: UiAutomation
	val device: UiDevice
	val context: Context
	private val keyboardPkgs: List<String> by lazy { computeKeyboardPkgs() }
	// Will be updated during the run, when the right command is sent (i.e. on AppLaunch)
	var launchedMainActivity: String = ""
	var lastResponse: DeviceResponse = DeviceResponse.empty

	var lastWindows : List<DisplayedWindow> private set

	val imgDir: File
	init {
		lastWindows = emptyList()
		// setting logcat debug/performance prints according to specified DM-2 configuration
		debugEnabled = enablePrintouts
		measurePerformance = measurePerformance && enablePrintouts
		debugOut("initialize environment", debug)

		// Disabling waiting for selector implicit timeout
		val c = Configurator.getInstance()
		c.waitForSelectorTimeout = 0L
		c.setUiAutomationFlags( c.getUiAutomationFlags() or UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES )

		automation = if(api>=24) instr.getUiAutomation(c.uiAutomationFlags) else instr.uiAutomation

		// Subscribe to window information, necessary to access the UiAutomation.windows
//		val info = automation.serviceInfo
//		info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
//		automation.setServiceInfo(info)
		/** this is already done within the device.getInstance(instrumentation) call if the API version allows for it*/


		this.context = instr.targetContext  // this is the context of the app we are going to start not of 'this' instrumentation (would be getContext() instead), otherwise we get an error on launch that we are not allowed to control Audio

		if (context == null) throw AssertionError(" could not determine instrumentation context")

		this.device = UiDevice.getInstance(instr)
		if (device == null) throw AssertionError(" could not determine UI-Device")

		if(delayedImgTransfer && context.checkSelfPermission(WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED) {
			debugOut("warn we have no storage permission, we may not be able to store & fetch screenshots")
		}
		imgDir =
				Environment.getExternalStorageDirectory().resolve("DM-2/images")
//				File(DeviceConstants.imgPath)
		imgDir.deleteRecursively()  // delete content from previous explorations
		if(!imgDir.exists()) imgDir.mkdirs()

		try {
			// Orientation is set initially to natural, however can be changed by action
			device.setOrientationNatural()
			device.freezeRotation()
		} catch (e: RemoteException) {
			e.printStackTrace()
		}
	}

	private fun computeKeyboardPkgs(): List<String>{
		val inputMng = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
		return inputMng.inputMethodList.map { it.packageName }.also {
			debugOut("computed keyboard packages $it", debug)
		}
	}

	//FIXME for some reason this does not report the pixels of the system navigation bar in the bottom of the display
	private fun getDisplayDimension(): DisplayDimension {
		debugOut("get display dimension",false)
		val p = Point()
		(InstrumentationRegistry.getInstrumentation().context.getSystemService(Service.WINDOW_SERVICE) as WindowManager)
				.defaultDisplay.getSize(p)
		debugOut("dimensions are $p",false)
		return DisplayDimension(p.x,p.y)
	}

	private fun selectKeyboardRoot(minY:Int, width: Int, height: Int): SelectorCondition {
		return {node,_ ->
			val b = node.getBounds(width,height)
			debugOut("check $b",false)
					b.top>minY
	}}

	private fun AccessibilityNodeInfo.isKeyboard() = keyboardPkgs.contains(this.packageName)

	val captureScreen: ()-> Bitmap? = { nullableDebugT("img capture time", {
		UiHierarchy.getScreenShot(automation)
	}, inMillis = true) }

	private suspend fun processWindows(w: AccessibilityWindowInfo, uncoveredC: MutableList<Rect>):DisplayedWindow?{
		debugOut("process ${w.id}", false)
		var outRect = Rect()
		// REMARK we wait that the app AND keyboard root nodes are available for synchronization reasons
		// otherwise we may extract an app widget as definedAsVisible which would have been hidden behind the input window
		if(w.root == null && w.type == AccessibilityWindowInfo.TYPE_APPLICATION || w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD){
			val deviceRoots = device.getWindowRootNodes()
			val root = deviceRoots.find{ it.windowId == w.id}
			if(root != null){ // this is usually the case for input methods (i.e. the keyboard window)
				root.getBoundsInParent(outRect)
				// this is necessary since newly appearing keyboards may otherwise take the whole screen and thus screw up our visibility analysis
				if(root.isKeyboard()) {
					uncoveredC.firstOrNull()?.let { r ->
						outRect.intersect(r)
						if (outRect == r){  // wrong keyboard boundaries reported
							debugOut("try to handle soft keyboard in front with $outRect", debug)
							UiHierarchy.findAndPerform(listOf(root),selectKeyboardRoot(r.top+1,r.width(),r.height()),retry = false,
									action = { node -> outRect = node.getBounds(r.width(),r.height() ); true })
						}
					}
				}
				debugOut("warn use device root for ${w.id} ${root.packageName}[$outRect] uncovered = $uncoveredC ${w.type}", debug)
				return DisplayedWindow(w, uncoveredC, outRect, root.isKeyboard(), root)
			}
			debugOut("warn no root for ${w.id} ${deviceRoots.map { "${it.packageName}" +" wId=${it.window?.id}"}}")
			return null
		}
		w.getBoundsInScreen(outRect)
		if(outRect.isEmpty && w.type == AccessibilityWindowInfo.TYPE_APPLICATION){
			debugOut("warn empty application window")
			return null
		}
		debugOut("process window ${w.id} ${w.root?.packageName ?: "no ROOT!! type=${w.type}"}", debug)
		return DisplayedWindow(w, uncoveredC, outRect, w.root?.isKeyboard()?:false)
	}

	private fun DisplayedWindow.canReuseFor(newW: AccessibilityWindowInfo): Boolean{
		val b = Rect()
		newW.getBoundsInScreen(b)
		return w.windowId == newW.id && layer == newW.layer
				&& bounds == b
				&& (!isExtracted() || newW.root != null).also {
			if(!it) 		debugOut("extracted = ${isExtracted()}; newW = ${newW.layer}, $b", debug)
		}
	}
	private var lastDisplayDimension = DisplayDimension(0,0)

	suspend fun getDisplayedWindows(): List<DisplayedWindow> {
		debugOut("compute displayCoordinates", false)
		// to compute which areas in the screen are not yet occupied by other windows (for UiElement-visibility)
		val displayDim = getDisplayDimension()
		val processedWindows = HashMap<Int,DisplayedWindow>() // keep track of already processed windowIds to prevent re-processing when we have to re-fetch windows due to missing accessibility roots

		var windows = automation.getWindows()
		var count = 0
		while(count++<10 && windows.none { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root != null  }){  // wait until app/home window is available
			delay(10)
			windows = automation.getWindows()
		}
		val uncoveredC = LinkedList<Rect>().apply { add(Rect(0,0,displayDim.width,displayDim.height)) }
//		/*
		if(lastDisplayDimension == displayDim){
			var canReuse = windows.size >= lastWindows.size // necessary since otherwise disappearing soft-keyboards would mark part of the app screen as invisible
			var c = 0
			while (canReuse && c<lastWindows.size && c<windows.size){
				with(lastWindows[c]) {
					val newW = windows[c++]
					val cnd = canReuseFor(newW)
					if(w.windowId == newW.id && !cnd) debugOut("try to reuse $this for ${newW.id}", debug)
					if (cnd) {
						debugOut("can reuse window ${w.windowId} ${w.pkgName} ${w.boundaries}", debug)
						processedWindows[w.windowId] = this.apply { if(isExtracted()) rootNode = newW.root }
					}
					else canReuse = false // no guarantees after we have one mismatching window
				}
			}
			if (!canReuse){ // wo could only partially reuse windows or none
				if(processedWindows.isNotEmpty()) debugOut("only partial reuse was possible", debug)
				processedWindows.values.forEach { it.bounds.visibleAxis(uncoveredC) } // then we need to mark the (reused) displayed window area as occupied
			}
		}
//		*/

		count = 0
		while(count++<10 && (processedWindows.size<windows.size)){
			var canContinue = true
			windows.forEach { window ->
				if(canContinue && !processedWindows.containsKey(window.id)){
					processWindows(window,uncoveredC)?.also {
						debugOut("created window ${it.w.windowId} ${it.w.pkgName}")
						processedWindows[it.w.windowId] = it }
							?: let{ delay(10); windows = automation.getWindows(); canContinue = false }
				}
			}
		}
		if (processedWindows.size<windows.size) debugOut("ERROR could not get rootNode for all windows[#dw=${processedWindows.size}, #w=${windows.size}] ${device.getWindowRootNodes().mapNotNull { it.packageName }}")
		return processedWindows.values.toList().also { displayedWindows ->
			lastDisplayDimension = displayDim // store results to be potentially reused
			lastWindows = displayedWindows
			debugOut("-- done displayed window computation [#windows = ${displayedWindows.size}] ${displayedWindows.joinToString(separator = ";\t ") { "${it.w.windowId}:(${it.layer})${it.w.pkgName}[${it.w.boundaries}] isK=${it.isKeyboard} isL=${it.isLauncher} isE=${it.isExtracted()} ${it.initialArea}" }}" )
		}
	}

	suspend fun isKeyboardOpen(): Boolean = getDisplayedWindows().any { it.isKeyboard }

	suspend fun getAppRootNodes() = 	getDisplayedWindows().mapNotNull { if(it.isApp()) it.rootNode else null }


}