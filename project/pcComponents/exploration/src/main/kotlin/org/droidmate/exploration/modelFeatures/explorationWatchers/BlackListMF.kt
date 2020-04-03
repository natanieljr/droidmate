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

package org.droidmate.exploration.modelFeatures.explorationWatchers

import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineName
import org.droidmate.exploration.ExplorationContext
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State

class BlackListMF: WidgetCountingMF() {
	override val coroutineContext: CoroutineContext = CoroutineName("BlackListMF")

	/**
	 * Blacklist items which lead to a crash.
	 * Items that lead outside of the app or let the exploration stuck on a screen are not a problem.
	 */
	override suspend fun onNewAction(traceId: UUID, interactions: List<Interaction<*>>, prevState: State<*>, newState: State<*>) {
		val target = interactions
			.firstOrNull { it.targetWidget != null }
			?.targetWidget
		if (target != null && newState.isAppHasStoppedDialogBox) {
			incCnt(target.uid, prevState.uid)
		}
	}

	override suspend fun onAppExplorationFinished(context: ExplorationContext<*, *, *>) {
		dump(context.model.config.baseDir.resolve("lastBlacklist.txt"))
	}
}