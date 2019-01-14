@file:Suppress("MemberVisibilityCanBePrivate")

package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.app.UiAutomation
import android.graphics.Bitmap
import android.graphics.Rect
import android.support.test.uiautomator.*
import android.util.Log
import android.util.Xml
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.communication.UiElementProperties
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.uiautomator2daemon.exploration.debugT
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.util.*
import kotlin.math.max
import kotlin.system.measureTimeMillis


@Suppress("unused")
object UiHierarchy : UiParser() {
	private const val LOGTAG = "droidmate/UiHierarchy"

	private var nActions = 0
	private var ut = 0L
	suspend fun fetch(windows: List<DisplayedWindow>, img: Bitmap?): List<UiElementPropertiesI>?
			= debugT(" compute UiNodes avg= ${ut / (max(nActions, 1) * 1000000)}", {
		val nodes = LinkedList<UiElementProperties>()

		try {

			val validImg = img.isValid(windows)// we cannot use an error prone image for ui extraction -> rather work without it completely
			//TODO check if this filters out all os windows but keeps permission request dialogues
//			debugOut("windows to extract: ${windows.map { "${it.isExtracted()}-${it.w.pkgName}:${it.w.windowId}[${visibleOuterBounds(it.area)}]" }}")
			windows.forEach {  w: DisplayedWindow ->
				if (w.isExtracted() && !w.isLauncher){  // for now we are not interested in the Launcher elements
					w.area = LinkedList<Rect>().apply { w.initialArea.forEach { add(it) } }
					if(w.rootNode == null) debugOut("ERROR root should not be null")
					check(w.rootNode != null) {"if extraction is enabled we have to have a rootNode"}
					createBottomUp(w, w.rootNode!!, parentXpath = "//", nodes = nodes, img = if(validImg) img else null)
					Log.d(LOGTAG, "${w.w.pkgName}:${w.w.windowId} ${visibleOuterBounds(w.initialArea)} " +
							"#elems = ${nodes.size} ${w.initialArea} empty=${w.initialArea.isEmpty()}")				}
			}
		} catch (e: Exception) {  // the accessibilityNode service may throw this if the node is no longer up-to-date
			Log.w("droidmate/UiDevice", "error while fetching widgets ${e.localizedMessage}\n last widget was ${nodes.lastOrNull()}")
			return null
		}
nodes
	}, inMillis = true, timer = { ut += it; nActions += 1 })


	fun getXml(device: UiDevice):String = debugT(" fetching gui Dump ", {
		StringWriter().use { out ->
			device.waitForIdle()

			val serializer = Xml.newSerializer()
			serializer.setFeature("http://xmlpull.org/v1/doc/modelFeatures.html#indent-output", true)
			serializer.setOutput(out)//, "UTF-8")

			serializer.startDocument("UTF-8", true)
			serializer.startTag("", "hierarchy")
			serializer.attribute("", "rotation", Integer.toString(device.displayRotation))

			device.apply(nodeDumper(serializer, device.displayWidth, device.displayHeight)
			) { serializer.endTag("", "node") }

			serializer.endTag("", "hierarchy")
			serializer.endDocument()
			serializer.flush()
			out.toString()
		}
	}, inMillis = true)

	/** check if this node fulfills the given condition and recursively check descendents if not **/
	suspend fun any(env: UiAutomationEnvironment, retry: Boolean=false, cond: SelectorCondition):Boolean{
		return findAndPerform(env, cond, retry) { true }
	}

	@JvmOverloads
	suspend fun findAndPerform(env: UiAutomationEnvironment, cond: SelectorCondition, retry: Boolean=true, action:((AccessibilityNodeInfo)->Boolean)): Boolean {
		return findAndPerform(env.getAppRootNodes(),cond,retry,action)
	}

		/** looks for a UiElement fulfilling [cond] and executes [action] on it.
	 * The search condition should be unique to avoid unwanted side-effects on other nodes which fulfill the same condition.
	 */
	@JvmOverloads
	suspend fun findAndPerform(roots: List<AccessibilityNodeInfo>, cond: SelectorCondition, retry: Boolean=true, action:((AccessibilityNodeInfo)->Boolean)): Boolean{
		var found = false
		var successfull = false

		debugOut("called findAndPerform (which will process the accessibility node tree until condition)")

		val processor:NodeProcessor = { node,_, xPath ->
			when{
				found -> false // we already found our target and performed our action -> stop searching
//				!isActive -> {Log.w(LOGTAG,"process became inactive"); false} //TODO this is still experimental
				!node.isVisibleToUser -> {
//					Log.d(LOGTAG,"node $xPath is invisible")
					false}
				!node.refresh() -> {Log.w(LOGTAG,"refresh on node $xPath failed"); false}
			// do not traverse deeper
			else -> {
				found = cond(node,xPath).also { isFound ->
					if(isFound){
						successfull = action(node).run { if(retry && !this){
							Log.d(LOGTAG,"action failed on $node\n with id ${computeIdHash(xPath,node.window.layer)}, try a second time")
							runBlocking { delay(20) }
							action(node)
							}else this
						}.also {
							Log.d(LOGTAG,"action returned $it")
						}
					}
				}
				!found // continue if condition is not fulfilled yet
				}
			}
		}
		roots.forEach { root ->
			processTopDown(root, processor = processor, postProcessor = { _ -> Unit })
		}
		if(retry && !found) {
			Log.d(LOGTAG,"didn't find target, try a second time")
			runBlocking { delay(20) }
			roots.forEach { root ->
				processTopDown(root, processor = processor, postProcessor = { _ -> Unit })
			}
		}
		Log.d(LOGTAG,"found = $found")
		return found && successfull
	}

	/** @paramt timeout amount of mili seconds, maximal spend to wait for condition [cond] to become true (default 10s)
	 * @return if the condition was fulfilled within timeout
	 * */
	@JvmOverloads
	fun waitFor(env: UiAutomationEnvironment, timeout: Long = 10000, cond: SelectorCondition): Boolean{
		return waitFor(env,timeout,10,cond)
	}
	/** @param pollTime time intervall (in ms) to recheck the condition [cond] */
	fun waitFor(env: UiAutomationEnvironment, timeout: Long, pollTime: Long, cond: SelectorCondition) = runBlocking{
		// lookup should only take less than 100ms (avg 50-80ms) if the UiAutomator did not screw up
		val scanTimeout = 100 // this is the maximal number of milliseconds, which is spend for each lookup in the hierarchy
		var time = 0.0
		var found = false

		while(!found && time<timeout){
			measureTimeMillis {
				with(async { any(env, retry=false, cond=cond) }) {
					var i = 0
					while(!isCompleted && i<scanTimeout){
						delay(10)
						i+=10
					}
					if (isCompleted)
						found = await()
					else cancel()
				}
			}.run{ time += this
				env.device.runWatchers() // to update the exploration view?
				if(!found && this<pollTime) delay(pollTime-this)
				Log.d(LOGTAG,"$found single wait iteration $this")
			}
		}
		found.also {
			Log.d(LOGTAG,"wait was successful: $found")
		}
	}

	fun getScreenShot(automation: UiAutomation): Bitmap? {
		var screenshot: Bitmap? = null
		debugT("first screen-fetch attempt ", {
			try {
//				screenshot = Screenshot.capture()?.bitmap // REMARK we cannot use this method as it would screw up the window handles in the UiAutomation
				screenshot = automation.takeScreenshot()
			} catch (e: Exception) {
				Log.w(LOGTAG, "exception on screenshot-capture")
			}
		}, inMillis = true)
		return screenshot.also {
			if (it == null)
				Log.w(LOGTAG,"no screenshot available")
		}
	}

	@JvmStatic private var t = 0.0
	@JvmStatic private var c = 0
	@JvmStatic
	fun compressScreenshot(screenshot: Bitmap): ByteArray = debugT("compress image avg = ${t / max(1, c)}", {
		var bytes = ByteArray(0)
		val stream = ByteArrayOutputStream()
		try {
			screenshot.setHasAlpha(false)
			screenshot.compress(Bitmap.CompressFormat.JPEG, 100, stream)
			stream.flush()

			bytes = stream.toByteArray()
			stream.close()
		} catch (e: Exception) {
			Log.w(LOGTAG, "Failed to compress screenshot: ${e.message}. Stacktrace: ${e.stackTrace}")
		}

		bytes
	}, inMillis = true, timer = { t += it / 1000000.0; c += 1 })


	private val windowFilter: (window:DisplayedWindow, value: Int) -> Int = { w,v -> if( w.isExtracted() ) v else 0 }
	private val windowWidth: (DisplayedWindow?)->Int = { window -> window?.w?.boundaries?.let{ windowFilter(window,it.leftX + it.width) } ?: 0 }
	private val windowHeight: (DisplayedWindow?)->Int = { window -> window?.w?.boundaries?.let{ windowFilter(window,it.topY + it.height) } ?: 0 }
	fun Bitmap?.isValid(appWindows:List<DisplayedWindow>): Boolean {
		return if (this != null) {
			try {
				val maxWidth = windowWidth(appWindows.maxBy(windowWidth))
				val maxHeight = windowHeight(appWindows.maxBy(windowHeight))

				(maxWidth == 0 && maxHeight == 0) || ((maxWidth <= this.width) && (maxHeight <= this.height))
			} catch (e: Exception) {
				Log.e(LOGTAG, "Error on screen validation ${e.message}. Stacktrace: ${e.stackTrace}")
				false
			}
		}
		else
			false
	}
}




