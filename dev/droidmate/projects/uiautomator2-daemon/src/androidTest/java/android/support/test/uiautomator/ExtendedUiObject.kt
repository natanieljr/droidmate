package android.support.test.uiautomator

import android.support.test.InstrumentationRegistry
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

private const val logTag = "droidmate/SearchCondition"
val hasInteractive = object : SearchCondition<Boolean>() {
    private val device: UiDevice by lazy { UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()) }
    private val interactive: (UiObject2) -> Boolean = { o ->
        !o.applicationPackage.startsWith("com.android.")   // ignore system widgets
                && (o.isClickable || o.isLongClickable //|| o.isCheckable || o.isFocusable || o.isScrollable
                )
    }

    private fun findInteractive(): UiObject2? {
        return try {
            device.findObjects(BySelector())
                    .find(interactive)
        } catch (e: StaleObjectException) {
            Log.w(logTag, "WARN: StaleObjectException in SearchCondition: ${e.message}\t${e.localizedMessage}")
            findInteractive()
        }
    }

    override fun apply(context: Searchable): Boolean {
        val t: UiObject2? = findInteractive()
        Log.d(logTag, "found any element= ${t != null}")
//        t?.run { Log.d(logTag, "found interactive element $resourceName,$applicationPackage,$className,$visibleCenter") }
        return t != null
    }
}

/**
 * UiObjects present a View on how to find the elements by the given selector.
 * These views can be reused.
 * In contrast to UiObject2 they are not linked to any specific object instance (AccessibilityNodeInfo).
 * Therefore they do not implement any hierarchical structure by default.
 * However, we want to propagate actions upwards (e.g. clicks) if this action is not available for this UiObject
 * but for a parent in the current UiState.
 *
 */
open class ExtendedUiObject(device: UiDevice, selector: UiSelector) : UiObject(device, selector) {
    @Throws(UiObjectNotFoundException::class)
    fun getParent(): AccessibilityNodeInfo {
        val node = findAccessibilityNodeInfo(Configurator.getInstance().waitForSelectorTimeout)
                ?: throw UiObjectNotFoundException(selector.toString())
        return node.parent
    }

    @Throws(UiObjectNotFoundException::class)
    fun getIndex(): Int {
        val node = findAccessibilityNodeInfo(Configurator.getInstance().waitForSelectorTimeout)
                ?: throw UiObjectNotFoundException(selector.toString())
        TODO("get root accessibility node and fetch children to determine the index")
    }
}

/*
depending on what information we are going to need later on for data extraction the way to go is:
    [] extend by selector with Xpath
    extend UiObject2:
        [] for use of this custom selector
        [] (extend findObjects) to traverse the AccessibilityNode hierarchy for xPath (custom Id injection would be possible, but makes record & replay difficult)
        [] extend with function to retrieve additional information, e.g. isEditable => TextFields
    [] use this new extended UiObject for any device (inter)action
 */

