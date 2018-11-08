package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.droidmate.deviceInterface.exploration.AppWindow

private const val debug = true

/** on device we need additional information to compute UiElement visibility this information is wrapped within this class.
 * It is the Callers responsibility to recycle [root] when it is no longer needed.
 *
 * @param area this area coordinates are used to compute correct UiElement visibility in the Ui extraction/parsing
 * @param rootNode is null if we do not want to extract the elements from this window, i.e. if it is a system window like status/navigation bar
 * */
data class DisplayedWindow(val w: AppWindow,
                           val initialArea: List<Rect>,
//                           var area: MutableList<Rect>,
                      var rootNode: AccessibilityNodeInfo?, val isKeyboard: Boolean,
                      val layer: Int, val bounds: Rect, val windowType:Int) {

	var area: MutableList<Rect> = mutableListOf()
	fun isExtracted() = rootNode != null
	fun isApp() = windowType == AccessibilityWindowInfo.TYPE_APPLICATION

	companion object {
		operator fun invoke(wInfo: AccessibilityWindowInfo, uncoveredCoordinates: MutableList<Rect>, outRect: Rect,
		                    deviceRoot: AccessibilityNodeInfo? = null): DisplayedWindow{

			val root: AccessibilityNodeInfo? = deviceRoot ?: wInfo.root // REMARK: do not recycle root nodes, only instances requested via getChild
			// compute which points on the screen are occupied by this window (and are not occupied by a higher layer window)
			debugOut("start window visibility computation for ${root?.packageName} $outRect" +
					"type=${wInfo.type} "
// title=${wInfo.title} desc=${root.contentDescription}, ${wInfo.isAccessibilityFocused}, ${wInfo.isFocused}, ${wInfo.layer}, ${wInfo.isActive}
					, debug)
			val area = outRect.visibleAxis(uncoveredCoordinates)
			debugOut("create ${wInfo.id} $area", debug)
			return DisplayedWindow(
					AppWindow(wInfo.id, root?.packageName?.toString()?:"systemWindow_WithoutRoot", wInfo.isFocused, wInfo.isAccessibilityFocused, visibleOuterBounds(area)),
					area,
					isKeyboard = wInfo.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD, //keyboardPkgs.contains(root?.packageName),
					rootNode = if(wInfo.type == AccessibilityWindowInfo.TYPE_APPLICATION || wInfo.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) root else null,
					layer = wInfo.layer,
					bounds = outRect,
					windowType = wInfo.type
			).also {
//				wInfo.recycle()
			}
		}
	}
}