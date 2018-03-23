// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Jenny Hotzkow
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
package org.droidmate.device.datatypes.statemodel

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.sync.Mutex
import kotlinx.coroutines.experimental.sync.withLock
import org.droidmate.android_sdk.DeviceException
import org.droidmate.debug.debugT
import org.droidmate.device.datatypes.statemodel.features.ModelFeature
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.device.IDeviceLogs
import org.droidmate.exploration.device.MissingDeviceLogs
import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.properties.Delegates

class ActionData private constructor(val actionType:String, val targetWidget: Widget?,
                                     val startTimestamp: LocalDateTime, val endTimestamp: LocalDateTime, val screenshot: URI?,
                                     val successful: Boolean, val exception: String,
                                     val resState:ConcreteId, val deviceLogs: IDeviceLogs = MissingDeviceLogs){

	constructor(action:ExplorationAction, startTimestamp: LocalDateTime, endTimestamp: LocalDateTime,
	            deviceLogs: IDeviceLogs, screenshot: URI?, exception: DeviceException, successful: Boolean, resState:ConcreteId)
			:this(action::class.simpleName?:"Unknown", action.widget,
			startTimestamp, endTimestamp, screenshot,	successful, exception.toString(), resState,	deviceLogs)

	constructor(res:ActionResult, resStateId:ConcreteId, prevStateId: ConcreteId): this(res.action,res.startTimestamp,res.endTimestamp,res.deviceLogs,res.screenshot,res.exception,res.successful,resStateId){ prevState = prevStateId }

	lateinit var prevState:ConcreteId

	/**
	 * Time the strategy pool took to select a strategy and a create an action
	 * (used to measure overhead for new exploration strategies)
	 */
	val decisionTime: Long by lazy{ ChronoUnit.MILLIS.between(startTimestamp, endTimestamp) }

	fun actionString():String = P.values().joinToString(separator = sep) { when (it){
		P.Action -> actionType
		P.StartTime -> startTimestamp.toString()
		P.EndTime -> endTimestamp.toString()
		P.Exception -> exception
		P.SuccessFul -> successful.toString()
		P.DstId -> resState.toString()
		P.Id -> prevState.toString()
		P.WId -> targetWidget?.uid.toString()
	}}

	companion object {
//		@JvmStatic operator fun invoke(res:ActionResult, resStateId:ConcreteId, prevStateId: ConcreteId):ActionData =
//				ActionData(res.action,res.startTimestamp,res.endTimestamp,res.deviceLogs,res.screenshot,res.exception,res.successful,resStateId).apply { prevState = prevStateId }

		@JvmStatic fun createFromString(e:List<String>, target: Widget?):ActionData = ActionData(
				e[P.Action.ordinal], target, LocalDateTime.parse(e[P.StartTime.ordinal]), LocalDateTime.parse(e[P.EndTime.ordinal]),
				null, e[P.SuccessFul.ordinal].toBoolean(), e[P.Exception.ordinal], stateIdFromString(e[P.DstId.ordinal])
		).apply{ prevState = stateIdFromString(e[P.Id.ordinal])}

		@JvmStatic val empty: ActionData by lazy{ ActionData("EMPTY",null, LocalDateTime.MIN, LocalDateTime.MIN, null, true, "empty action", emptyId
				).apply{ prevState = emptyId} }

		@JvmStatic val header = P.values().joinToString(separator=sep) { it.header }
		val widgetIdx = P.WId.ordinal

		private enum class P(var header:String="") { Id("Source State"),Action,WId("Interacted Widget"),
			DstId("Resulting State"),StartTime,EndTime,SuccessFul,Exception;
			init{ if(header=="") header=name }
		}
	}

	override fun toString(): String {
		return "$actionType:$targetWidget"
	}
}

class Trace(private val watcher:List<ModelFeature> = emptyList()){
	/** REMARK: the completion of action processing has to be ensured by calling actionProcessorJob.joinChildren() before accessing this property */
	private val trace = LinkedList<ActionData>()

	private val actionProcessorJob = Job()
	private fun<T> waitFor(block:()->T):T = runBlocking { actionProcessorJob.joinChildren(); block() }
	private val context: CoroutineContext = newCoroutineContext(context = CoroutineName("ActionProcessor"), parent = actionProcessorJob)

  private val date by lazy{ "${timestamp()}_${hashCode()}"}

	private val targets:MutableList<Widget?> = LinkedList()
	/** used for special id creation of interacted edit fields, map< iEditId -> (state -> collection<widget> )> */
	private val editFields:MutableMap<UUID,LinkedList<Pair<StateData, Widget>>> = mutableMapOf()

	/** this property is set in the end of the trace update and notifies all watchers for changes */
	private val initialState:Pair<StateData,Widget?> = Pair(StateData.emptyState, null)
	private var newState by Delegates.observable(initialState) { _, old, new ->
		notifyObserver(old.first, new.first, new.second)
		internalUpdate(new.second, old.first)
	}

	/** observable delegates do not support coroutines within the lambda function therefore this method*/
	private fun notifyObserver(old:StateData, new:StateData, target: Widget?){
		watcher.forEach {
			launch(it.context, parent = it.job){ it.onNewInteracted(target,old,new) }
			launch(it.context, parent = it.job){ it.onNewAction(lazy{last()!!},old,new) }
		}
	}

	/** used to keep track of all widgets interacted with, i.e. the edit fields which require special care in uid computation */
	private fun internalUpdate(target: Widget?, state:StateData){
		targets.add(target)
		target?.run {
			if (isEdit) editFields.compute(state.iEditId, { _,stateMap ->
				(stateMap ?: LinkedList()).apply {add(Pair(state, target)) }
			})
		}
	}

	private val actionProcessor: (ActionResult,StateData)->suspend CoroutineScope.()->Unit = { action,newState ->
		{
			debugT("create actionData", { ActionData(action, newState.stateId, newState.stateId) })
					.also {
						debugT("add action", { trace.add(it) })
					}
		}
	}

	/*************** public interface ******************/

	fun update(action:ActionResult,newState:StateData) {
		launch(context, parent = actionProcessorJob, block = actionProcessor(action,newState))

		debugT("set newState",{this.newState = Pair(newState, action.action.widget)})
	}

	val currentState get() = newState.first
	val size: Int get() = waitFor { trace.size }

	val interactedEditFields: Map<UUID, List<Pair<StateData, Widget>>> get() = editFields

	fun unexplored(candidates:List<Widget>):List<Widget> = candidates.filterNot { w -> targets.any{ it?.run { w.uid == uid }?:false } }

	/** this directly accesses the [trace] without any synchronization.
	 * the caller should ensure that this method is only called in sequential/synchronized context
	 */
	internal fun S_addAction(action:ActionData) = trace.add(action)

	fun getActions():List<ActionData> = waitFor{ trace }
  fun last():ActionData? = waitFor { trace.lastOrNull() }
	fun isEmpty(): Boolean = waitFor { trace.isEmpty() }
	fun isNotEmpty(): Boolean = waitFor { trace.isNotEmpty() }
	fun first(): ActionData{
		return if(trace.isNotEmpty()) trace.first
			else waitFor { trace.first }
	}

	//TODO this can be nicely done with a buffered channel instead of requiring a explicit launch call each time
  suspend fun dump(config: ModelDumpConfig) = dumpMutex.withLock("trace dump"){
    File(config.traceFile(date)).bufferedWriter().use{ out ->
      out.write(ActionData.header)
      out.newLine()
	    waitFor {  }  // ensure that our trace is complete before dumping it
      trace.forEach { action ->
        out.write(action.actionString())
        out.newLine()
      }
    }
  }

  companion object {
    @JvmStatic private val dumpMutex = Mutex()
  }

	override fun equals(other: Any?): Boolean {
    return (other as? Trace)?.let{ val t = other.trace
      trace.foldIndexed( true, {i,res,a -> res && a == t[i] })
    } ?: false
  }

  override fun hashCode(): Int {
    return trace.hashCode()
  }

}

