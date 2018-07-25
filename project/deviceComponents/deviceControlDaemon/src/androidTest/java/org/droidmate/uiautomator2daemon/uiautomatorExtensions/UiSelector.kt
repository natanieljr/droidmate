package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.view.accessibility.AccessibilityNodeInfo

object UiSelector {
	@JvmStatic
	val isWebView: SelectorCondition = { it,_ -> it.packageName != "android.webkit.WebView" }

	@JvmStatic
	val permissionRequest: SelectorCondition = { node,_ -> node.viewIdResourceName == "com.android.packageinstaller:id/permission_allow_button"}
	@JvmStatic
	val ignoreSystemElem: SelectorCondition = { node,_ -> node.viewIdResourceName?.let{! it.startsWith("com.android.systemui")}?:false }
	// TODO check if need special case for packages "com.android.chrome" ??
	@JvmStatic
	val isActable: SelectorCondition = {it,_ ->
		it.isEnabled && it.isVisibleToUser
				&& (it.isClickable || it.isCheckable || it.isLongClickable || it.isScrollable
				|| it.isEditable  || it.isFocusable )
	}

	@JvmStatic
	val actableAppElem = { node:AccessibilityNodeInfo, xpath:String ->
		UiSelector.ignoreSystemElem(node,xpath) && !isWebView(node,xpath) && // look for internal elements instead of WebView layouts
				(UiSelector.isActable(node,xpath) || UiSelector.permissionRequest(node,xpath))
	}

}

typealias SelectorCondition = (AccessibilityNodeInfo,xPath:String) -> Boolean
