# UiAutomator #
[differences of UiObject and UiObject2](https://stackoverflow.com/questions/40881680/whats-is-the-difference-between-uiobject-and-uiobject2-other-than-uiautomator-2)
  * by default the UI-elements may have no id such that we have to rely on other means to uniquely identify the elements from the 'HierarchyDump' for later actions
  

## UiObject ##
  * UiObjects present a View on how to find the elements by the given selector.
  * These views can be reused.
  * In contrast to UiObject2 they are not linked to any specific object instance (AccessibilityNodeInfo).
  * Therefore they do not implement any hierarchical structure by default.
  * no reference to parent, however we can extent the object to get the AccessibilityNode of the parent if we really need to
    ```kotlin
    package android.support.test.uiautomator
    import android.view.accessibility.AccessibilityNodeInfo
    /**
     * However, we want to propagate actions upwards (e.g. clicks) if this action is not available for this UiObject
     * but for a parent in the current UiState.
     *
     */
    open class ExtendedUiObject(device: UiDevice, selector: UiSelector) : UiObject(device,selector) {
        @Throws(UiObjectNotFoundException::class)
        fun getParent(): AccessibilityNodeInfo {
            val node = findAccessibilityNodeInfo(Configurator.getInstance().waitForSelectorTimeout)
                    ?: throw UiObjectNotFoundException(selector.toString())
            return node.parent
        }
    }
    ```
  * supports selection by index
  * `parent_UiSelector.childSelector(<child UiSelector))` can be used to traverse the UI tree, i.e. to retrieve the child node (fulfilling the child selector criteria) from the selected parent node 

## UiObject2 ##
  * BySelector does not support selection via index
  * Moreover BySelector are not easily to work with chaining, as `BySelector.hasChild()` doesn't give the reference to the respective child but merely is a selector criterion for the 'parent' element and parents can only be accessed over the UiObject2.
  * However we can get `UiObject2.children.get(index)` to get the respective UiObject2
    If we do so we first have to determine the correct root element.
    The children of `BySelector.deph(0)` contain our root and some package='com.android.systemui' nodes which are system internal and should be ignored.
    

## Element Selection ##

  For elements only visible in the UiAutomater2.0 dump [UiSelector] (used for [UiObject]) does not find the elements, meanwhile [BySelector] is able to do so (at least for id's, for Xpath there is no precise solution for BySelector).
  But traversing the UiObject2 elements does not work either. 
  
  _Maybe we could directly traverse the AccessibilityNodeInfo in this case?_
  
  For example for the app 'ch.bailu.aat' the keyboard widgets cannot be found with the UiSelector but work just fine with the BySelector (RESOURCE_ID=com.google.android.inputmethod.latin:id/key_pos_0_1).
  Therefore we are going to need a hybrid approach for reliable target identification.
  `first try UiSelector and if !UiObject.exists() find the UiObject2`
  We do not always use UiObject2 as it refers to the real object instances (AccessibilityNodeInfo) and can be only used once.
  In particular to find any element by it's xPath we have to traverse the whole object trace, as BySelector do not support the [Index] property (which is not always the same as [Instance]).
