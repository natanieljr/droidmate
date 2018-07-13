package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.apply
import android.util.Log
import android.util.Xml
import org.droidmate.uiautomator2daemon.debugT
import org.droidmate.uiautomator_daemon.guimodel.WidgetData
import java.io.StringWriter
import java.util.*


@Suppress("unused")
object UiHierarchy : UiParser(),UiSelector {
	private const val LOGTAG = "droidmate/UiHierarchy"

	private var nActions = 1
	private var time = 0L
	fun fetch(device: UiDevice): List<WidgetData> = debugT(" compute UiNodes avg= ${time/(nActions*1000000)}", {LinkedList<WidgetData>().apply {
		device.waitForIdle()
		device.apply(widgetCreator(this,device.displayWidth, device.displayHeight))
	}.apply { Log.d(LOGTAG,"#elems = ${this.size}")} }, inMillis = true, timer = {time += it; nActions+=1})

	fun getXml(device: UiDevice):String = 	debugT(" fetching gui Dump ", {StringWriter().use { out ->
		device.waitForIdle()

		val serializer = Xml.newSerializer()
		serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
		serializer.setOutput(out)//, "UTF-8")

		serializer.startDocument("UTF-8", true)
		serializer.startTag("", "hierarchy")
		serializer.attribute("", "rotation", Integer.toString(device.displayRotation))

		device.apply(nodeDumper(serializer, device.displayWidth, device.displayHeight))

		serializer.endTag("", "hierarchy")
		serializer.endDocument()
		serializer.flush()
		out.toString()
	}}, inMillis = true)

}



