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

package org.droidmate.exploration.statemodel

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.sendBlocking
import kotlinx.coroutines.experimental.sync.Mutex
import kotlinx.coroutines.experimental.sync.withLock
import org.droidmate.configuration.ConfigProperties.ModelProperties.dump.sep
import org.droidmate.debug.debugT
import org.droidmate.device.android_sdk.DeviceException
import org.droidmate.device.deviceInterface.IDeviceLogs
import org.droidmate.device.deviceInterface.MissingDeviceLogs
import org.droidmate.deviceInterface.guimodel.ActionQueue
import org.droidmate.deviceInterface.guimodel.EmptyAction
import org.droidmate.deviceInterface.guimodel.ExplorationAction
import org.droidmate.exploration.actions.widgetTargets
import org.droidmate.exploration.statemodel.features.ModelFeature
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.properties.Delegates

@Suppress("DataClassPrivateConstructor")
data class ActionData constructor(val actionType: String, val targetWidget: Widget?,
                                          val startTimestamp: LocalDateTime, val endTimestamp: LocalDateTime,
                                          val successful: Boolean, val exception: String,
                                          val resState: ConcreteId, val deviceLogs: IDeviceLogs = MissingDeviceLogs,
                                          private val sep:String) {

	constructor(action: ExplorationAction, startTimestamp: LocalDateTime, endTimestamp: LocalDateTime,
	            deviceLogs: IDeviceLogs, exception: DeviceException, successful: Boolean, resState: ConcreteId, sep:String)
			: this(action.name, widgetTargets.pollFirst(),
			startTimestamp, endTimestamp, successful, exception.toString(), resState, deviceLogs, sep)

	constructor(res: ActionResult, prevStateId: ConcreteId, resStateId: ConcreteId, sep: String)
			: this(res.action, res.startTimestamp, res.endTimestamp, res.deviceLogs, res.exception, res.successful, resStateId, sep) {
		prevState = prevStateId
	}

	/** used for ActionQueue entries */
	constructor(action: ExplorationAction, res: ActionResult, prevStateId: ConcreteId, resStateId: ConcreteId, sep: String)
			: this(action.name, if(action.hasWidgetTarget) widgetTargets.pollFirst() else null, res.startTimestamp,
			res.endTimestamp, deviceLogs = res.deviceLogs, exception = res.exception.toString(), successful = res.successful,
			resState = resStateId, sep = sep) {
		prevState = prevStateId
	}

	/** used for ActionQueue sart/end ActionData */
	constructor(actionName:String,res: ActionResult, prevStateId: ConcreteId, resStateId: ConcreteId, sep: String)
			: this(actionName, null, res.startTimestamp,
			res.endTimestamp, deviceLogs = res.deviceLogs, exception = res.exception.toString(), successful = res.successful,
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
		}
	}

	companion object {
//		@JvmStatic operator fun invoke(res:ActionResult, resStateId:ConcreteId, prevStateId: ConcreteId):ActionData =
//				ActionData(res.action,res.startTimestamp,res.endTimestamp,res.deviceLogs,res.screenshot,res.exception,res.successful,resStateId).apply { prevState = prevStateId }

		@JvmStatic
		fun createFromString(e: List<String>, target: Widget?, contentSeparator: String): ActionData = ActionData(
				actionType = e[ActionDataFields.Action.ordinal], targetWidget = target, startTimestamp = LocalDateTime.parse(e[ActionDataFields.StartTime.ordinal]),
				endTimestamp = LocalDateTime.parse(e[ActionDataFields.EndTime.ordinal]), successful = e[ActionDataFields.SuccessFul.ordinal].toBoolean(),
				exception = e[ActionDataFields.Exception.ordinal], resState = idFromString(e[ActionDataFields.DstId.ordinal]), sep = contentSeparator
		).apply { prevState = idFromString(e[ActionDataFields.PrevId.ordinal]) }

		@JvmStatic
		val empty: ActionData by lazy {
			ActionData("EMPTY", null, LocalDateTime.MIN, LocalDateTime.MIN, true, "root action", emptyId, sep = ";"  //FIXME sep should be read from eContext instead
			).apply { prevState = emptyId }
		}

		@JvmStatic
		fun emptyWithWidget(widget: Widget?): ActionData =
			ActionData("EMPTY", widget, LocalDateTime.MIN, LocalDateTime.MIN, true, "root action", emptyId, sep = ";"  //FIXME sep should be read from eContext instead
			).apply { prevState = emptyId }


		@JvmStatic val header:(String)-> String = { sep -> ActionDataFields.values().joinToString(separator = sep) { it.header } }
		@JvmStatic val widgetIdx = ActionDataFields.WId.ordinal
		@JvmStatic val resStateIdx = ActionDataFields.DstId.ordinal
		@JvmStatic val srcStateIdx = ActionDataFields.PrevId.ordinal

		enum class ActionDataFields(var header: String = "") { PrevId("Source State"), Action, WId("Interacted Widget"),
			DstId("Resulting State"), StartTime, EndTime, SuccessFul, Exception;

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

class Trace(private val watcher: MutableList<ModelFeature> = mutableListOf(), private val config: ModelConfig, modelJob: Job, val id:UUID) {
	private val date by lazy { "${timestamp()}_${hashCode()}" }

	private val processorJob = Job(parent = modelJob)
	private val actionProcessorJob = Job(parent = modelJob)
	private val trace = CollectionActor(LinkedList<ActionData>(),"TraceActor").create(actionProcessorJob)
	private val context: CoroutineContext = newCoroutineContext(context = CoroutineName("ActionProcessor"), parent = actionProcessorJob)

	private val targets: MutableList<Widget?> = LinkedList()
	/** used for special id creation of interacted edit fields, map< iEditId -> (state -> collection<widget> )> */
	private val editFields: MutableMap<UUID, LinkedList<Pair<StateData, Widget>>> = mutableMapOf()

	/** this property is set in the end of the trace update and notifies all watchers for changes */
	private val initialState: Triple<StateData, List<Widget>, ExplorationAction> = Triple(StateData.emptyState, emptyList(), EmptyAction)
	private var newState by Delegates.observable(initialState) { _, (srcState,_), (dstState,targets, explorationAction) ->
		notifyObserver(srcState, dstState, targets, explorationAction)
		internalUpdate(srcState = srcState, targets = targets)
	}

	/** observable delegates do not support co-routines within the lambda function therefore this method*/
	private fun notifyObserver(old: StateData, new: StateData, targets: List<Widget>, explorationAction: ExplorationAction) {
		watcher.forEach {
			launch(it.context, parent = it.job) { it.onNewInteracted(id, targets, old, new) }
			val actionIndex = size - 1
			assert(actionIndex >= 0){"ERROR the action-trace size was not properly updated"}
			launch(it.context, parent = it.job) { it.onNewInteracted(id, actionIndex, explorationAction, targets, old, new) }

			val action =
					async(it.context) {
						getAt(actionIndex)!!
					}

			launch(it.context, parent = it.job) {
				it.onNewAction(id, action, old, new)
			}
		}
	}

	/** used to keep track of all widgets interacted with, i.e. the edit fields which require special care in uid computation */
	private fun internalUpdate(srcState: StateData, targets: List<Widget>) {
		this.targets.addAll(targets)
		targets.forEach {
			if (it.isEdit) editFields.compute(srcState.iEditId) { _, stateMap ->
				(stateMap ?: LinkedList()).apply { add(Pair(srcState, it)) }
			}
			Unit
		}
	}

	private val actionProcessor: (ActionResult, StateData, StateData) -> suspend CoroutineScope.() -> Unit = { actionRes, oldState, dstState ->
		{
			if(widgetTargets.isNotEmpty())
				assert(oldState.widgets.containsAll(widgetTargets)) {"ERROR on Trace generation, tried to add action for widgets $widgetTargets which do not exist in the source state $oldState"}

			debugT("create actionData", {
				if(actionRes.action is ActionQueue)
					actionRes.action.actions.map {
						ActionData(it,res = actionRes, prevStateId = oldState.stateId, resStateId = dstState.stateId, sep = config[sep])
					}.also {
						P_addAction(ActionData(ActionQueue.startName,res = actionRes, prevStateId = oldState.stateId, resStateId = dstState.stateId, sep = config[sep]))
						P_addAll(it)
						P_addAction(ActionData(ActionQueue.endName,res = actionRes, prevStateId = oldState.stateId, resStateId = dstState.stateId, sep = config[sep]))
					}
				else ActionData(res = actionRes, prevStateId = oldState.stateId, resStateId = dstState.stateId, sep =config[sep]).also {
					debugT("add action", { P_addAction(it) })
				}
				widgetTargets.clear()
			})
//					.also {
//						assert(it.prevState == oldState.stateId && it.resState == dstState.stateId) {"ERROR ActionData was created wrong $it for $actionRes in $oldState"}
//						assert(it.targetWidget == actionRes.action.widget) {"ERROR in ActionData instantiation wrong targetWidget ${it.targetWidget} instead of ${actionRes.action.widget}"}
//
////						println("DEBUG: $it")
//					}
		}
	}

	/*************** public interface ******************/

	fun update(action: ActionResult, dstState: StateData) {
		size += 1
		lastActionType = action.action::class.simpleName ?: "ERROR"
		// we did not update this.dstState yet, therefore it contains the now 'old' state

		val actionTargets = widgetTargets.toList()  // we may have an action queue and therefore multiple targets in the same state
		this.newState.first.let{ oldState ->
			launch(context, block = actionProcessor(action, oldState, dstState), parent = processorJob)
		}

		debugT("set dstState", { this.newState = Triple(dstState, actionTargets, action.action) })
	}

    fun addWatcher(mf: ModelFeature) = watcher.add(mf)

	/** this function is used by the ModelLoader which creates ActionData objects from dumped data
	 * this function is purposely not called for the whole ActionData set, such that we can issue all watcher updates
	 * if no watchers are registered use [updateAll] instead
	 * ASSUMPTION only one co-routine is simultaneously working on this Trace object*/
	internal suspend fun update(action: ActionData, dstState: StateData) {
		size += 1
		lastActionType = action.actionType
		trace.send(Add(action))
		this.newState = Triple(dstState, widgetTargets, EmptyAction)
	}

	/** this function is used by the ModelLoader which creates ActionData objects from dumped data
	 * to update the whole trace at once
	 * ASSUMPTION no watchers are to be notified
	 */
	internal suspend fun updateAll(actions: List<ActionData>, latestState: StateData){
		size += actions.size
		lastActionType = actions.last().actionType
		trace.send(AddAll(actions))
		if(actions.last().actionType == ActionQueue.name){
			val queueStart = actions.indexOfLast { it.actionType == ActionQueue.startName }
			this.newState = Triple(latestState,
					actions.subList(queueStart,actions.size).mapNotNull { it.targetWidget }, EmptyAction)
		}else this.newState = Triple(latestState, listOfNotNull(actions.last().targetWidget), EmptyAction)
	}

	val currentState get() = newState.first
	var size: Int = 0 // avoid timeout from trace access and just count how many actions were created
	var lastActionType: String = ""

	val interactedEditFields: Map<UUID, List<Pair<StateData, Widget>>> get() = editFields

	fun unexplored(candidates: List<Widget>): List<Widget> = candidates.filterNot { w ->
		targets.any {
			it?.run { w.uid == uid } ?: false
		}
	}

	fun getExploredWidgets(): List<Widget> = targets.filterNotNull()

	/** this directly accesses the [trace] and therefore uses synchronization.
	 * It could be probably optimized with and channel/actor approach instead, if necessary.
	 */
	private fun P_addAction(action:ActionData) = trace.sendBlocking(Add(action))  // this does never actually block the sending since the capacity is unlimited
	private fun P_addAll(actions:List<ActionData>) = trace.sendBlocking(AddAll(actions))  // this does never actually block the sending since the capacity is unlimited

	/** use this function only on the critical execution path otherwise use [P_getActions] instead */
	fun getActions(): List<ActionData> = trace.S_getAll()
	@Suppress("MemberVisibilityCanBePrivate")
	/** use this method within co-routines to make complete use of suspendable feature */
	suspend fun P_getActions(): List<ActionData>{
		processorJob.joinChildren() // ensure the last action was already added
		return trace.getAll()
	}

	suspend fun last(): ActionData? {
		processorJob.joinChildren() // ensure the last action was already added
		return trace.getOrNull { it.lastOrNull() }
	}

	/** get the element at index [i] if it exists and null otherwise */
	suspend fun getAt(i:Int): ActionData?{
		processorJob.joinChildren() // ensure the last action was already added
		return trace.getOrNull { (it as LinkedList<ActionData>).let{ list ->
			if(list.indices.contains(i))
				list[i]
			else {
				println("Index: $i \t Size: ${list.size}")
				throw RuntimeException("Here!!!")
				// null
			}
		} }
	}

	/** this has to access a co-routine actor prefer using [size] if synchronization is not critical */
	suspend fun isEmpty(): Boolean{
		processorJob.joinChildren() // ensure the last action was already added
		return trace.get { it.isEmpty() }
	}
	/** this has to access a co-routine actor prefer using [size] if synchronization is not critical */
	suspend fun isNotEmpty(): Boolean{
		processorJob.joinChildren() // ensure the last action was already added
		return trace.get { it.isNotEmpty() }
	}

	/** this process is not waiting for the currently processed action, therefore this method should be only
	 * used if at least 2 actions were already executed. Otherwise you should prefer 'getAt(0)'
	 */
	fun first(): ActionData = runBlocking { trace.getOrNull { it.first() } ?: ActionData.empty }

	//FIXME ensure that the latest dump is not overwritten due to scheduling issues, for example by using a nice buffered channel only keeping the last value offer
	suspend fun dump(config: ModelConfig = this.config) = dumpMutex.withLock {
		File(config.traceFile(id.toString())).bufferedWriter().use { out ->
			out.write(ActionData.header(config[sep]))
			out.newLine()
			// ensure that our trace is complete before dumping it by calling blocking getActions
			P_getActions().forEach { action ->
				out.write(action.actionString())
				out.newLine()
			}
		}
	}

	companion object {
		@JvmStatic
		private val dumpMutex = Mutex()
	}

	override fun equals(other: Any?): Boolean {
		return(other as? Trace)?.let {
			val t = other.getActions()
			getActions().foldIndexed(true) { i, res, a -> res && a == t[i] }
		} ?: false
	}

	override fun hashCode(): Int {
		return trace.hashCode()
	}

}

