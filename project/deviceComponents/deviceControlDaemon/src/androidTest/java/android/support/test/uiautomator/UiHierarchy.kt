package android.support.test.uiautomator

import android.util.Log
import android.util.Xml
import android.view.accessibility.AccessibilityNodeInfo
import org.xmlpull.v1.XmlSerializer
import java.io.OutputStream

object UiHierarchy{
	private const val LOGTAG = "droidmate/UiHierarchy"
	private val NAF_EXCLUDED_CLASSES = arrayOf(android.widget.GridView::class.java.name, android.widget.GridLayout::class.java.name, android.widget.ListView::class.java.name, android.widget.TableLayout::class.java.name)

	/**
	 * The list of classes to exclude my not be complete. We're attempting to
	 * only reduce noise from standard layout classes that may be falsely
	 * configured to accept clicks and are also enabled.
	 *
	 * @param node
	 * @return true if node is excluded.
	 */
	private fun nafExcludedClass(node: AccessibilityNodeInfo): Boolean {
		val className = safeCharSeqToString(node.className)
		for (excludedClassName in NAF_EXCLUDED_CLASSES) {
			if (className.endsWith(excludedClassName))
				return true
		}
		return false
	}



	fun dump(device: UiDevice,out:OutputStream){

			val serializer = Xml.newSerializer()
			serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
			serializer.setOutput(out, "UTF-8")

			serializer.startDocument("UTF-8", true)
			serializer.startTag("", "hierarchy")
			serializer.attribute("", "rotation", Integer.toString(device.displayRotation))

			for (root in device.windowRoots) {
				dumpNodeRec(root, serializer, 0, device.displayWidth, device.displayHeight)
			}
			serializer.endTag("", "hierarchy")
			serializer.endDocument()
	}

	private fun dumpNodeRec(node: AccessibilityNodeInfo, serializer: XmlSerializer, index: Int, width: Int, height: Int) {
		serializer.startTag("", "node")
		if (!nafExcludedClass(node))
			serializer.attribute("", "NAF", java.lang.Boolean.toString(true))
		serializer.attribute("", "index", Integer.toString(index))
		serializer.attribute("", "text", safeCharSeqToString(node.text))
		serializer.attribute("", "resource-id", safeCharSeqToString(node.viewIdResourceName))
		serializer.attribute("", "class", safeCharSeqToString(node.className))
		serializer.attribute("", "package", safeCharSeqToString(node.packageName))
		serializer.attribute("", "content-desc", safeCharSeqToString(node.contentDescription))
		serializer.attribute("", "checkable", java.lang.Boolean.toString(node.isCheckable))
		serializer.attribute("", "checked", java.lang.Boolean.toString(node.isChecked))
		serializer.attribute("", "clickable", java.lang.Boolean.toString(node.isClickable))
		serializer.attribute("", "enabled", java.lang.Boolean.toString(node.isEnabled))
		serializer.attribute("", "focusable", java.lang.Boolean.toString(node.isFocusable))
		serializer.attribute("", "focused", java.lang.Boolean.toString(node.isFocused))
		serializer.attribute("", "scrollable", java.lang.Boolean.toString(node.isScrollable))
		serializer.attribute("", "long-clickable", java.lang.Boolean.toString(node.isLongClickable))
		serializer.attribute("", "password", java.lang.Boolean.toString(node.isPassword))
		serializer.attribute("", "selected", java.lang.Boolean.toString(node.isSelected))
		serializer.attribute("", "visible-to-user", java.lang.Boolean.toString(node.isVisibleToUser))
		serializer.attribute("", "bounds", AccessibilityNodeInfoHelper.getVisibleBoundsInScreen(
				node, width, height).toShortString())

		/** custom attributes, usually not visible in the device-UiDump */
		serializer.attribute("", "editable", java.lang.Boolean.toString(node.isEditable)) // could be usefull for custom widget classes to identify input fields
		// experimental
		serializer.attribute("", "canOpenPopup", java.lang.Boolean.toString(node.canOpenPopup()))
		serializer.attribute("", "isDismissable", java.lang.Boolean.toString(node.isDismissable))
		serializer.attribute("", "isImportantForAccessibility", java.lang.Boolean.toString(node.isImportantForAccessibility))
		serializer.attribute("", "inputType", Integer.toString(node.inputType))
		serializer.attribute("", "describeContents", Integer.toString(node.describeContents())) // -> seams always 0


		val count = node.childCount
		for (i in 0 until count) {
			val child = node.getChild(i)
			if (child != null) {
//				if (child.isVisibleToUser()) {  // FIX in contrast to AccessibilityNodeInfoDumper we want ALL nodes in the dump but only target visible ones
					dumpNodeRec(child, serializer, i, width, height)
					child.recycle()
//				} else {
//					Log.i(LOGTAG, String.format("Skipping invisible child: %s", child.toString()))
//				}
			} else {
				Log.i(LOGTAG, String.format("Null child %d/%d, parent: %s",
						i, count, node.toString()))
			}
		}
		serializer.endTag("", "node")

	}

	private fun safeCharSeqToString(cs: CharSequence?): String {
		return if (cs == null)
			""
		else {
			stripInvalidXMLChars(cs)
		}
	}

	private fun stripInvalidXMLChars(cs: CharSequence): String {
		val ret = StringBuffer()
		var ch: Char
		/* http://www.w3.org/TR/xml11/#charsets
        [#x1-#x8], [#xB-#xC], [#xE-#x1F], [#x7F-#x84], [#x86-#x9F], [#xFDD0-#xFDDF],
        [#x1FFFE-#x1FFFF], [#x2FFFE-#x2FFFF], [#x3FFFE-#x3FFFF],
        [#x4FFFE-#x4FFFF], [#x5FFFE-#x5FFFF], [#x6FFFE-#x6FFFF],
        [#x7FFFE-#x7FFFF], [#x8FFFE-#x8FFFF], [#x9FFFE-#x9FFFF],
        [#xAFFFE-#xAFFFF], [#xBFFFE-#xBFFFF], [#xCFFFE-#xCFFFF],
        [#xDFFFE-#xDFFFF], [#xEFFFE-#xEFFFF], [#xFFFFE-#xFFFFF],
        [#x10FFFE-#x10FFFF].
         */
		for (i in 0 until cs.length) {
			ch = cs[i]

			if (ch.toInt() in 0x1..0x8 || ch.toInt() in 0xB..0xC || ch.toInt() in 0xE..0x1F ||
					ch.toInt() in 0x7F..0x84 || ch.toInt() in 0x86..0x9f ||
					ch.toInt() in 0xFDD0..0xFDDF || ch.toInt() in 0x1FFFE..0x1FFFF ||
					ch.toInt() in 0x2FFFE..0x2FFFF || ch.toInt() in 0x3FFFE..0x3FFFF ||
					ch.toInt() in 0x4FFFE..0x4FFFF || ch.toInt() in 0x5FFFE..0x5FFFF ||
					ch.toInt() in 0x6FFFE..0x6FFFF || ch.toInt() in 0x7FFFE..0x7FFFF ||
					ch.toInt() in 0x8FFFE..0x8FFFF || ch.toInt() in 0x9FFFE..0x9FFFF ||
					ch.toInt() in 0xAFFFE..0xAFFFF || ch.toInt() in 0xBFFFE..0xBFFFF ||
					ch.toInt() in 0xCFFFE..0xCFFFF || ch.toInt() in 0xDFFFE..0xDFFFF ||
					ch.toInt() in 0xEFFFE..0xEFFFF || ch.toInt() in 0xFFFFE..0xFFFFF ||
					ch.toInt() in 0x10FFFE..0x10FFFF)
				ret.append(".")
			else
				ret.append(ch)
		}
		return ret.toString()
	}
}