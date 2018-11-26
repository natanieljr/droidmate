package org.droidmate.explorationModel.interaction

import org.droidmate.deviceInterface.communication.TimeFormattedLogMessageI
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.emptyId
import org.droidmate.explorationModel.retention.StringCreator
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

typealias DeviceLog = TimeFormattedLogMessageI
typealias DeviceLogs = List<DeviceLog>

@Suppress("DataClassPrivateConstructor")
open class Interaction (
		@property:Persistent("Action", 1) val actionType: String,
		@property:Persistent("Interacted Widget", 2, PType.ConcreteId) val targetWidget: Widget?,
		@property:Persistent("StartTime", 4, PType.DateTime) val startTimestamp: LocalDateTime,
		@property:Persistent("EndTime", 5, PType.DateTime) val endTimestamp: LocalDateTime,
		@property:Persistent("SuccessFul", 6, PType.Boolean) val successful: Boolean,
		@property:Persistent("Exception", 7) val exception: String,
		@property:Persistent("Source State", 0, PType.ConcreteId) val prevState: ConcreteId,
		@property:Persistent("Resulting State", 3, PType.ConcreteId) val resState: ConcreteId,
		@property:Persistent("Data", 8) val data: String = "",
		val deviceLogs: DeviceLogs = emptyList(),
		@Suppress("unused") val meta: String = "") {

	constructor(res: ActionResult, prevStateId: ConcreteId, resStateId: ConcreteId, target: Widget?)
			: this(actionType = res.action.name, targetWidget = target,
			startTimestamp = res.startTimestamp, endTimestamp = res.endTimestamp, successful = res.successful,
			exception = res.exception, prevState = prevStateId, resState = resStateId, data = computeData(res.action),
			deviceLogs = res.deviceLogs,	meta = res.action.id.toString())

	/** used for ActionQueue entries */
	constructor(action: ExplorationAction, res: ActionResult, prevStateId: ConcreteId, resStateId: ConcreteId, target: Widget?)
			: this(action.name, target, res.startTimestamp,
			res.endTimestamp, successful = res.successful, exception = res.exception, prevState = prevStateId,
			resState = resStateId, data = computeData(action), deviceLogs = res.deviceLogs)

	/** used for ActionQueue start/end Interaction */
	internal constructor(actionName:String, res: ActionResult, prevStateId: ConcreteId, resStateId: ConcreteId)
			: this(actionName, null, res.startTimestamp,
			res.endTimestamp, successful = res.successful, exception = res.exception, prevState = prevStateId,
			resState = resStateId, deviceLogs = res.deviceLogs)

	/** used for parsing from string */
	constructor(actionType: String, target: Widget?, startTimestamp: LocalDateTime, endTimestamp: LocalDateTime,
	            successful: Boolean, exception: String, resState: ConcreteId, prevState: ConcreteId)
			: this(actionType = actionType, targetWidget = target, startTimestamp = startTimestamp, endTimestamp = endTimestamp,
			successful = successful, exception = exception, prevState = prevState, resState = resState)


	/**
	 * Time the strategy pool took to select a strategy and a create an action
	 * (used to measure overhead for new exploration strategies)
	 */
	val decisionTime: Long by lazy { ChronoUnit.MILLIS.between(startTimestamp, endTimestamp) }

	@JvmOverloads
	@Deprecated("to be removed", ReplaceWith("StringCreator.createActionString(a: Interaction, sep: String)"))
	fun actionString(chosenFields: Array<ActionDataFields> = ActionDataFields.values(), sep: String = ";"): String = chosenFields.joinToString(separator = sep) {
		when (it) {
			ActionDataFields.Action -> actionType
			ActionDataFields.StartTime -> startTimestamp.toString()
			ActionDataFields.EndTime -> endTimestamp.toString()
			ActionDataFields.Exception -> exception
			ActionDataFields.SuccessFul -> successful.toString()
			ActionDataFields.PrevId -> prevState.toString()
			ActionDataFields.DstId -> resState.toString()
			ActionDataFields.WId -> targetWidget?.id.toString()
			ActionDataFields.Data -> data
		}
	}

	companion object {

		@JvmStatic val actionTypeIdx = StringCreator.actionProperties.indexOfFirst { it.property == Interaction::actionType }
		@JvmStatic val widgetIdx = StringCreator.actionProperties.indexOfFirst { it.property == Interaction::targetWidget }
		@JvmStatic val resStateIdx = StringCreator.actionProperties.indexOfFirst { it.property == Interaction::resState }
		@JvmStatic val srcStateIdx = StringCreator.actionProperties.indexOfFirst { it.property == Interaction::prevState }

		@JvmStatic
		fun computeData(e: ExplorationAction):String = when(e){
			is TextInsert -> e.text
			is Swipe -> "${e.start.first},${e.start.second} TO ${e.end.first},${e.end.second}"
			is RotateUI -> e.rotation.toString()
			else -> ""
		}

		@JvmStatic
		val empty: Interaction by lazy {
			Interaction("EMPTY", null, LocalDateTime.MIN, LocalDateTime.MIN, true,
					"root action", emptyId, prevState = emptyId)
		}

		@Deprecated("to be removed in next version")
		enum class ActionDataFields(var header: String = "") { PrevId("Source State"), Action, WId("Interacted Widget"),
			DstId("Resulting State"), StartTime, EndTime, SuccessFul, Exception, Data;

			init {
				if (header == "") header = name
			}
		}
	}

	override fun toString(): String {
		@Suppress("ReplaceSingleLineLet")
		return "$actionType: widget[${targetWidget?.let { it.toString() }}]:\n$prevState->$resState"
	}

	fun copy(prevState: ConcreteId, resState: ConcreteId): Interaction
		= Interaction(actionType = actionType, targetWidget = targetWidget, startTimestamp = startTimestamp,
			endTimestamp = endTimestamp, successful = successful, exception = exception,
			prevState = prevState, resState = resState, data = data, deviceLogs = deviceLogs, meta = meta)
}
