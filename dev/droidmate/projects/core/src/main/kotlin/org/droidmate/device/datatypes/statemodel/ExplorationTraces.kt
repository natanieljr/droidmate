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

import kotlinx.coroutines.experimental.sync.Mutex
import kotlinx.coroutines.experimental.sync.withLock
import org.droidmate.android_sdk.DeviceException
import org.droidmate.device.datatypes.Widget
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.WidgetExplorationAction
import org.droidmate.exploration.device.IDeviceLogs
import org.droidmate.exploration.device.MissingDeviceLogs
import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class ActionData private constructor(val actionType:String?,val targetWidget: Widget?,
                                     val startTimestamp: LocalDateTime, val endTimestamp: LocalDateTime, val screenshot: URI?,
                                     val successful: Boolean, val exception: String,
                                     val resState:StateId, val deviceLogs: IDeviceLogs = MissingDeviceLogs()){

	constructor(action:ExplorationAction, startTimestamp: LocalDateTime, endTimestamp: LocalDateTime,
	            deviceLogs: IDeviceLogs, screenshot: URI?, exception: DeviceException, successful: Boolean, resState:StateId)
			:this(action::class.simpleName,(action as? WidgetExplorationAction)?.widget,
			startTimestamp, endTimestamp, screenshot,	successful, exception.toString(), resState,	deviceLogs)

	lateinit var prevState:StateId

	/**
	 * Time the strategy pool took to select a strategy and a create an action
	 * (used to measure overhead for new exploration strategies)
	 */
	val decisionTime: Long
		get() = ChronoUnit.MILLIS.between(startTimestamp, endTimestamp)

	fun actionString():String = P.values().joinToString(separator = sep) { when (it){
		P.Action -> actionType?: ""
		P.StartTime -> startTimestamp.toString()
		P.EndTime -> endTimestamp.toString()
		P.Exception -> exception
		P.SuccessFul -> successful.toString()
		P.DstId -> resState.toString()
		P.Id -> prevState.toString()
		P.WId -> targetWidget?.uid.toString()
	}}

	companion object {
		operator fun invoke(res:ActionResult,resStateId:StateId,prevStateId: StateId):ActionData =
				ActionData(res.action,res.startTimestamp,res.endTimestamp,res.deviceLogs,res.screenshot,res.exception,res.successful,resStateId).apply { prevState = prevStateId }

		fun createFromString(e:List<String>, target: Widget?):ActionData = ActionData(
				e[P.Action.ordinal], target, LocalDateTime.parse(e[P.StartTime.ordinal]), LocalDateTime.parse(e[P.EndTime.ordinal]),
				null, e[P.SuccessFul.ordinal].toBoolean(), e[P.Exception.ordinal], stateIdFromString(e[P.DstId.ordinal])
		).apply{ prevState = stateIdFromString(e[P.Id.ordinal])}

		fun empty(): ActionData = ActionData(null,null, LocalDateTime.MIN, LocalDateTime.MIN, null, true, "empty action", emptyId
				).apply{ prevState = emptyId}

		val header = P.values().joinToString(separator=sep) { it.header }
		val widgetIdx = P.WId.ordinal

		private enum class P(var header:String="") { Id("Source State"),Action,WId("Interacted Widget"),
			DstId("Resulting State"),StartTime,EndTime,SuccessFul,Exception;
			init{ if(header=="") header=name }
		}
	}
}

class Trace{
  private val trace = LinkedList<ActionData>()
  private val date by lazy{ "${timestamp()}_${hashCode()}"}
	val size: Int get() = trace.size

	fun addAction(action:ActionData) = synchronized(trace){ trace.add(action) }
  fun last():ActionData? = trace.lastOrNull()
  fun getActions():List<ActionData> = trace

  suspend fun dump(config: ModelDumpConfig) = dumpMutex.withLock("trace dump"){
    File(config.traceFile(date)).bufferedWriter().use{ out ->
      out.write(ActionData.header)
      out.newLine()
      trace.forEach { action ->
        out.write(action.actionString())
        out.newLine()
      }
    }
  }

  companion object {
    private val dumpMutex = Mutex()
  }

	override fun equals(other: Any?): Boolean {
    return (other as? Trace)?.let{ val t = other.trace
      trace.foldIndexed( true, {i,res,a -> res && a == t[i] })
    } ?: false
  }

  override fun hashCode(): Int {
    return trace.hashCode()
  }

  fun isEmpty(): Boolean = trace.isEmpty()
	fun isNotEmpty(): Boolean = trace.isNotEmpty()
	fun first(): ActionData = trace.first

}

