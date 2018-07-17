package android.support.test.uiautomator

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo


fun AccessibilityNodeInfo.getBounds(width: Int, height: Int): Rect = when{
	isVisibleToUser ->
		AccessibilityNodeInfoHelper.getVisibleBoundsInScreen(this, width, height)
	else -> Rect().apply { getBoundsInScreen(this)}
}

/** @return true if children should be recursively traversed */
typealias NodeProcessor = (rootNode: AccessibilityNodeInfo, index: Int)	-> Boolean
typealias PostProcessor<T> = (rootNode: AccessibilityNodeInfo)	-> T
const val osPkg = "com.android.systemui"
inline fun<reified T> UiDevice.apply(noinline processor: NodeProcessor, noinline postProcessor: PostProcessor<T>): List<T> =
	getNonSystemRootNodes().map { root: AccessibilityNodeInfo ->
				processTopDown(root,processor = processor,postProcessor = postProcessor)
	}

fun UiDevice.apply(processor: NodeProcessor){
	getNonSystemRootNodes().map { root: AccessibilityNodeInfo ->
		processTopDown(root, processor = processor, postProcessor = { _ -> Unit })
	}
}

fun<T> processTopDown(node:AccessibilityNodeInfo, index: Int=0, processor: NodeProcessor, postProcessor: PostProcessor<T>):T{
	val nChildren = node.childCount
	val proceed = processor(node,index)

	if(proceed)
	(0 until nChildren).map { i ->
		processTopDown(node.getChild(i),i,processor, postProcessor)
	}
	val res = postProcessor(node)

	node.recycle()
	return res
}

@Suppress("UsePropertyAccessSyntax")
fun UiDevice.getNonSystemRootNodes():List<AccessibilityNodeInfo> = getWindowRoots().filterNot { it.packageName == osPkg }

fun UiDevice.longClick(x: Int, y: Int, timeout: Long)=
	interactionController.longTapAndSync(x,y,timeout)
//	interactionController.longTapNoSync(x,y)

fun UiDevice.click(x: Int, y: Int, timeout: Long)=
	interactionController.clickAndSync(x,y,timeout)
//	interactionController.clickNoSync(x,y)


