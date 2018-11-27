package android.support.test.uiautomator

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo


fun AccessibilityNodeInfo.getBounds(width: Int, height: Int): Rect = when{
	isEnabled && isVisibleToUser ->
		AccessibilityNodeInfoHelper.getVisibleBoundsInScreen(this, width, height)
	else -> Rect().apply { getBoundsInScreen(this)} // this may lead to negative coordinates/width/height values
}

/** @return true if children should be recursively traversed */
typealias NodeProcessor = (rootNode: AccessibilityNodeInfo, index: Int, xPath: String)	-> Boolean
typealias PostProcessor<T> = (rootNode: AccessibilityNodeInfo)	-> T
const val osPkg = "com.android.systemui"

inline fun<reified T> UiDevice.apply(noinline processor: NodeProcessor, noinline postProcessor: PostProcessor<T>): List<T> =
	getNonSystemRootNodes().mapIndexed { index,root: AccessibilityNodeInfo ->
		processTopDown(root,processor = processor,postProcessor = postProcessor)
	}

fun UiDevice.apply(processor: NodeProcessor){
	try{
		getNonSystemRootNodes().mapIndexed { index, root: AccessibilityNodeInfo ->
			processTopDown(root, processor = processor, postProcessor = { _ -> Unit })
		}
	}catch(e: Exception){
		Log.w("droidmate/UiDevice","error while processing AccessibilityNode tree ${e.localizedMessage}")
	}
}

fun<T> processTopDown(node:AccessibilityNodeInfo, index: Int=0, processor: NodeProcessor, postProcessor: PostProcessor<T>, parentXpath: String = "//"):T{
	val nChildren = node.childCount
	val xPath = parentXpath +"${node.className}[${index + 1}]"
	val proceed = processor(node,index,xPath)

	try {
		if(proceed)
			(0 until nChildren).map { i ->
				node.getChild(i).let { child ->
					processTopDown(child, i, processor, postProcessor, "$xPath/").also {
						child.recycle()
					}
				}
			}
	} catch (e: Exception){	// the accessibilityNode service may throw this if the node is no longer up-to-date
		Log.w("droidmate/UiDevice", "error child of $parentXpath node no longer available ${e.localizedMessage}")
		node.refresh()
	}

	return postProcessor(node)
}

@Suppress("UsePropertyAccessSyntax")
fun UiDevice.getNonSystemRootNodes():List<AccessibilityNodeInfo> = getWindowRootNodes().filterNot { it.packageName == osPkg }
@Suppress("UsePropertyAccessSyntax")
fun UiDevice.getWindowRootNodes(): Array<out AccessibilityNodeInfo> = getWindowRoots()

fun UiDevice.longClick(x: Int, y: Int, timeout: Long)=
	interactionController.longTapAndSync(x,y,timeout)
//	interactionController.longTapNoSync(x,y)

fun UiDevice.click(x: Int, y: Int, timeout: Long)=
	interactionController.clickAndSync(x,y,timeout)
//	interactionController.clickNoSync(x,y)


