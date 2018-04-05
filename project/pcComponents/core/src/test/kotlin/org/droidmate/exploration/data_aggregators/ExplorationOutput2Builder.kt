// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.exploration.data_aggregators

class ExplorationOutput2Builder {
	// TODO Fix tests

	/*private lateinit var currentlyBuiltApkOut2: ExplorationContext
	private val builtOutput: ExplorationOutput2 = ExplorationOutput2(ArrayList())

	companion object {
			@JvmStatic
			fun build(): ExplorationOutput2 {
					val builder = ExplorationOutput2Builder()

					return builder.builtOutput
			}
	}

	fun apk(attributes: Map<String, Any>, apkBuildDefinition: () -> Any) {
			assert(attributes["name"] is String)
			assert(attributes["monitorInitTime"] is LocalDateTime)
			assert(attributes["explorationStartTime"] is LocalDateTime)
			assert(attributes["explorationEndTimeMss"] is Int)

			val packageName = attributes["name"]!! as String
			this.currentlyBuiltApkOut2 = ExplorationContext(
							ApkTestHelper.build(
											packageName,
											"$packageName/$packageName.MainActivity",
											packageName + "1",
											"applicationLabel"
							)
			)
			this.currentlyBuiltApkOut2.explorationStartTime = attributes["explorationStartTime"]!! as LocalDateTime
			this.currentlyBuiltApkOut2.explorationEndTime = explorationStartPlusMss(attributes["explorationEndTimeMss"]!! as Int)

			apkBuildDefinition()

			this.currentlyBuiltApkOut2.verify()

			builtOutput.add(currentlyBuiltApkOut2)
	}

	private fun actRes(attributes: Map<String, Any>) {
			val runnableAction = buildRunnableAction(attributes)
			val result = buildActionResult(attributes)
			currentlyBuiltApkOut2.add(runnableAction, result)
	}

	private fun buildRunnableAction(attributes: Map<String, Any>): RunnableExplorationAction {
			assert(attributes["mss"] is Int)
			val mssSinceExplorationStart = attributes["mss"] as Int? ?: 0
			val timestamp = explorationStartPlusMss(mssSinceExplorationStart)

			return parseRunnableAction(attributes["action"] as String, timestamp)
	}

	internal fun buildActionResult(attributes: Map<String, Any>): ActionResult {
			val deviceLogs = buildDeviceLogs(attributes)
			val guiSnapshot = attributes["guiSnapshot"] as IDeviceGuiSnapshot? ?: UiautomatorWindowDumpTestHelper.newHomeScreenWindowDump()

			val successful = if (attributes.containsKey("successful")) attributes["successful"] as Boolean else true

			val exception = if (successful) DeviceExceptionMissing() else
					DeviceException("Exception created in ${ExplorationOutput2Builder::class.java.simpleName}.buildActionResult()")

			val packageName = attributes["packageName"] as String? ?: currentlyBuiltApkOut2.packageName
			assert(packageName.isNotEmpty())

			return ActionResult(EmptyAction(), LocalDateTime.now(), LocalDateTime.now(), deviceLogs, guiSnapshot,
					exception, URI.create("file://."))
	}


	@Suppress("UNCHECKED_CAST")
	private fun buildDeviceLogs(attributes: Map<String, Any>): IDeviceLogs {
			val apiLogs = attributes["logs"] as LinkedList<LinkedList<String>>? ?: LinkedList()

			val deviceLogs = DeviceLogs()

			deviceLogs.apiLogs = apiLogs.map {

					assert(it.size == 2)
					val methodName = it[0]
					val mssSinceExplorationStart = it[1].toInt()

					ApiLogcatMessageTestHelper.newApiLogcatMessage(
									mutableMapOf("time" to explorationStartPlusMss(mssSinceExplorationStart),
													"methodName" to methodName,
													// Minimal stack trace to pass all the validation checks.
													// In particular, the ->Socket.<init> is enforced by asserts in org.droidmate.report.FilteredDeviceLogs.Companion.isStackTraceOfMonitorTcpServerSocketInit
													"stackTrace" to "$Api.monitorRedirectionPrefix->Socket.<init>->$currentlyBuiltApkOut2.packageName")
					)
			}.toMutableList()

			return deviceLogs
	}

	private fun parseRunnableAction(actionString: String, timestamp: LocalDateTime): RunnableExplorationAction {
			val action: ExplorationAction = when (actionString) {
					"reset" -> ExplorationAction.newResetAppExplorationAction()
					"click" -> ExplorationActionTestHelper.newWidgetClickExplorationAction()
					"terminate" -> ExplorationAction.newTerminateExplorationAction()
					else -> throw UnexpectedIfElseFallthroughError()

			}
			return RunnableExplorationAction.from(action, timestamp)
	}

	private fun explorationStartPlusMss(mss: Int): LocalDateTime
					= datePlusMss(this.currentlyBuiltApkOut2.explorationStartTime, mss)

	private fun datePlusMss(date: LocalDateTime, mss: Int): LocalDateTime
					= date.plusNanos((mss * 1000000).toLong())*/
}