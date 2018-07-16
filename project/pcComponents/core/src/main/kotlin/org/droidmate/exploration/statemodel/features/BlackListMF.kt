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

package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Deferred
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
	override suspend fun onNewAction(deferredAction: Deferred<ActionData>, prevState: StateData, newState: StateData) {
		if(prevState.isHomeScreen || !isStuck(deferredAction.await().actionType)) this.lastActionableState = prevState
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