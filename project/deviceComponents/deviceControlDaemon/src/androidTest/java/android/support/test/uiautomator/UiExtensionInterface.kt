package android.support.test.uiautomator

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo


fun AccessibilityNodeInfo.getBounds(width: Int, height: Int): Rect = when{
	isVisibleToUser ->
		AccessibilityNodeInfoHelper.getVisibleBoundsInScreen(this, width, height)
	else -> Rect().apply { getBoundsInScreen(this)}
}

typealias NodeProcessor = (rootNode: AccessibilityNodeInfo, index: Int)	-> Unit
const val osPkg = "com.android.systemui"
fun UiDevice.apply( processor: NodeProcessor): Unit = with(windowRoots) {
	filterNot { it.packageName == osPkg }
			.mapIndexed { index: Int, root: AccessibilityNodeInfo -> processor(root, index) }
}

