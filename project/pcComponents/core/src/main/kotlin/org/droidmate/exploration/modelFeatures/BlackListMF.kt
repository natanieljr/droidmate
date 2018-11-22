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

package org.droidmate.exploration.modelFeatures

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Deferred
import org.droidmate.deviceInterface.exploration.ActionType
import org.droidmate.deviceInterface.exploration.LaunchApp
import org.droidmate.exploration.ExplorationContext
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import java.util.*
import kotlin.coroutines.CoroutineContext

class BlackListMF: WidgetCountingMF() {
	override val coroutineContext: CoroutineContext = CoroutineName("BlackListMF")

	private var lastActionableState: State = State.emptyState

	/** used to keep track of the last state before we got stuck */
	override suspend fun onNewAction(traceId: UUID, deferredAction: Deferred<Interaction>, prevState: State, newState: State) {
		if(prevState.isHomeScreen || !isStuck(deferredAction.await().actionType)) this.lastActionableState = prevState
	}

	@Suppress("DEPRECATION")
	private fun isStuck(actionType: String): Boolean =	when(actionType){  // ignore initial reset
		LaunchApp.name, ActionType.PressBack.name -> true
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


	override suspend fun onAppExplorationFinished(context: ExplorationContext) {
		dump(context.getModel().config.baseDir.resolve("lastBlacklist.txt"))
	}
}