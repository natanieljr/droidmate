package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.view.accessibility.AccessibilityNodeInfo

interface UiSelector {

}

typealias SelectorCondition = (AccessibilityNodeInfo) -> Boolean