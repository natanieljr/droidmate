package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.view.accessibility.AccessibilityNodeInfo

object UiSelector {
	@JvmStatic
	val permissionRequest: SelectorCondition = { it.viewIdResourceName == "com.android.packageinstaller:id/permission_allow_button"}
	@JvmStatic
	val ignoreSystemElem: SelectorCondition = { it.viewIdResourceName?.let{! it.startsWith("com.android.systemui")}?:false }
	// TODO check if need special case for packages "com.android.chrome" ??
	@JvmStatic
	val isActable: SelectorCondition = {
		it.isEnabled && it.isVisibleToUser && (it.isClickable || it.isCheckable || it.isLongClickable || it.isScrollable
				|| it.isEditable  || it.isFocusable )
	}

}

typealias SelectorCondition = (AccessibilityNodeInfo) -> Boolean
typealias XpathCondition = (AccessibilityNodeInfo,parentXpath: String) -> Boolean
