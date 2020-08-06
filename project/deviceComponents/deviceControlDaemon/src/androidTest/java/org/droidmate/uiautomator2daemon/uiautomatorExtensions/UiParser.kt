@file:Suppress("UsePropertyAccessSyntax")

package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.support.test.uiautomator.NodeProcessor
import android.support.test.uiautomator.getBounds
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.isActive
import org.droidmate.deviceInterface.communication.UiElementProperties
import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.isEnabled
import org.droidmate.deviceInterface.exploration.visibleOuterBounds
import org.xmlpull.v1.XmlSerializer
import java.util.*
import kotlin.coroutines.coroutineContext

abstract class UiParser {

	companion object {
		private const val LOGTAG = "droidmate/UiParser"

		fun computeIdHash(xPath: String, windowLayer: Int) = xPath.hashCode() + windowLayer
		/** used for parentHash and idHash computation of [UiElementProperties] */
		private fun computeIdHash(xPath: String, window: DisplayedWindow) = computeIdHash(xPath, window.layer)
	}

	protected suspend fun createBottomUp(w: DisplayedWindow, node: AccessibilityNodeInfo, index: Int = 0,
	                                     parentXpath: String, nodes: MutableList<UiElementProperties>, img: Bitmap?,
	                                     parentH: Int = 0): UiElementProperties? {
		if(!coroutineContext.isActive) {
			Log.w(LOGTAG, "coroutine is no longer active UI parsing is aborted")
			return null
		}
		val refresh = node.refresh()
		if(!refresh){
			Log.d(LOGTAG, "refresh node failed, this will lead to illegale state exception later-on, return null for this element")
			return null
		}
		val xPath = parentXpath +"${node.className}[${index + 1}]"

		val nChildren = node.getChildCount()
		val idHash= computeIdHash(xPath,w)

		SiblingNodeComparator.initParentBounds(node)
		//FIXME sometimes getChild returns a null node, this may be a synchronization issue in this case the fetch should return success=false or retry to fetch
		val children: List<UiElementProperties?> = (nChildren-1 downTo 0).map { i -> Pair(i,node.getChild(i)) }
				//REMARK we use drawing order but sometimes there is a transparent layout in front of the elements, probably used by the apps to determine their app area (e.g. amazon), this has to be considered in the [visibleAxis] call for the window area
			.sortedWith(SiblingNodeComparator)
				.map { (i,childNode) ->		// bottom-up strategy, process children first (in drawingOrder) if they exist
					if(childNode == null) debugOut("ERROR child nodes should never be null")
					createBottomUp(w, childNode, i, "$xPath/",nodes, img, parentH = idHash).also {
						childNode.recycle()  //recycle children as we requested instances via getChild which have to be released
					}
				}

		return node.createWidget(w, xPath, children.filterNotNull(), img, idHash = idHash, parentH = parentH, processedNodes = nodes).also {
			nodes.add(it)
		}
	}

	private val isClickableDescendant:(UiElementProperties)->Boolean = {
		it.hasClickableDescendant || it.clickable || (it.selected.isEnabled()
				&& !it.className.contains("view", ignoreCase = true))
	}
	private fun AccessibilityNodeInfo.createWidget(w: DisplayedWindow, xPath: String, children: List<UiElementProperties>,
	                                               img: Bitmap?, idHash: Int, parentH: Int, processedNodes: List<UiElementProperties>): UiElementProperties {
		val nodeRect = Rect()
		this.getBoundsInScreen(nodeRect)  // determine the 'overall' boundaries these may be outside of the app window or even outside of the screen
		val props = LinkedList<String>()
		val boundsInParent = Rect()
		this.getBoundsInParent(boundsInParent)
		if(nodeRect.height()<0 || nodeRect.width()<0) { // no idea why this happens but try to correct the width/height by using the second bounds property
			nodeRect.right = nodeRect.left+boundsInParent.width()
			nodeRect.bottom = nodeRect.top+boundsInParent.height()
		}

		props.add("defBounds (l,t,r,b)= $nodeRect")
		props.add("boundsInParent= $boundsInParent")
		props.add("actionList = ${this.actionList}")
		if(api>=24)	props.add("drawingOrder = ${this.drawingOrder}")
		props.add("labelFor = ${this.labelFor}")
		props.add("labeledBy = ${this.getLabeledBy()}")
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

		props.add("markedAsOccupied = $markedAsOccupied")
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

		val selected = if(actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SELECT)&& markedAsOccupied) isSelected else null
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
				hintText = if(api>=27) safeCharSeqToString(hintText) else "",
				inputType = inputType,
				contentDesc = safeCharSeqToString(contentDescription),
				resourceId = safeCharSeqToString(viewIdResourceName),
				className = safeCharSeqToString(className),
				packageName = safeCharSeqToString(packageName),
				enabled = isEnabled,
				isInputField = isEditable || actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT),
				isPassword = isPassword,
				clickable = isClickable,
				longClickable = isLongClickable,
				checked = if (isCheckable) isChecked else null,
				focused = if (isFocusable) isFocused else null,
				scrollable = isScrollable,
				selected = selected, // ignore 'transparent' layouts
				hasClickableDescendant = children.any(isClickableDescendant).let { hasClickableDescendant ->
					// check if there are already 'selectable' items in the visible bounds of it, if so set clickable descendants to true
					if (!hasClickableDescendant && selected.isEnabled()) {
						// this visible area contains 'selectable/clickable' items therefore we want to mark this as having such descendants even if it is no direct parent but only an 'uncle' to these elements
						processedNodes.any { visibleBounds.surrounds(it.visibleBounds) && isClickableDescendant(it) }
					} else hasClickableDescendant
				},
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
			{ ser: XmlSerializer, width: Int, height: Int ->
				{ node: AccessibilityNodeInfo, index: Int, _ ->
					val nodeRect = Rect()
					node.getBoundsInScreen(nodeRect)  // determine the 'overall' boundaries these may be outside of the app window or even outside of the screen

					ser.startTag("", "node")
					ser.addAttribute("index",index)
					ser.addAttribute("drawingOrder", if(api>=24) node.drawingOrder else "NA")
					ser.addAttribute("windowId", node.windowId)
					
					ser.addAttribute("text", node.text)
					ser.addAttribute("resource-id", node.viewIdResourceName)
					ser.addAttribute("class", node.className)
					ser.addAttribute("package", node.packageName)
					ser.addAttribute("content-desc", node.contentDescription)
					ser.addAttribute("LabeledBy", node.labeledBy
						?.let{ "it.text bounds: ${it.getBounds(width,height).toShortString()}" }?:"")

					ser.startTag("","action-types")
					ser.addAttribute("actionList", node.actionList)
					ser.addAttribute("inputType", node.inputType)
					ser.addAttribute("checkable", node.isCheckable)
					ser.addAttribute("checked", node.isChecked)
					ser.addAttribute("clickable", node.isClickable)
					ser.addAttribute("enabled", node.isEnabled)
					ser.addAttribute("focusable", node.isFocusable)
					ser.addAttribute("focused", node.isFocused)
					ser.addAttribute("scrollable", node.isScrollable)
					ser.addAttribute("long-clickable", node.isLongClickable)
					ser.addAttribute("isInputField", node.isEditable) // could be useful for custom widget classes to identify input fields
					ser.addAttribute("password", node.isPassword)
					ser.addAttribute("selected", node.isSelected)
					ser.addAttribute("selected", node.isSelected)
					ser.endTag("","action-types")

					ser.startTag("","boundaries")
					ser.addAttribute("definedAsVisible-to-user", node.isVisibleToUser)
					ser.addAttribute("defBounds", nodeRect.toShortString())
					val pBounds = Rect().apply { node.getBoundsInParent(this) }
					ser.addAttribute("boundsInParent", pBounds.toShortString())
					ser.addAttribute("liveRegion", node.liveRegion)
					ser.endTag("","boundaries")

					/** custom attributes, usually not definedAsVisible in the device-UiDump */
					/** experimental */
//		serializer.attribute("", "canOpenPopup", java.lang.Boolean.toString(node.canOpenPopup()))
//		serializer.attribute("", "isDismissable", java.lang.Boolean.toString(node.isDismissable))
////		serializer.attribute("", "isImportantForAccessibility", java.lang.Boolean.toString(node.isImportantForAccessibility)) // not working for android 6
//		serializer.attribute("", "inputType", Integer.toString(node.inputType))
//		serializer.attribute("", "describeContents", Integer.toString(node.describeContents())) // -> seams always 0

					true  // traverse whole hierarchy
				}
			}

//	@Suppress("PrivatePropertyName")
//	private val NAF_EXCLUDED_CLASSES = arrayOf(android.widget.GridView::class.java.name, android.widget.GridLayout::class.java.name, android.widget.ListView::class.java.name, android.widget.TableLayout::class.java.name)
//	/**
//	 * The list of classes to exclude my not be complete. We're attempting to
//	 * only reduce noise from standard layout classes that may be falsely
//	 * configured to accept clicks and are also enabled.
//	 *
//	 * @param node
//	 * @return true if node is excluded.
//	 */
//	private fun nafExcludedClass(node: AccessibilityNodeInfo): Boolean {
//		val className = safeCharSeqToString(node.className)
//		for (excludedClassName in NAF_EXCLUDED_CLASSES) {
//			if (className.endsWith(excludedClassName))
//				return true
//		}
//		return false
//	}

}