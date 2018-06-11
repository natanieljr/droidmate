package org.droidmate.exploration.actions

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.debug.debugT
import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.deviceInterface.DeviceLogsHandler
import org.droidmate.device.deviceInterface.IRobustDevice
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.exploration.statemodel.Widget
import org.droidmate.uiautomator_daemon.guimodel.CoordinateLongClickAction
import org.droidmate.uiautomator_daemon.guimodel.LongClickAction

private var performT: Long = 0
private var performN: Int = 1

open class LongClickExplorationAction(override val widget: Widget,
                                      private val useCoordinates: Boolean = true,
                                      private val delay: Int = 100): AbstractExplorationAction(){
	override fun toShortString(): String = "L-Cl"


	override fun performDeviceActions(app: IApk, device: IRobustDevice) = runBlocking {
		log.debug("1. Assert only background API logs are present, if any.")
		val logsHandler = DeviceLogsHandler(device)
		debugT("reading log", { logsHandler.readClearAndAssertOnlyBackgroundApiLogsIfAny() }, inMillis = true)

		log.debug("2. Perform widget long-click: $javaClass.")

		val x = widget.bounds.centerX.toInt()
		val y = widget.bounds.centerY.toInt()
		try {
			debugT("perform action on average ${performT / performN} ms", {
				launch {
					// do the perform as launch to inject a suspension point, as perform is currently no suspend function
					snapshot = when {
						useCoordinates -> device.perform(CoordinateLongClickAction(x, y))
						!useCoordinates -> device.perform(LongClickAction(widget.xpath, widget.resourceId))
						else -> throw UnexpectedIfElseFallthroughError("Action type not yet supported in ${this.javaClass.simpleName}")
					}
				}.join()
			}, timer = {
				performT += it / 1000000
				performN += 1
			}, inMillis = true)
		} catch (e: Exception) {
			if (!useCoordinates) {
				log.warn("2.1. Failed to click using XPath and resourceID, attempting restart UIAutomatorDaemon and to long-click coordinates: $javaClass.")
				device.restartUiaDaemon(false)
				snapshot =  device.perform(CoordinateLongClickAction(x, y))
			}
		}

		log.debug("3. Read and clear API logs if any, then seal logs reading.")
		debugT("read log after action", { logsHandler.readAndClearApiLogs() }, inMillis = true)
		logs = logsHandler.getLogs()

		delay(delay)
	}
}