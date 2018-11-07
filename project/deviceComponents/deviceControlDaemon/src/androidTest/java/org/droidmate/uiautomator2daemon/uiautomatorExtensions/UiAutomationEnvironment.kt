@file:Suppress("UsePropertyAccessSyntax")

package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.app.Instrumentation
import android.app.Service
import android.app.UiAutomation
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.RemoteException
import android.support.test.InstrumentationRegistry
import android.support.test.uiautomator.Configurator
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.getWindowRootNodes
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.experimental.delay
import org.droidmate.deviceInterface.exploration.DeviceResponse
import org.droidmate.uiautomator2daemon.exploration.measurePerformance
import java.util.*
import kotlin.collections.HashMap

private const val debug = false

data class UiAutomationEnvironment(val idleTimeout: Long = 100, val interactiveTimeout: Long = 1000, val enablePrintouts: Boolean) {
	// The instrumentation required to run uiautomator2-daemon is
	// provided by the command: adb shell instrument <PACKAGE>/<RUNNER>
	private val instr: Instrumentation = InstrumentationRegistry.getInstrumentation() ?: throw AssertionError(" could not get instrumentation")
	val automation: UiAutomation
	val device: UiDevice
	val context: Context
	val keyboardPkgs: List<String> by lazy { computeKeyboardPkgs() }
	// Will be updated during the run, when the right command is sent (i.e. on AppLaunch)
	var appPackageName: String = ""
	var lastResponse: DeviceResponse = DeviceResponse.empty

	init {
		// setting logcat debug/performance prints according to specified DM-2 configuration
		debugEnabled = enablePrintouts
		measurePerformance = measurePerformance && enablePrintouts
		debugOut("initialize environment")

		// Disabling waiting for selector implicit timeout
		val c = Configurator.getInstance()
		c.waitForSelectorTimeout = 0L
		c.setUiAutomationFlags( c.getUiAutomationFlags() or UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES )

		automation = instr.getUiAutomation(c.uiAutomationFlags)

		// Subscribe to window information, necessary to access the UiAutomation.windows
//		val info = automation.serviceInfo
//		info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
//		automation.setServiceInfo(info)
		/** this is already done within the device.getInstance(instrumentation) call if the API version allows for it*/


		this.context = instr.targetContext  // this is the context of the app we are going to start not of 'this' instrumentation (would be getContext() instead), otherwise we get an error on launch that we are not allowed to control Audio

		if (context == null) throw AssertionError(" could not determine instrumentation context")

		this.device = UiDevice.getInstance(instr)
		if (device == null) throw AssertionError(" could not determine UI-Device")

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
			debugOut("computed keyboard packages $it", false)
		}
	}

	//FIXME for some reason this does not report the pixels of the system navigation bar in the bottom of the display
	private fun getDisplayDimension(): DisplayDimension {
		debugOut("get display dimmension",false)
		val p = Point()
		(InstrumentationRegistry.getInstrumentation().context.getSystemService(Service.WINDOW_SERVICE) as WindowManager)
				.defaultDisplay.getSize(p)
		debugOut("dimensions are $p",false)
		return DisplayDimension(p.x,p.y)
	}

	private fun processWindows(w: AccessibilityWindowInfo, uncoveredC: MutableList<Rect>):DisplayedWindow?{
		debugOut("process ${w.id}")
		if(w.root == null && w.type == AccessibilityWindowInfo.TYPE_APPLICATION){ //FIXME sometimes keyboard root is not yet available, maybe have a extractKeyboard config which is used here to decide if we want to wait
			debugOut("warn no root for ${w.id} ${device.getWindowRootNodes().mapNotNull { it.packageName }}")
			return null
		}
		debugOut("process window ${w.id} ${w.root?.packageName ?: "no ROOT!! type=${w.type}"}", true)
		return DisplayedWindow(w, uncoveredC, keyboardPkgs)
	}

	private fun DisplayedWindow.canReuseFor(newW: AccessibilityWindowInfo): Boolean{
		val b = Rect()
		newW.getBoundsInScreen(b)
		return w.windowId == newW.id && layer == newW.layer
				&& bounds == b
				&& (!isExtracted() || newW.root != null).also {
			if(!it) 		debugOut("extracted = ${isExtracted()}; newW = ${newW.layer}, $b")
		}
	}
	private var lastDisplayDimension = DisplayDimension(0,0)
	private var lastWindows = emptyList<DisplayedWindow>()
	suspend fun getDisplayedWindows(): List<DisplayedWindow> {
		debugOut("compute displayCoordinates", false)
		// to compute which areas in the screen are not yet occupied by other windows (for UiElement-visibility)
		val displayDim = getDisplayDimension()
		val processedWindows = HashMap<Int,DisplayedWindow>() // keep track of already processed windowIds to prevent re-processing when we have to re-fetch windows due to missing accessibility roots

		var windows = automation.getWindows()
		var count = 0
		while(count++<10 && windows.none { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }){  // wait until app/home window is available
			delay(10)
			windows = automation.getWindows()
		}
		val uncoveredC = LinkedList<Rect>().apply { add(Rect(0,0,displayDim.width,displayDim.height)) }
//		/*
		if(lastDisplayDimension == displayDim){
			var canReuse = true
			var c = 0
			while (canReuse && c<lastWindows.size && c<windows.size){
				with(lastWindows[c]) {
					val newW = windows[c++]
					val cnd = canReuseFor(newW)
					if(w.windowId == newW.id && !cnd) debugOut("try to reuse $this for ${newW.id}")
					if (cnd) {
						debugOut("can reuse window $this")
						processedWindows[w.windowId] = this.apply { if(isExtracted()) rootNode = newW.root }
					}
					else canReuse = false // no guarantees after we have one mismatching window
				}
			}
			if (!canReuse){ // wo could only partially reuse windows or none
				if(processedWindows.isNotEmpty()) debugOut("only partial reuse was possible")
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
		if (processedWindows.size<windows.size) debugOut("ERROR could not get rootNode for all windows ${device.getWindowRootNodes().mapNotNull { it.packageName }}")
		return processedWindows.values.toList().also { displayedWindows ->
			lastDisplayDimension = displayDim // store results to be potentially reused
			lastWindows = displayedWindows
			debugOut("-- done displayed window computation [#windows = ${displayedWindows.size}] ${displayedWindows.map { "${it.w.windowId}:${it.w.pkgName}[${it.initialArea}]" }}" )
		}
	}
}