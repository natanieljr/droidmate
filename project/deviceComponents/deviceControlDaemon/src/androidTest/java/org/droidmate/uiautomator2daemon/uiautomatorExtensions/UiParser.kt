@file:Suppress("UsePropertyAccessSyntax")

package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.support.test.uiautomator.NodeProcessor
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.isActive
import org.droidmate.deviceInterface.communication.UiElementProperties
import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.visibleOuterBounds
import org.xmlpull.v1.XmlSerializer
import java.util.*
import kotlin.coroutines.coroutineContext

abstract class UiParser {

	companion object {
		fun computeIdHash(xPath: String, layer: Int) = xPath.hashCode() + layer
		/** used for parentHash and idHash computation of [UiElementProperties] */
		private fun computeIdHash(xPath: String, window: DisplayedWindow) = computeIdHash(xPath, window.layer)
	}

	protected suspend fun createBottomUp(w: DisplayedWindow, node: AccessibilityNodeInfo, index: Int = 0,
	                                     parentXpath: String, nodes: MutableList<UiElementProperties>, img: Bitmap?,
	                                     parentH: Int = 0): UiElementProperties? {
		if(!coroutineContext.isActive) return null
		val xPath = parentXpath +"${node.className}[${index + 1}]"

		val nChildren = node.getChildCount()
		val idHash= computeIdHash(xPath,w)

		//FIXME sometimes getChild returns a null node, this may be a synchronization issue in this case the fetch should return success=false or retry to fetch
		val children: List<UiElementProperties?> = (nChildren-1 downTo 0).map { i -> Pair(i,node.getChild(i)) }
				//REMARK we use drawing order but sometimes there is a transparent layout in front of the elements, probably used by the apps to determine their app area (e.g. amazon), this has to be considered in the [visibleAxis] call for the window area
				.sortedByDescending { (i,node) -> if(api>=24) node.drawingOrder else i }
				.map { (i,childNode) ->		// bottom-up strategy, process children first (in drawingOrder) if they exist
					if(childNode == null) debugOut("ERROR child nodes should never be null")
					createBottomUp(w, childNode, i, "$xPath/",nodes, img, parentH = idHash).also {
						childNode.recycle()  //recycle children as we requested instances via getChild which have to be released
					}
				}

		return node.createWidget(w, xPath, children.filterNotNull(), img, idHash = idHash, parentH = parentH).also {
			nodes.add(it)
		}
	}

	private fun AccessibilityNodeInfo.createWidget(w: DisplayedWindow, xPath: String, children: List<UiElementProperties>,
	                                               img: Bitmap?, idHash: Int, parentH: Int): UiElementProperties {
		val nodeRect = Rect()
		this.getBoundsInScreen(nodeRect)  // determine the 'overall' boundaries these may be outside of the app window or even outside of the screen
		val props = LinkedList<String>()

		props.add("actionList = ${this.actionList}")
		if(api>=24)	props.add("drawingOrder = ${this.drawingOrder}")
		if(api>=27)		props.add("hintText = ${this.hintText}")  // -> for edit fields
		props.add("inputType = ${this.inputType}")
		props.add("labelFor = ${this.labelFor}")
		props.add("liveRegion = ${this.liveRegion}")
		props.add("windowId = ${this.windowId}")

		var uncoveredArea = true
		// due to bottomUp strategy we will only get coordinates which are not overlapped by other UiElements
		val visibleAreas = if(!isEnabled || !isVisibleToUser) emptyList()
				else nodeRect.visibleAxis(w.area, isSingleElement = true).map { it.toRectangle() }.let { area ->
					if (area.isEmpty()) {
						val childrenC = children.flatMap { boundsList -> boundsList.visibleAreas } // allow the parent boundaries to contain all definedAsVisible child coordinates
						uncoveredArea = false
						nodeRect.visibleAxisR(childrenC)
					} else{
						uncoveredArea = markedAsOccupied
						area
					}
				}
//		val subBounds = LinkedList<Rectangle>().apply {
//			if(uncoveredArea) addAll(visibleAreas)
//			addAll(children.flatMap { it.allSubAreas })
//		}
		val visibleBounds: Rectangle = when {
			visibleAreas.isEmpty() -> Rectangle(0,0,0,0)  // no uncovered area means this node cannot be visible
			children.isEmpty() -> {
				visibleAreas.visibleOuterBounds()
			}
			else -> with(LinkedList<Rectangle>()){
				addAll(visibleAreas)
				addAll(children.map { it.visibleBounds })
				visibleOuterBounds()
			}
		}

		return UiElementProperties(
				idHash = idHash,
				imgId = computeImgId(img,visibleBounds),
				allSubAreas = emptyList(),//subBounds,
//				isInBackground = visibleBounds.isNotEmpty() && (
//						!subBounds.isComplete()
//						),
				visibleBounds = visibleBounds,
				hasUncoveredArea = uncoveredArea,
				metaInfo = props,
				text = safeCharSeqToString(text),
				contentDesc = safeCharSeqToString(contentDescription),
				resourceId = safeCharSeqToString(viewIdResourceName),
				className = safeCharSeqToString(className),
				packageName = safeCharSeqToString(packageName),
				enabled = isEnabled,
				isInputField = isEditable,
				isPassword = isPassword,
				clickable = isClickable,
				longClickable = isLongClickable,
				checked = if (isCheckable) isChecked else null,
				focused = if (isFocusable) isFocused else null,
				scrollable = isScrollable,
				selected = isSelected,
				definedAsVisible = isVisibleToUser,
				boundaries = nodeRect.toRectangle(),
				visibleAreas = visibleAreas,
				xpath = xPath,
				parentHash = parentH,
				childHashes = children.map { it.idHash },
				isKeyboard = w.isKeyboard
		)
	}

	private fun computeImgId(img: Bitmap?, b: Rectangle): Int {
		if (img == null || b.isEmpty()) return 0
		val subImg = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
		val c = Canvas(subImg)
		c.drawBitmap(img, b.toRect(), Rect(0,0,b.width,b.height),null)
		// now subImg contains all its pixel of the area specified by b
		// convert the image into byte array to determine a deterministic hash value
		return bitmapToBytes(subImg).contentHashCode()
	}

	/*
	 * The display may contain decorative elements as the status and menu bar which.
	 * We use this method to check if an element is invisible, since it is hidden by such decorative elements.
	 */
	/* for now keep it here if we later need to recognize decor elements again, maybe to differentiate them from other system windows
	fun computeAppArea():Rect{
	// compute the height of the status bar, which determines the offset for any definedAsVisible app element
		var sH = 0
		val resources = InstrumentationRegistry.getInstrumentation().context.resources
		val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
		if (resourceId > 0) {
			sH = resources.getDimensionPixelSize(resourceId)
		}

		val p = Point()
		(InstrumentationRegistry.getInstrumentation().context.getSystemService(Service.WINDOW_SERVICE) as WindowManager)
		.defaultDisplay.getSize(p)

		return Rect(0,sH,p.x,p.y)
	}
	*/

	protected val nodeDumper:(serializer: XmlSerializer, width: Int, height: Int)-> NodeProcessor =
			{ serializer: XmlSerializer, _: Int, _: Int ->
				{ node: AccessibilityNodeInfo, index: Int, _ ->
					val nodeRect = Rect()
					node.getBoundsInScreen(nodeRect)  // determine the 'overall' boundaries these may be outside of the app window or even outside of the screen

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
					serializer.attribute("", "definedAsVisible-to-user", java.lang.Boolean.toString(node.isVisibleToUser))
					serializer.attribute("", "bounds", nodeRect.toShortString())

					/** custom attributes, usually not definedAsVisible in the device-UiDump */
					serializer.attribute("", "isInputField", java.lang.Boolean.toString(node.isEditable)) // could be usefull for custom widget classes to identify input fields
					/** experimental */
//		serializer.attribute("", "canOpenPopup", java.lang.Boolean.toString(node.canOpenPopup()))
//		serializer.attribute("", "isDismissable", java.lang.Boolean.toString(node.isDismissable))
////		serializer.attribute("", "isImportantForAccessibility", java.lang.Boolean.toString(node.isImportantForAccessibility)) // not working for android 6
//		serializer.attribute("", "inputType", Integer.toString(node.inputType))
//		serializer.attribute("", "describeContents", Integer.toString(node.describeContents())) // -> seams always 0

					true  // traverse whole hierarchy
				}
			}

	private fun safeCharSeqToString(cs: CharSequence?): String {
		return if (cs == null)	""
		else
			stripInvalidXMLChars(cs).replace(";", "<semicolon>").replace(Regex("\\r\\n|\\r|\\n"), "<newline>").trim()
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

	@Suppress("PrivatePropertyName")
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

}