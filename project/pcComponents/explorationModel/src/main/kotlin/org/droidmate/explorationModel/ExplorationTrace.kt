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

@file:Suppress("FunctionName")

package org.droidmate.explorationModel

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.explorationModel.config.*
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.dump.sep
import org.droidmate.explorationModel.interaction.*
import org.droidmate.explorationModel.retention.StringCreator
import org.droidmate.explorationModel.retention.StringCreator.createActionString
import java.io.File
import java.util.*
import kotlin.properties.Delegates

@Suppress("MemberVisibilityCanBePrivate")
open class ExplorationTrace(private val watcher: MutableList<ModelFeatureI> = mutableListOf(), private val config: ModelConfig, val id: UUID) {
	protected val dumpMutex = Mutex() // for synchronization of (trace-)file access
	init{ 	widgetTargets.clear() // ensure that this list is cleared even if we had an exception on previous apk exploration
	}

	protected val trace = CollectionActor(LinkedList<Interaction>(), "TraceActor").create()

	private val targets: MutableList<Widget> = LinkedList()

	data class RecentState(val state: State, val interactionTargets: List<Widget>, val action: ExplorationAction, val interactions: List<Interaction>)
	/** this property is set in the end of the trace update and notifies all watchers for changes */
	protected var mostRecentState: RecentState
			by Delegates.observable(RecentState(State.emptyState, emptyList(), EmptyAction, emptyList())) { _, last, recent ->
				notifyObserver(old = last.state, new = recent.state, targets = recent.interactionTargets,
						explorationAction = recent.action, interactions = recent.interactions)
				internalUpdate(srcState = last.state, interactedTargets = recent.interactionTargets)
			}
		private set


	/** observable delegates do not support co-routines within the lambda function therefore this method*/
	protected open fun notifyObserver(old: State, new: State, targets: List<Widget>, explorationAction: ExplorationAction, interactions: List<Interaction>) {
		watcher.forEach {
			it.launch { it.onNewInteracted(id, targets, old, new) }
			val actionIndex = size - 1
			assert(actionIndex >= 0){"ERROR the action-trace size was not properly updated"}
			it.launch { it.onNewInteracted(id, actionIndex, explorationAction, targets, old, new) }

			it.launch {	it.onNewAction(id, interactions, old, new)	}
		}
	}

	/** used to keep track of all widgets interacted with, i.e. the edit fields which require special care in uid computation */
	protected open fun internalUpdate(srcState: State, interactedTargets: List<Widget>) {
		this.targets.addAll(interactedTargets)
	}

	private fun actionProcessor(actionRes: ActionResult, oldState: State, dstState: State): List<Interaction> = LinkedList<Interaction>().apply{
		if(widgetTargets.isNotEmpty())
			assert(oldState.widgets.containsAll(widgetTargets)) {"ERROR on ExplorationTrace generation, tried to add action for widgets $widgetTargets which do not exist in the source state $oldState"}

		if(actionRes.action is ActionQueue)
			actionRes.action.actions.map {
				Interaction(it, res = actionRes, prevStateId = oldState.stateId, resStateId = dstState.stateId,
						target = if(actionRes.action.hasWidgetTarget) widgetTargets.pollFirst() else null)
			}.also {
				add(Interaction(ActionQueue.startName, res = actionRes, prevStateId = oldState.stateId, resStateId = dstState.stateId))
				addAll(it)
				add(Interaction(ActionQueue.endName, res = actionRes, prevStateId = oldState.stateId, resStateId = dstState.stateId))
			}
		else
			add( Interaction(res = actionRes, prevStateId = oldState.stateId, resStateId = dstState.stateId,
					target = if(actionRes.action.hasWidgetTarget) widgetTargets.pollFirst() else null) )
	}.also { interactions ->
		widgetTargets.clear()
		P_addAll(interactions)
	}

	/*************** public interface ******************/

	fun update(action: ActionResult, dstState: State) {
		size += 1
		lastActionType = if(action.action is ActionQueue) action.action.actions.lastOrNull()?.name ?:"empty queue"  else action.action.name
		// we did not update this.dstState yet, therefore it contains the now 'old' state

		val actionTargets = widgetTargets.toList()  // we may have an action queue and therefore multiple targets in the same state
		val interactions = debugT("action processing", {actionProcessor(action, this.mostRecentState.state, dstState)},inMillis = true)

		debugT("set dstState", { this.mostRecentState = ExplorationTrace.RecentState(dstState, actionTargets, action.action, interactions) })
	}

    fun addWatcher(mf: ModelFeatureI) = watcher.add(mf)

	/** this function is used by the ModelLoader which creates Interaction objects from dumped data
	 * this function is purposely not called for the whole Interaction set, such that we can issue all watcher updates
	 * if no watchers are registered use [updateAll] instead
	 * ASSUMPTION only one co-routine is simultaneously working on this ExplorationTrace object*/
	internal suspend fun update(action: Interaction, dstState: State) {
		size += 1
		lastActionType = action.actionType
		trace.send(Add(action))
		this.mostRecentState = RecentState(dstState, widgetTargets, EmptyAction, listOf(action))
	}

	/** this function is used by the ModelLoader which creates Interaction objects from dumped data
	 * to update the whole trace at once
	 * ASSUMPTION no watchers are to be notified
	 */
	internal suspend fun updateAll(actions: List<Interaction>, latestState: State){
		size += actions.size
		lastActionType = actions.last().actionType
		trace.send(AddAll(actions))
		if(actions.last().actionType.isQueueEnd()){
			val queueStart = actions.indexOfLast { it.actionType.isQueueStart() }
			val interactions = actions.subList(queueStart,actions.size)
			this.mostRecentState = RecentState(latestState, interactions.mapNotNull { it.targetWidget }, EmptyAction, interactions)
		}else this.mostRecentState = RecentState(latestState, listOfNotNull(actions.last().targetWidget), EmptyAction, listOf(actions.last()))
	}

	val currentState get() = mostRecentState.state
	var size: Int = 0 // avoid timeout from trace access and just count how many actions were created
	var lastActionType: String = ""

	@Deprecated("to be removed, instead have a list of all unexplored widgets and remove the ones chosen as target -> best done as ModelFeature")
	fun unexplored(candidates: List<Widget>): List<Widget> = candidates.filterNot { w ->
		targets.any { w.uid == it.uid	}
	}

	fun getExploredWidgets(): List<Widget> = targets

	private fun P_addAll(actions:List<Interaction>) = trace.sendBlocking(AddAll(actions))  // this does never actually block the sending since the capacity is unlimited

	/** use this function only on the critical execution path otherwise use [P_getActions] instead */
	fun getActions(): List<Interaction> 	//FIXME the runBlocking should be replaced with non-thread blocking coroutineScope
			= runBlocking{coroutineScope<List<Interaction>> {	// -> requires suspend propagation in selectors which are lambda values
		return@coroutineScope trace.S_getAll()
	} }
	@Suppress("MemberVisibilityCanBePrivate")
	/** use this method within co-routines to make complete use of suspendable feature */
	suspend fun P_getActions(): List<Interaction>{
		return trace.getAll()
	}

	suspend fun last(): Interaction? {
		return trace.getOrNull { it.lastOrNull() }
	}

	/** this has to access a co-routine actor prefer using [size] if synchronization is not critical */
	suspend fun isEmpty(): Boolean{
		return trace.get { it.isEmpty() }
	}
	/** this has to access a co-routine actor prefer using [size] if synchronization is not critical */
	suspend fun isNotEmpty(): Boolean{
		return trace.get { it.isNotEmpty() }
	}

	/** this process is not waiting for the currently processed action, therefore this method should be only
	 * used if at least 2 actions were already executed. Otherwise you should prefer 'getAt(0)'
	 */
	suspend fun first(): Interaction = trace.getOrNull { it.first() } ?: Interaction.empty

	//TODO ensure that the latest dump is not overwritten due to scheduling issues, for example by using a nice buffered channel only keeping the last value offer
	open suspend fun dump(config: ModelConfig = this.config) = dumpMutex.withLock {
		File(config.traceFile(id.toString())).bufferedWriter().use { out ->
			out.write(StringCreator.actionHeader(config[sep]))
			out.newLine()
			// ensure that our trace is complete before dumping it by calling blocking getActions
			P_getActions().forEach { action ->
				out.write(createActionString(action,config[sep]))
				out.newLine()
			}
		}
	}

	override fun equals(other: Any?): Boolean {
		return(other as? ExplorationTrace)?.let {
			val t = other.getActions()
			getActions().foldIndexed(true) { i, res, a -> res && a == t[i] }
		} ?: false
	}

	override fun hashCode(): Int {
		return trace.hashCode()
	}

	companion object {
		val widgetTargets = LinkedList<Widget>()
	}

}

