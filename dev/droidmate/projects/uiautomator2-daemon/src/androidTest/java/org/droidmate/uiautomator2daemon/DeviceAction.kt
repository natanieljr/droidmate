package org.droidmate.uiautomator2daemon

import android.content.Context
import android.net.wifi.WifiManager
import android.support.test.uiautomator.*
import android.util.Log
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants.uiaDaemon_logcatTag
import org.droidmate.uiautomator_daemon.guimodel.*

/**
 * Created by J.H. on 05.02.2018.
 */
internal sealed class  DeviceAction{
    val defaultTimeout:Long=2000
    abstract fun execute(device: UiDevice, context: Context)
    protected fun waitForChanges(device: UiDevice, actionSuccessful:Boolean){
        if(actionSuccessful){
            device.waitForWindowUpdate(null,defaultTimeout)
            device.waitForIdle(defaultTimeout)
        }
    }

    companion object {
        fun fromAction(a:Action):DeviceAction = with(a){
            return when(this){
                is WaitAction -> DeviceWaitAction(target,criteria)
                is LongClickAction -> DeviceLongClickAction(xPath,resId)
                is SwipeAction -> {
                    if(start==null||dst==null) throw NotImplementedError("swipe executions currently only support point to point execution (TODO)")
                    else DeviceSwipeAction(start!!,dst!!)
                }
                is TextAction -> DeviceTextAction(xPath,resId,text)
                is ClickAction -> DeviceClickAction(xPath,resId)
                is PressBack -> DevicePressBack()
                is PressHome -> DevicePressHome()
                is EnableWifi -> DeviceEnableWifi()
                is LaunchApp -> DeviceLaunchApp(appLaunchIconName)
            }
        }
    }
}

private sealed class DeviceObjectAction:DeviceAction(){
    lateinit var bySel:BySelector   // selector used for wait conditions
    protected fun targetSelector(xPath: String,resId: String=""): UiSelector {
        if(resId.isNotEmpty()){
            bySel = By.res(resId)
            return findByResId(resId)
        }
        return findByXPath(xPath)
    }
    // FIXME xpath visitor would be safer but for now use quick and dirty string operations
    // XPath (for this we need to visit the xpath and check for each visitStep if an element with this classname and given parent exists
    protected fun findByXPath(xPath: String): UiSelector {
        var s:UiSelector? = null
        val nodes = xPath.split("/".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
        for (n in nodes) {
            if (n.length < 4) continue  // the initial '//' creates two empty strings which we have to skip
            val sIdx = n.indexOf('[')  // REMARK this only works as long as classNames don't contain '[' or ']' characters
            val eIdx = n.indexOf(']')
            val idx = Integer.parseInt(n.substring(sIdx + 1, eIdx)) - 1
            val className = n.substring(0, sIdx)
            if (s == null) {
                bySel = By.clazz(className)
                s = UiSelector().className(className).instance(idx)
            }else {
                bySel.hasChild(By.clazz(className))
                s = s.childSelector(UiSelector().className(className).instance(idx))
            }
        }
        return s!!
    }
    protected fun findByResId(resourceId:String): UiSelector = UiSelector().resourceId(resourceId)
    protected fun findByClassName(id:String): UiSelector = UiSelector().className(id)
    protected fun findByDescription(id:String): UiSelector = UiSelector().descriptionContains(id)
}

private class DevicePressBack:DeviceAction() {
    override fun execute(device: UiDevice, context: Context) {
        waitForChanges(device,device.pressBack())
    }
}

private class DevicePressHome:DeviceAction() {
    override fun execute(device: UiDevice, context: Context) {
        waitForChanges(device,device.pressHome())
    }
}

private class DeviceEnableWifi:DeviceAction() {
    /**
     * Based on: http://stackoverflow.com/a/12420590/986533
     */
    override fun execute(device: UiDevice, context: Context) {
        Log.d(uiaDaemon_logcatTag, "Ensuring WiFi is turned on.")
        val wfm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiEnabled = wfm.setWifiEnabled(true)

        if (!wifiEnabled) Log.w(uiaDaemon_logcatTag, "Failed to ensure WiFi is enabled!")
    }
}

private data class DeviceLaunchApp(val appLaunchIconName: String):DeviceAction() {
    private val APP_LIST_RES_ID = "com.google.android.googlequicksearchbox:id/apps_list_view"
    override fun execute(device: UiDevice, context: Context) {
        Log.d(uiaDaemon_logcatTag, "Launching app by navigating to and clicking icon with text " + appLaunchIconName)

        val clickResult: Boolean
        try {
            val app = navigateToAppLaunchIcon(appLaunchIconName,device)
            Log.v(uiaDaemon_logcatTag, "Pressing the $appLaunchIconName app icon to launch it.")
            clickResult = app.clickAndWaitForNewWindow()

        } catch (e: UiObjectNotFoundException) {
            Log.w(uiaDaemon_logcatTag,
                    String.format("Attempt to navigate to and click on the icon labeled '%s' to launch the app threw an exception: %s: %s",
                            appLaunchIconName, e.javaClass.simpleName, e.localizedMessage))
            Log.d(uiaDaemon_logcatTag, "Pressing 'home' button after failed app launch.")
            waitForChanges(device,device.pressHome())
            return
        }

        if (clickResult)
            waitForChanges(device,clickResult)
        else
            Log.w(uiaDaemon_logcatTag, "A click on the icon labeled '$appLaunchIconName' to launch the app returned false")
    }

    @Throws(UiObjectNotFoundException::class)
    private fun navigateToAppLaunchIcon(appLaunchIconName: String,device: UiDevice): UiObject {
        // Simulate a short press on the HOME button.
        device.pressHome()

        // We’re now in the home screen. Next, we want to simulate
        // a user bringing up the All Apps screen.
        // If you use the uiautomatorviewer tool to capture a snapshot
        // of the Home screen, notice that the All Apps button’s
        // content-description property has the value "Apps".  We can
        // use this property to create a UiSelector to find the button.
        val allAppsButton = device.findObject(UiSelector().description("Apps"))

        // Simulate a click to bring up the All Apps screen.
        allAppsButton.clickAndWaitForNewWindow()


        // In the All Apps screen, the app launch icon is located in
        // the Apps tab. To simulate the user bringing up the Apps tab,
        // we create a UiSelector to find a tab with the text
        // label "Apps".
        try {
            val appsTab = device.findObject(UiSelector().text("Apps"))

            // Simulate a click to enter the Apps tab.
            appsTab.click()
        } catch (e: UiObjectNotFoundException) {
            Log.w(uiaDaemon_logcatTag, "This device does not have an 'Apps' and a 'Widgets' tab, skipping.")
        }

        // Next, in the apps tabs, we can simulate a user swiping until
        // they come to the app launch icon. Since the container view
        // is scrollable, we can use a UiScrollable object.
        var appViews: UiScrollable

        try {
            Log.i(uiaDaemon_logcatTag, "Attempting to locate app list by resourceId.")
            appViews = UiScrollable(UiSelector().resourceId(APP_LIST_RES_ID))

            // Set the swiping mode to horizontal (the default is vertical)
            appViews.setAsHorizontalList()

            // Create a UiSelector to find the app launch icon and simulate
            // a user click to launch the app.
            return appViews.getChildByText(
                    UiSelector().className(android.widget.TextView::class.java.name),
                    appLaunchIconName)
        } catch (e: UiObjectNotFoundException) {
            Log.i(uiaDaemon_logcatTag, "It was not possible to locate app list by resourceId, using heuristic.")
            appViews = UiScrollable(UiSelector().scrollable(true))
        }

        // Set the swiping mode to horizontal (the default is vertical)
        appViews.setAsHorizontalList()

        // Create a UiSelector to find the app launch icon and simulate
        // a user click to launch the app.
        return appViews.getChildByText(
                UiSelector().className(android.widget.TextView::class.java.name),
                appLaunchIconName)
    }
}

private data class DeviceSwipeAction(val start:Pair<Int,Int>, val dst:Pair<Int,Int>,val xPath: String="",val direction:String=""):DeviceAction() {
    private val x0:Int inline get()=start.first
    private val y0:Int inline get()=start.second
    private val x1:Int inline get()=dst.first
    private val y1:Int inline get()=dst.second
    override fun execute(device: UiDevice, context: Context) {
        Log.d(uiaDaemon_logcatTag, "Swiping from (x,y) coordinates ($x0,$y0) to ($x1,$y1)")
        assert(x0>=0 && x0<device.displayWidth,{"Error on swipe invalid x0:$x0"})
        assert(y0>=0 && y0<device.displayHeight,{"Error on swipe invalid y0:$y0"})
        assert(x1>=0 && x1<device.displayWidth,{"Error on swipe invalid x1:$x1"})
        assert(y1>=0 && y1<device.displayHeight,{"Error on swipe invalid y1:$y1"})

        val success = device.swipe(x0,y0,x1,y1,35)
        if (!success) Log.e(uiaDaemon_logcatTag, "Swipe failed: from (x,y) coordinates ($x0,$y0) to ($x1,$y1)")
        // we could issue a wait action on failure (e.g. waitExist for widget at position)
    }
}

private data class DeviceWaitAction(private val id:String, private val criteria:WidgetSelector):DeviceObjectAction() {
    override fun execute(device: UiDevice, context: Context) {
        Log.d(uiaDaemon_logcatTag, "Wait for element to exist"+this.toString())
        val target:UiObject = when (criteria) {
            WidgetSelector.ResourceId -> findByResId(id)
            WidgetSelector.ClassName -> findByClassName(id)
            WidgetSelector.ContentDesc -> findByDescription(id)
            WidgetSelector.XPath  -> findByXPath(id)
        }.let{device.findObject(it)}

        Log.v(uiaDaemon_logcatTag, "wait up to 10s to find widget $target")
        target.waitForExists(10000)  // waits until the view becomes visible on the display, or until the timeout (ms) has elapsed
    }
}

private data class DeviceClickAction(val xPath: String, val resId:String):DeviceObjectAction() {
    override fun execute(device: UiDevice, context: Context) {
        val target = device.findObject(targetSelector(xPath,resId))
        var success = target.click()
        if(!success){
            Log.e(uiaDaemon_logcatTag,"The operation click for widget with appLaunchIconName=$resId xPath=$xPath failed (the 'click' method returned 'false'). Try to wait for exist for up to 2 seconds")
            target.waitForExists(1000)
            success = target.click()
            Log.i(uiaDaemon_logcatTag, "Second Try returned $success")
        }
        waitForChanges(device,success)
    }
}

private data class DeviceLongClickAction(val xPath: String, val resId:String):DeviceObjectAction() {
    override fun execute(device: UiDevice, context: Context) {
        val target = device.findObject(targetSelector(xPath,resId))
        var success = target.longClick()
        if(!success){
            Log.e(uiaDaemon_logcatTag,"The operation long-click for widget with appLaunchIconName=$resId xPath=$xPath failed (the 'longClick' method returned 'false'). Try to wait for exist for up to 2 seconds")
            target.waitForExists(1000)
            success = target.longClick()
            Log.i(uiaDaemon_logcatTag, "Second Try returned $success")
        }
        waitForChanges(device,success)
    }
}

private data class DeviceTextAction(val xPath: String, val resId:String, val text:String):DeviceObjectAction() {
    // TODO check if this is still an issue at all
    // NEED FIX: In some cases the setting of text does open the keyboard and is hiding some widgets
    // but these widgets are still in the uiautomator dump. Therefore it may be that droidmate
    // clicks on the keyboard thinking it clicked one of the widgets below it.
    // http://stackoverflow.com/questions/17223305/suppress-keyboard-after-setting-text-with-android-uiautomator
    // -> It seems there is no reliable way to suppress the keyboard.
    override fun execute(device: UiDevice, context: Context) {
        try {
            device.findObject(targetSelector(xPath,resId)).also { elem ->
                val success = elem.setText(text)
                if (success) device.findObject(bySel).wait(Until.textContains(text), defaultTimeout)
                else Log.w(uiaDaemon_logcatTag, "Failed to enter text in widget with resource id: $resId, xPath: $xPath")
            }
        } catch (e: UiObjectNotFoundException) {
            throw AssertionError("Assertion error:  UIObject not found. ResourceId: $resId, xPath: $xPath")
        }
    }
}