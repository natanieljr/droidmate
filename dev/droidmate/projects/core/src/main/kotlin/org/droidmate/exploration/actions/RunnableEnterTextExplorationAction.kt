package org.droidmate.exploration.actions

import org.droidmate.android_sdk.IApk
import org.droidmate.exploration.device.DeviceLogsHandler
import org.droidmate.exploration.device.IRobustDevice
import org.droidmate.uiautomator_daemon.guimodel.TextAction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RunnableEnterTextExplorationAction constructor(action: EnterTextExplorationAction, timestamp: LocalDateTime, takeScreenshot: Boolean)
    : RunnableExplorationAction(action, timestamp, takeScreenshot) {

    companion object {
        private const val serialVersionUID: Long = 1
    }

    override fun performDeviceActions(app: IApk, device: IRobustDevice) {
        log.debug("1. Assert only background API logs are present, if any.")
        val logsHandler = DeviceLogsHandler(device)
        logsHandler.readClearAndAssertOnlyBackgroundApiLogsIfAny()

        val action = base as EnterTextExplorationAction
        //NEED FIX: In some cases the setting of text does open the keyboard and is hiding some widgets
        // 					but these widgets are still in the uiautomator dump. Therefore it may be that droidmate
        //					clicks on the keyboard thinking it clicked one of the widgets below it.
        //				  http://stackoverflow.com/questions/17223305/suppress-keyboard-after-setting-text-with-android-uiautomator
        //					-> It seems there is no reliable way to suppress the keyboard.
        log.debug("2. Perform widget text input: $action.")
        device.perform(TextAction(action.widget.xpath, action.widget.resourceId, action.textToEnter))

        log.debug("3. Read and clear API logs if any, then seal logs reading.")
        logsHandler.readAndClearApiLogs()
        this.logs = logsHandler.getLogs()

        log.debug("4. Get GUI snapshot.")
        this.snapshot = device.getGuiSnapshot()

        log.debug("5. Take a screenshot.")
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS")
        this.screenshot = device.takeScreenshot(app, timestamp.format(formatter)).toUri()
    }

}

