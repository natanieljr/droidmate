@file:Suppress("MemberVisibilityCanBePrivate")

package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.graphics.Bitmap
import android.support.test.runner.screenshot.Screenshot
import android.support.test.uiautomator.NodeProcessor
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.apply
import android.support.test.uiautomator.getNonSystemRootNodes
import android.util.Log
import android.util.Xml
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.experimental.NonCancellable.isActive
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.uiautomator2daemon.debugT
import org.droidmate.deviceInterface.guimodel.WidgetData
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
	suspend fun fetch(device: UiDevice): List<WidgetData> = debugT(" compute UiNodes avg= ${ut/(max(nActions,1)*1000000)}", {
		deviceW = device.displayWidth
		deviceH = device.displayHeight
		val nodes = LinkedList<WidgetData>()

		device.getNonSystemRootNodes().let{
			it.forEachIndexed { index: Int, root: AccessibilityNodeInfo ->
				rootIdx = index
				createBottomUp(root,parentXpath = "//", nodes = nodes)
			}
		}
		nodes.also { Log.d(LOGTAG,"#elems = ${it.size}")}
	}, inMillis = true, timer = {ut += it; nActions+=1})


	fun getXml(device: UiDevice):String = 	debugT(" fetching gui Dump ", {StringWriter().use { out ->
		device.waitForIdle()

		val serializer = Xml.newSerializer()
		serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
		serializer.setOutput(out)//, "UTF-8")

		serializer.startDocument("UTF-8", true)
		serializer.startTag("", "hierarchy")
		serializer.attribute("", "rotation", Integer.toString(device.displayRotation))

		device.apply(nodeDumper(serializer, device.displayWidth, device.displayHeight)
		) { _-> serializer.endTag("", "node")}

		serializer.endTag("", "hierarchy")
		serializer.endDocument()
		serializer.flush()
		out.toString()
	}}, inMillis = true)

	/** check if this node fullfills the given condition and recursively check descendents if not **/
	fun any(device: UiDevice, cond: SelectorCondition):Boolean{
		var found = false

		val processor:NodeProcessor = { node,_ ->
			if (!isActive || !node.isVisibleToUser || !node.refresh()) false  // do not traverse deeper
			else {
				found = cond(node)
				!found // continue if condition is not fulfilled yet
			}
		}
		device.apply(processor)
		return found
	}

	/** @paramt timeout amount of mili seconds, maximal spend to wait for condition [cond] to become true (default 10s)
	 * @return if the condition was fulfilled within timeout
	 * */
	@JvmOverloads
	fun waitFor(device: UiDevice, timeout: Long = 10000, cond: SelectorCondition): Boolean{
		return waitFor(device,timeout,10,cond)
	}
	/** @param pollTime time intervall (in ms) to recheck the condition [cond] */
	fun waitFor(device: UiDevice, timeout: Long, pollTime: Long, cond: SelectorCondition) = runBlocking{
		// lookup should only take less than 100ms (avg 50-80ms) if the UiAutomator did not screw up
		val scanTimeout = 100 // this is the maximal number of mili seconds, which is spend for each lookup in the hierarchy
		var time = 0.0
		var found = false

		while(!found && time<timeout){
			measureTimeMillis {
				with(async { any(device, cond) }) {
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
				device.runWatchers() // to update the ui view?
				if(!found && this<pollTime) delay(pollTime-this)
				Log.d(LOGTAG,"$found single wait iteration $this")
			}
		}
		found
	}

	suspend fun getScreenShot(): Bitmap {
		var screenshot =
				debugT("first screen-fetch attemp ", {Screenshot.capture().bitmap},inMillis = true)

		if (screenshot == null){
			Log.d(LOGTAG,"screenshot failed")
			delay(10)
			screenshot = Screenshot.capture().bitmap
		}
		return screenshot.also {
			if (it == null)
				Log.w(LOGTAG,"no screenshot available")
			}
	}

	@JvmStatic private var t = 0.0
	@JvmStatic private var c = 0
	@JvmStatic
	fun compressScreenshot(screenshot: Bitmap?): ByteArray = debugT("compress image avg = ${t/ max(1,c)}",{
		var bytes = ByteArray(0)
		val stream = ByteArrayOutputStream()
		try {
			screenshot?.setHasAlpha(false)
			screenshot?.compress(Bitmap.CompressFormat.PNG, 100, stream)
			stream.flush()

			bytes = stream.toByteArray()
			stream.close()
		} catch (e: Exception) {
			Log.e(LOGTAG, "Failed to compress screenshot: ${e.message}. Stacktrace: ${e.stackTrace}")
		}

		bytes
	}, inMillis = true, timer = { t += it / 1000000.0; c += 1})

}




