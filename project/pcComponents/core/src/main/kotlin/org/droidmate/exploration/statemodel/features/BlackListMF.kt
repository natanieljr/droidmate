package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.joinChildren
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.PressBackExplorationAction
import org.droidmate.exploration.actions.ResetAppExplorationAction
import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.statemodel.StateData
import java.io.File
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext

class BlackListMF: ModelFeature() {
	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("BlackListMF"), parent = job)

	// records how often a specific widget was selected and from which state-context (widget.uid -> Map<state.uid -> numActions>)
	private val wCnt = mutableMapOf<UUID, MutableMap<UUID, Int>>()
	private var lastActionableState: StateData = StateData.emptyState

	/** used to keep track of the last state before we got stuck */
	override suspend fun onNewAction(lazyAction: Lazy<ActionData>, prevState: StateData, newState: StateData) {
		if(prevState.isHomeScreen || !isStuck(lazyAction.value.actionType)) this.lastActionableState = prevState
	}

	private fun isStuck(actionType: String): Boolean =	when(actionType){  // ignore initial reset
		ResetAppExplorationAction::class.simpleName, PressBackExplorationAction::class.simpleName -> true
		else -> false
	}

	/**
	 * on each Reset or Press-Back which was not issued from the HomeScreen we can assume, that our Exploration got stuck
	 * and blacklist the widget of the action before this one to be not/ less likely explored
	 */
	override suspend fun onContextUpdate(context: ExplorationContext) {
		if(!lastActionableState.isHomeScreen && context.lastTarget != null && isStuck(context.getLastActionType()))
			wCnt.compute( context.lastTarget!!.uid,
					{ _, m -> m?.incCnt(lastActionableState.uid) ?: mutableMapOf(lastActionableState.uid to 1) })
	}

	suspend fun decreaseCounter(context: ExplorationContext){
		context.lastTarget?.let { lastTarget ->
			job.joinChildren() // wait that all updates are applied before changing the counter value
			wCnt.compute(lastTarget.uid,
			{ _,m -> m?.decCnt(lastActionableState.uid) ?: mutableMapOf(lastActionableState.uid to 0)})
		}
	}

	fun isBlacklisted(wId: UUID, threshold: Int = 1): Boolean = wCnt.sumCounter(wId) >= threshold

	fun isBlacklistedInState(wId: UUID, sId:UUID, threshold: Int = 1): Boolean = wCnt.getCounter(wId, sId) >= threshold

	override suspend fun dump(context: ExplorationContext) {
		job.joinChildren()  // wait until all other coroutines of this feature are completed
		File(context.getModel().config.baseDir.toString() + "${File.separator}lastBlacklist.txt").bufferedWriter().use { out ->
			out.write(header)
			wCnt.forEach { wMap ->	wMap.value.entries.forEach { (sId, cnt) ->
					out.newLine()
					out.write("${wMap.key} ;\t$sId ;\t$cnt")
			}}
		}
	}

	companion object {
		val header = "WidgetId".padEnd(38)+"; State-Context".padEnd(38)+"; # listed"

	}
}