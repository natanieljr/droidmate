package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.PressBackExplorationAction
import org.droidmate.exploration.actions.ResetAppExplorationAction
import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.statemodel.StateData
import java.io.File
import kotlin.coroutines.experimental.CoroutineContext

class BlackListMF: WidgetCountingMF() {
	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("BlackListMF"), parent = job)

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
			incCnt(context.lastTarget!!.uid, lastActionableState.uid)
	}

	fun decreaseCounter(context: ExplorationContext){
		context.lastTarget?.let { lastTarget ->
			decCnt(lastTarget.uid, lastActionableState.uid)
		}
	}

	override suspend fun dump(context: ExplorationContext) {
		dump(File(context.getModel().config.baseDir.toString() + "${File.separator}lastBlacklist.txt"))
	}
}