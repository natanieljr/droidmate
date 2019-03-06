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

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.ModelFeature
import org.droidmate.explorationModel.Model
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext

/**
 * This model is used by the playback class to identify actions which could not be replayed.
 * This information is the used for dumping/reporting
 */
class ActionPlaybackFeature(val storedModel: Model,
							val skippedActions: MutableSet<Pair<Int,Int>> = HashSet()) : ModelFeature() {

	override val coroutineContext: CoroutineContext = CoroutineName("EventProbabilityMF") + Job()
	fun addNonReplayableActions(traceIdx: Int, actionIdx: Int){
		skippedActions.add(Pair(traceIdx, actionIdx))
	}

	override suspend fun onAppExplorationFinished(context: ExplorationContext) {
		this.join()

		val sb = StringBuilder()
		sb.appendln(header)

		skippedActions.forEach {
			val trace = it.first
			val action = it.second
			sb.appendln("$trace;$action")
		}

		val outputFile = context.getModel().config.baseDir.resolve("playbackErrors.txt")
		Files.write(outputFile, sb.lines())
	}

	companion object {
		private const val header = "ExplorationTrace;Action"
	}
}
