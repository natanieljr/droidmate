package org.droidmate.explorationModel.interaction

import org.droidmate.deviceInterface.communication.TimeFormattedLogMessageI
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.config.ConcreteId
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.dump.sep
import org.droidmate.explorationModel.config.dumpString
import org.droidmate.explorationModel.config.emptyId
import org.droidmate.explorationModel.config.idFromString
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

typealias DeviceLog = TimeFormattedLogMessageI
typealias DeviceLogs = List<DeviceLog>

@Suppress("DataClassPrivateConstructor")
data class Interaction constructor(val actionType: String, val targetWidget: Widget?,
                                   val startTimestamp: LocalDateTime, val endTimestamp: LocalDateTime,
                                   val successful: Boolean, val exception: String,
                                   val resState: ConcreteId, val deviceLogs: DeviceLogs = emptyList(),
                                   private val sep:String, val data: String="") {

	constructor(action: ExplorationAction, startTimestamp: LocalDateTime, endTimestamp: LocalDateTime,
	            deviceLogs: DeviceLogs, exception: String, successful: Boolean, resState: ConcreteId, sep:String)
			: this(action.name+"-${action.id}", widgetTargets.pollFirst(),
			startTimestamp, endTimestamp, successful, exception, resState, deviceLogs, sep, ExplorationTrace.computeData(action))

	constructor(res: ActionResult, prevStateId: ConcreteId, resStateId: ConcreteId, sep: String)
			: this(res.action, res.startTimestamp, res.endTimestamp, res.deviceLogs, res.exception, res.successful, resStateId, sep) {
		prevState = prevStateId
	}

	/** used for ActionQueue entries */
	constructor(action: ExplorationAction, res: ActionResult, prevStateId: ConcreteId, resStateId: ConcreteId, sep: String)
			: this(action.name, if(action.hasWidgetTarget) widgetTargets.pollFirst() else null, res.startTimestamp,
			res.endTimestamp, deviceLogs = res.deviceLogs, exception = res.exception, successful = res.successful,
			resState = resStateId, sep = sep, data = ExplorationTrace.computeData(action)) {
		prevState = prevStateId
	}

	/** used for ActionQueue sart/end Interaction */
	constructor(actionName:String, res: ActionResult, prevStateId: ConcreteId, resStateId: ConcreteId, sep: String)
			: this(actionName, null, res.startTimestamp,
			res.endTimestamp, deviceLogs = res.deviceLogs, exception = res.exception, successful = res.successful,
			resState = resStateId, sep = sep) {
		prevState = prevStateId
	}


	lateinit var prevState: ConcreteId

	/**
	 * Time the strategy pool took to select a strategy and a create an action
	 * (used to measure overhead for new exploration strategies)
	 */
	val decisionTime: Long by lazy { ChronoUnit.MILLIS.between(startTimestamp, endTimestamp) }

	@JvmOverloads
	fun actionString(chosenFields: Array<ActionDataFields> = ActionDataFields.values()): String = chosenFields.joinToString(separator = sep) {
		when (it) {
			ActionDataFields.Action -> actionType
			ActionDataFields.StartTime -> startTimestamp.toString()
			ActionDataFields.EndTime -> endTimestamp.toString()
			ActionDataFields.Exception -> exception
			ActionDataFields.SuccessFul -> successful.toString()
			ActionDataFields.PrevId -> prevState.dumpString()
			ActionDataFields.DstId -> resState.dumpString()
			ActionDataFields.WId -> targetWidget?.run { id.dumpString() } ?: "null"
			ActionDataFields.Data -> data
		}
	}

	companion object {
//		@JvmStatic operator fun invoke(res:ActionResult, resStateId:ConcreteId, prevStateId: ConcreteId):Interaction =
//				Interaction(res.action,res.startTimestamp,res.endTimestamp,res.deviceLogs,res.screenshot,res.exception,res.successful,resStateId).apply { prevState = prevStateId }

		@JvmStatic
		fun createFromString(e: List<String>, target: Widget?, contentSeparator: String): Interaction = Interaction(
				actionType = e[ActionDataFields.Action.ordinal], targetWidget = target, startTimestamp = LocalDateTime.parse(e[ActionDataFields.StartTime.ordinal]),
				endTimestamp = LocalDateTime.parse(e[ActionDataFields.EndTime.ordinal]), successful = e[ActionDataFields.SuccessFul.ordinal].toBoolean(),
				exception = e[ActionDataFields.Exception.ordinal], resState = idFromString(e[ActionDataFields.DstId.ordinal]), sep = contentSeparator
				, data = e[ActionDataFields.Data.ordinal]
		).apply { prevState = idFromString(e[ActionDataFields.PrevId.ordinal]) }

		@JvmStatic
		val empty: Interaction by lazy {
			Interaction("EMPTY", null, LocalDateTime.MIN, LocalDateTime.MIN, true, "root action", emptyId, sep = ";"  //FIXME sep should be read from eContext instead
			).apply { prevState = emptyId }
		}

		@JvmStatic
		fun emptyWithWidget(widget: Widget?): Interaction =
			Interaction("EMPTY", widget, LocalDateTime.MIN, LocalDateTime.MIN, true, "root action", emptyId, sep = ";"  //FIXME sep should be read from eContext instead
			).apply { prevState = emptyId }


		@JvmStatic val header:(String)-> String = { sep -> ActionDataFields.values().joinToString(separator = sep) { it.header } }
		@JvmStatic val widgetIdx = ActionDataFields.WId.ordinal
		@JvmStatic val resStateIdx = ActionDataFields.DstId.ordinal
		@JvmStatic val srcStateIdx = ActionDataFields.PrevId.ordinal

		enum class ActionDataFields(var header: String = "") { PrevId("Source State"), Action, WId("Interacted Widget"),
			DstId("Resulting State"), StartTime, EndTime, SuccessFul, Exception, Data;

			init {
				if (header == "") header = name
			}
		}
	}

	override fun toString(): String {
		@Suppress("ReplaceSingleLineLet")
		return "$actionType: widget[${targetWidget?.let { it.dataString("\t") }}]:\n${prevState.dumpString()}->${resState.dumpString()}"
	}
}

var widgetTargets = LinkedList<Widget>()  //TODO this should probably be in the model or trace instead