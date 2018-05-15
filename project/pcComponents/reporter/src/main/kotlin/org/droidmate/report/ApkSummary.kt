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
package org.droidmate.report

import com.konradjamrozik.Resource
import com.konradjamrozik.uniqueItemsWithFirstOccurrenceIndex
import org.droidmate.device.android_sdk.DeviceException
import org.droidmate.apis.IApiLogcatMessage
import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.actions.DeviceExceptionMissing
import org.droidmate.exploration.ExplorationContext
import org.droidmate.logging.LogbackConstants
import org.droidmate.misc.minutesAndSeconds
import org.droidmate.misc.replaceVariable
import org.droidmate.report.misc.resetActionsCount
import java.time.Duration

class ApkSummary {

	companion object {

		fun build(data: ExplorationContext): String {
			return build(Payload(data))
		}

		fun build(payload: Payload): String {

			return with(payload) {
				// @formatter:off
				StringBuilder(template)
						.replaceVariable("exploration_title", "droidmate-run:$appPackageName")
						.replaceVariable("total_run_time", totalRunTime.minutesAndSeconds)
						.replaceVariable("total_actions_count", totalActionsCount.toString().padStart(4, ' '))
						.replaceVariable("total_resets_count", totalResetsCount.toString().padStart(4, ' '))
						.replaceVariable("exception", exception.messageIfAny())
						.replaceVariable("unique_apis_count", uniqueApisCount.toString())
						.replaceVariable("api_entries", apiEntries.joinToString(separator = System.lineSeparator()))
						.replaceVariable("unique_api_event_pairs_count", uniqueEventApiPairsCount.toString())
						.replaceVariable("api_event_entries", apiEventEntries.joinToString(separator = System.lineSeparator()))
						.toString()
			}
			// @formatter:on
		}

		private val template: String by lazy {
			Resource("apk_exploration_summary_template.txt").text
		}

		private fun DeviceException.messageIfAny(): String {
			return if (this is DeviceExceptionMissing)
				""
			else {
				"\n* * * * * * * * * *\n" +
						"WARNING! This exploration threw an exception.\n\n" +
						"Exception message: '${this.message}'.\n\n" +
						LogbackConstants.err_log_msg + System.lineSeparator() +
						"* * * * * * * * * *\n"
			}
		}
	}

	@Suppress("unused") // Kotlin BUG on private constructor(data: IApkExplorationOutput2, uniqueApiLogsWithFirstTriggeringActionIndex: Map<IApiLogcatMessage, Int>)
	data class Payload(
			val appPackageName: String,
			val totalRunTime: Duration,
			val totalActionsCount: Int,
			val totalResetsCount: Int,
			val exception: DeviceException,
			val uniqueApisCount: Int,
			val apiEntries: List<ApiEntry>,
			val uniqueEventApiPairsCount: Int,
			val apiEventEntries: List<ApiEventEntry>
	) {

		constructor(data: ExplorationContext) : this(
				data,
				data.uniqueApiLogsWithFirstTriggeringActionIndex,
				data.uniqueEventApiPairsWithFirstTriggeringActionIndex
		)

		private constructor(
				data: ExplorationContext,
				uniqueApiLogsWithFirstTriggeringActionIndex: Map<IApiLogcatMessage, Int>,
				uniqueEventApiPairsWithFirstTriggeringActionIndex: Map<Pair<ActionData, IApiLogcatMessage>, Int>
		) : this(
				appPackageName = data.apk.packageName,
				totalRunTime = data.getExplorationDuration(),
				totalActionsCount = data.actionTrace.size,
				totalResetsCount = data.resetActionsCount,
				exception = data.exception,
				uniqueApisCount = uniqueApiLogsWithFirstTriggeringActionIndex.keys.size,
				apiEntries = uniqueApiLogsWithFirstTriggeringActionIndex.map {
					val (apiLog: IApiLogcatMessage, firstIndex: Int) = it
					ApiEntry(
							time = Duration.between(data.explorationStartTime, apiLog.time),
							actionIndex = firstIndex,
							threadId = apiLog.threadId.toInt(),
							apiSignature = apiLog.uniqueString
					)
				},
				uniqueEventApiPairsCount = uniqueEventApiPairsWithFirstTriggeringActionIndex.keys.size,
				apiEventEntries = uniqueEventApiPairsWithFirstTriggeringActionIndex.map {
					val (eventApiPair, firstIndex: Int) = it
					val (event: ActionData, apiLog: IApiLogcatMessage) = eventApiPair
					ApiEventEntry(
							ApiEntry(
									time = Duration.between(data.explorationStartTime, apiLog.time),
									actionIndex = firstIndex,
									threadId = apiLog.threadId.toInt(),
									apiSignature = apiLog.uniqueString
							),
							event = event.actionString()
					)
				}
		)

		companion object {
			val ExplorationContext.uniqueApiLogsWithFirstTriggeringActionIndex: Map<IApiLogcatMessage, Int>
				get() {
					return this.actionTrace.getActions().uniqueItemsWithFirstOccurrenceIndex(
							extractItems = { it.deviceLogs.apiLogs },
							extractUniqueString = { it.uniqueString }
					)
				}

			val ExplorationContext.uniqueEventApiPairsWithFirstTriggeringActionIndex: Map<Pair<ActionData, IApiLogcatMessage>, Int>
				get() {

					return this.actionTrace.getActions().uniqueItemsWithFirstOccurrenceIndex(
							extractItems = { it.deviceLogs.apiLogs.map { apiLog -> Pair(it, apiLog) } },
							extractUniqueString = { (action, api) -> action.actionString() + "_" + api.uniqueString }
					)
				}

		}
	}

	data class ApiEntry(val time: Duration, val actionIndex: Int, val threadId: Int, val apiSignature: String) {
		companion object {
			private const val actionIndexPad: Int = 7
			private const val threadIdPad: Int = 7
		}

		override fun toString(): String {
			val actionIndexFormatted = "$actionIndex".padStart(actionIndexPad)
			val threadIdFormatted = "$threadId".padStart(threadIdPad)
			return "${time.minutesAndSeconds} $actionIndexFormatted $threadIdFormatted  $apiSignature"
		}
	}

	data class ApiEventEntry(private val apiEntry: ApiEntry, val event: String) {
		companion object {
			private const val actionIndexPad: Int = 7
			private const val threadIdPad: Int = 7
			private const val eventPadEnd: Int = 69
		}

		override fun toString(): String {
			val actionIndexFormatted = "${apiEntry.actionIndex}".padStart(actionIndexPad)
			val eventFormatted = event.padEnd(eventPadEnd)
			val threadIdFormatted = "${apiEntry.threadId}".padStart(threadIdPad)

			return "${apiEntry.time.minutesAndSeconds} $actionIndexFormatted  $eventFormatted $threadIdFormatted  ${apiEntry.apiSignature}"
		}
	}
}