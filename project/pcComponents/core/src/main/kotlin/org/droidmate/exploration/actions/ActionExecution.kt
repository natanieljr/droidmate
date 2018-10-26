package org.droidmate.exploration.actions

import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.misc.debugT
import org.droidmate.device.android_sdk.DeviceException
import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.logcat.DeviceLogsHandler
import org.droidmate.device.deviceInterface.IRobustDevice
import org.droidmate.device.logcat.IApiLogcatMessage
import org.droidmate.device.logcat.MissingDeviceLogs
import org.droidmate.deviceInterface.DeviceResponse
import org.droidmate.deviceInterface.guimodel.ActionQueue
import org.droidmate.deviceInterface.guimodel.ActionType
import org.droidmate.deviceInterface.guimodel.ExplorationAction
import org.droidmate.deviceInterface.guimodel.LaunchApp
import org.droidmate.explorationModel.ActionResult
import org.droidmate.logging.Markers
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.math.max

private val log by lazy { LoggerFactory.getLogger("PC-ActionRun") }

private lateinit var snapshot: DeviceResponse
private lateinit var logs: MutableList<IApiLogcatMessage>
lateinit var exception: DeviceException

private var performT: Long = 0
private var performN: Int = 0

private fun performAction(action: ExplorationAction,app: IApk,device: IRobustDevice){
	when {
		action.name == ActionType.Terminate.name -> terminate(app,device)
		action is LaunchApp || (action is ActionQueue && action.actions.any { it is LaunchApp }) -> {
			resetApp(app,device)
			defaultExecution(action, device)
		}
		else -> defaultExecution(action, device)
	}
}

@Throws(DeviceException::class)
fun ExplorationAction.run(app: IApk, device: IRobustDevice): ActionResult{
	logs = MissingDeviceLogs
	snapshot = DeviceResponse.empty
	exception = DeviceExceptionMissing()
	// @formatter:on

	val startTime = LocalDateTime.now()
	try {
		log.trace("$name.run(app=${app.fileName}, device)")

		log.debug("1. Assert only background API logs are present, if any.")
		val logsHandler = DeviceLogsHandler(device)
//		debugT("reading logcat", { logsHandler.readClearAndAssertOnlyBackgroundApiLogsIfAny() }, inMillis = true)
//		logs = logsHandler.getLogs()

		log.debug("2. Perform action ${this}")

		performAction(this,app,device)

		log.debug("3. Read and clear API logs if any, then seal logs reading.")
		debugT("read logcat after action", { logsHandler.readAndClearApiLogs() }, inMillis = true)
		logs = logsHandler.getLogs()

		log.trace("$name.run(app=${app.fileName}, device) - DONE")
	} catch (e: DeviceException) {
		exception = e
		log.warn(Markers.appHealth, "! Caught ${e.javaClass.simpleName} while performing device actionTrace of ${this}. " +
				"Returning failed ${javaClass.simpleName} with the exception assigned to a field.")
	}
	val endTime = LocalDateTime.now()

	// For post-conditions, see inside the constructor call made line below.
	return ActionResult(this, startTime, endTime, logs, snapshot, exception = exception.toString(), screenshot = snapshot.screenshot)

}


@Throws(DeviceException::class)
private fun defaultExecution(action: ExplorationAction, device: IRobustDevice){
	try {
		debugT("perform $action on average ${performT / max(performN, 1)} ms", {
			runBlocking {
				launch {
					// do the perform as launch to inject a suspension point, as perform is currently no suspend function
					snapshot = device.perform(action)
				}.join()
			}
		}, timer = {
			performT += it / 1000000
			performN += 1
		}, inMillis = true)
	} catch (e: Exception) {
		log.warn("2.1. Failed to perform $action, retry once")
		device.restartUiaDaemon(false)
		snapshot =  device.perform(action)
	}
}

private fun resetApp(app: IApk, device: IRobustDevice){
	log.debug("LaunchApp action was issued this will kill the current app process if it is active and re-launch it")
	log.debug("try to terminate app process")
	device.clearPackage(app.packageName)

	log.debug("Ensure home screen is displayed.")
	device.ensureHomeScreenIsDisplayed()

	log.debug("Ensure app is not running.")
	if (device.appIsRunning(app.packageName)) {
		log.trace("App is still running. Clearing package again.")
		device.clearPackage(app.packageName)
	}
}

private fun terminate(app: IApk, device: IRobustDevice){
	log.debug("2.0 Close monitor servers, if any.")
	device.closeMonitorServers()
	log.debug("2.1 Clear package ${app.packageName}}.")
	device.clearPackage(app.packageName)

	log.debug("2.2 Assert app is not running.")
	assertAppIsNotRunning(device, app)

	log.debug("2.3 Ensure home screen is displayed.")
	snapshot = device.ensureHomeScreenIsDisplayed()

	log.debug("5. Ensure app is not running.")
	if (device.appIsRunning(app.packageName)) {
		log.trace("App is still running. Clearing package again.")
		device.clearPackage(app.packageName)
	}

}
@Throws(DeviceException::class)
private fun assertAppIsNotRunning(device: IRobustDevice, apk: IApk) {
	assert(device.appIsNotRunning(apk))
}
