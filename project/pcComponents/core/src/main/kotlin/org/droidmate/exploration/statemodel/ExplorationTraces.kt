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
import org.droidmate.exploration.actions.AbstractExplorationAction
import org.droidmate.exploration.statemodel.features.ModelFeature
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.properties.Delegates

open class ActionData protected constructor(val actionType: String, val targetWidget: Widget?,
                                            val startTimestamp: LocalDateTime, val endTimestamp: LocalDateTime,
                                            val successful: Boolean, val exception: String,
                                            val resState: ConcreteId, val deviceLogs: IDeviceLogs = MissingDeviceLogs,
                                            private val sep:String) {

	constructor(action: AbstractExplorationAction, startTimestamp: LocalDateTime, endTimestamp: LocalDateTime,
	            deviceLogs: IDeviceLogs, exception: DeviceException, successful: Boolean, resState: ConcreteId, sep:String)
			: this(action.actionName, action.widget,
			startTimestamp, endTimestamp, successful, exception.toString(), resState, deviceLogs, sep)

	constructor(res: ActionResult, prevStateId: ConcreteId, resStateId: ConcreteId, sep: String)
			: this(res.action, res.startTimestamp, res.endTimestamp, res.deviceLogs, res.exception, res.successful, resStateId, sep) {
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

class Trace(private val watcher: List<ModelFeature> = emptyList(), private val config: ModelConfig, modelJob: Job) {
	private val date by lazy { "${timestamp()}_${hashCode()}" }

	private val actionProcessorJob = Job(parent = modelJob)
	private val trace = CollectionActor(LinkedList<ActionData>(),"TraceActor").create(actionProcessorJob)
	private val context: CoroutineContext = newCoroutineContext(context = CoroutineName("ActionProcessor"), parent = actionProcessorJob)

	private val targets: MutableList<Widget?> = LinkedList()
	/** used for special id creation of interacted edit fields, map< iEditId -> (state -> collection<widget> )> */
	private val editFields: MutableMap<UUID, LinkedList<Pair<StateData, Widget>>> = mutableMapOf()

	/** this property is set in the end of the trace update and notifies all watchers for changes */
	private val initialState: Pair<StateData, Widget?> = Pair(StateData.emptyState, null)
	private var newState by Delegates.observable(initialState) { _, (srcState,_), (dstState,target) ->
		notifyObserver(srcState, dstState, target)
		internalUpdate(srcState = srcState, target = target)
	}

	/** observable delegates do not support coroutines within the lambda function therefore this method*/
	private fun notifyObserver(old: StateData, new: StateData, target: Widget?) {
		watcher.forEach {
			launch(it.context, parent = it.job) { it.onNewInteracted(target, old, new) }
			size.let{ i -> launch(it.context, parent = it.job) {
				it.onNewAction(lazy {
					runBlocking(it.context){
						getAt(i-1)!! }
				}, old, new) } } //FIXME sometimes throwing null pointer ?! maybe use defered value instead?
		}
	}

	/** used to keep track of all widgets interacted with, i.e. the edit fields which require special care in uid computation */
	private fun internalUpdate(srcState: StateData, target: Widget?) {
		targets.add(target)
		target?.run {
			if (isEdit) editFields.compute(srcState.iEditId) { _, stateMap ->
				(stateMap ?: LinkedList()).apply { add(Pair(srcState, target)) }
			}
		}
	}

	private val actionProcessor: (ActionResult, StateData, StateData) -> suspend CoroutineScope.() -> Unit = { action, oldState, dstState ->
		{
			if(action.action.widget != null ) assert(oldState.widgets.contains(action.action.widget!!),{"ERROR on Trace generation, tried to add action for widget which does not exist in the source state $oldState"})
			debugT("create actionData", { ActionData(res = action, prevStateId = oldState.stateId, resStateId = dstState.stateId, sep =config[sep]) })
					.also {
						assert(it.prevState == oldState.stateId && it.resState == dstState.stateId, {"ERROR ActionData was created wrong $it for $action in $oldState"})
						assert(it.targetWidget == action.action.widget, {"ERROR in ActionData instanciation wrong targetWidget ${it.targetWidget} instead of ${action.action.widget}"})
						debugT("add action", { P_addAction(it) })
//						println("DEBUG: $it")
					}
		}
	}

	/*************** public interface ******************/

	fun update(action: ActionResult, dstState: StateData) {
		size += 1
		lastActionType = action.action::class.simpleName ?: "ERROR"
		// we did not update this.dstState yet, therefore it contains the now 'old' state

		this.newState.first.let{ oldState ->
			if(action.action.widget != null ) assert(oldState.widgets.contains(action.action.widget!!),{"ERROR on Trace generation, tried to add action for widget ${action.action.widget!!.id} which does not exist in the source state $oldState"})
			launch(context, block = actionProcessor(action, oldState, dstState))
		}

		debugT("set dstState", { this.newState = Pair(dstState, action.action.widget) })
	}

	/** this function is used by the ModelLoader which creates ActionData objects from dumped data
	 * this function is purposely not called for the whole ActionData set, such that we can issue all watcher updates
	 * if no watchers are registered use [updateAll] instead
	 * ASSUMPTION only one coroutine is simultaneously working on this Trace object*/
	internal suspend fun update(action: ActionData, dstState: StateData) {
		size += 1
		lastActionType = action.actionType
		trace.send(Add(action))
		this.newState = Pair(dstState, action.targetWidget)
	}

	/** this function is used by the ModelLoader which creates ActionData objects from dumped data
	 * to update the whole trace at once
	 * ASSUMPTION no watchers are to be notified
	 */
	internal suspend fun updateAll(actions: Collection<ActionData>, latestState: StateData){
		size += actions.size
		lastActionType = actions.last().actionType
		trace.send(AddAll(actions))
		this.newState = Pair(latestState, actions.last().targetWidget)
	}

	val currentState get() = newState.first
	var size: Int = 0 // avoid delay from trace access and just count how many actions were created
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

	/** use this function only on the critical execution path otherwise use [P_getActions] instead */
	fun getActions(): List<ActionData> = trace.S_getAll()
	@Suppress("MemberVisibilityCanBePrivate")
	/** use this method within coroutines to make complete use of suspendable feature */
	suspend fun P_getActions(): List<ActionData>   = trace.getAll()

	suspend fun last(): ActionData? = trace.getOrNull { it.lastOrNull() }

	/** get the element at index [i] if it exists and null otherwise */
	suspend fun getAt(i:Int): ActionData? = trace.getOrNull { (it as LinkedList<ActionData>).let{ list ->
		if(list.indices.contains(i))
			list[i]
		else null
	} }

	/** this has to acsess a couroutine actor prefer using [size] if synchronization is not critical */
	suspend fun isEmpty(): Boolean = trace.get { it.isEmpty() }
	/** this has to acsess a couroutine actor prefer using [size] if synchronization is not critical */
	suspend fun isNotEmpty(): Boolean = trace.get { it.isNotEmpty() }
	fun first(): ActionData = runBlocking { trace.getOrNull { it.first() } ?: ActionData.empty }

	//FIXME ensure that the latest dump is not overwritten due to scheduling issues, for example by using a nice buffered channel only keeping the last value offer
	suspend fun dump(config: ModelConfig = this.config) = dumpMutex.withLock {
		File(config.traceFile(date)).bufferedWriter().use { out ->
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
			getActions().foldIndexed(true, { i, res, a -> res && a == t[i] })
		} ?: false
	}

	override fun hashCode(): Int {
		return trace.hashCode()
	}

}

