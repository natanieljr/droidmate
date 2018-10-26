package org.droidmate.uiautomator2daemon

import android.support.test.uiautomator.*
import android.util.Log
import org.droidmate.deviceInterface.communication.DeviceDaemonException
import org.droidmate.deviceInterface.UiautomatorDaemonConstants.uiaDaemon_logcatTag

/**
 * Created by J.H. on 08.02.2018.
 */

/**
 * the lambda function defining the UiSelector to find the target element.
 */
private typealias Selector = (String) -> UiSelector

// FIXME xpath visitor would be safer but for now use quick and dirty string operations
// XPath (for this we need to visit the xpath and check for each visitStep if an element with this classname and given parent exists
private val processNode = { s: UiSelector?, n: String ->
	assert(n.count { c -> c == '[' } == 1 && n.count { c -> c == ']' } == 1, { "xPath error there are to many [ or ] chars in the node $n" })
	val sIdx = n.indexOf('[')  // REMARK this only works as long as classNames don't contain '[' or ']' characters
	val eIdx = n.indexOf(']')
	val idx = Integer.parseInt(n.substring(sIdx + 1, eIdx)) - 1
	val className = n.substring(0, sIdx)
	if (s == null) {
		UiSelector().index(idx).className(className)    //REMARK the index HAS TO BE the first criteria, otherwise the selector may target the wrong element
	} else {
		s.childSelector(UiSelector().index(idx).className(className))
	}
}
val findByXPath: Selector = { xPath ->
	debugT( "find xPath", {
		val nodes = with(xPath.split("/".toRegex()).dropLastWhile({ it.isEmpty() })) { subList(2, size).toTypedArray() }    // the initial '//' creates two empty strings which we have to skip
		nodes.fold(null, processNode)!!
	}, inMillis = true)
}
val findByResId: Selector = { id -> debugT( "find ResId", {UiSelector().resourceId(id) }, inMillis = true)}
val findByClassName: Selector = { id -> debugT( "find className", {UiSelector().className(id) }, inMillis = true)}
val findByDescription: Selector = { id -> debugT( "find Desc", {UiSelector().descriptionContains(id) }, inMillis = true)}

/**
 * the action to be executed on object <O> as lambda function (e.g. `{o->o.click()}`), where R is the return type
 */
private typealias Action<O, R> = (O) -> R

/**
 * This function should be used by default based on the xPath of any target widget, even if a resourceId is given.
 * Because the resourceId is only guaranteed to be unique within the specific sub-tree but 'uncle' trees could contain the same id.
 * Meanwhile the xPath is uniquely defined by the hierarchical structure of the UI elements
 *
 * The [selector] should be chosen from the predefined [Selector] functions:
 * {[findByXPath], [findByResId], [findByClassName], [findByDescription]}
 *
 * If any additional selector becomes necessary it should be added to this file.
 *
 * @param id        the String used to find the target element via the given selector. This String must not be empty!
 * @param action    the [Action] to be executed on the [UiObject]
 * @param selector  the lambda function defining the UiSelector to find the target element.
 *          By default [findByXPath] is used.
 * @return true if the element could be found with `selector(id)` and the [action] execution was successful
 */
fun executeAction(device: UiDevice, action: Action<UiObject, Boolean>, id: String, selector: Selector = findByXPath): Boolean {
	assert(id.isNotEmpty(), { "parameter id must not be empty to use this function" })
	return debugT("find Object", {
		device.findObject(selector(id)).let {
			if (it.exists()) action(it) else {
				Log.w(uiaDaemon_logcatTag, "Target element could not be found with $id: $it")
				false
			}
		}
	}, inMillis = true)
}

/**
 * This is a fallback function which should be only used if [executeAction] failed (returned false).
 * This may be the case for system internal targets (like the keyboard keys) which are not visible to the [UiSelector] or
 * in the [UiObject2] structure.
 * This is still experimental and has to be tested thoroughly.
 * If that doesn't work reliable we will have to directly traverse the underlying AccessibilityNodes, by extending the UiObject classes.
 *
 * @param id        the resource id of the targeted UI element. This String must not be empty!
 * @param action    the action to be executed on the UiObject2 as lambda function (e.g. `{o->o.click()}`)
 * @throws DeviceDaemonException if action cannot be executed (target element could not be identified)
 */
fun executeAction2(device: UiDevice, action: Action<UiObject2, Unit>, id: String) {
	assert(id.isNotEmpty(), { "Widget resourceId must not be empty to use this function" })
	(device.findObject(By.res(id)) ?: throw DeviceDaemonException("object with id $id could not be found")).let {
		action(it)
	}
}
