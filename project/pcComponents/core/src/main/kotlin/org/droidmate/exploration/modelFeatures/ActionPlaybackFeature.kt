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

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.joinChildren
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.ExplorationContext
import org.droidmate.explorationModel.Model
import java.nio.file.Files
import kotlin.collections.HashSet
import kotlin.coroutines.experimental.CoroutineContext

/**
 * This model is used by the playback class to identify actions which could not be replayed.
 * This information is the used for dumping/reporting
 */
class ActionPlaybackFeature(val storedModel: Model,
							val skippedActions: MutableSet<Pair<Int,Int>> = HashSet()) : ModelFeature() {
	init{
		job = Job(parent = (this.job)) // we don't want to wait for other modelFeatures (or having them wait for us), therefore create our own (child) job
	}

	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("EventProbabilityMF"), parent = job)

	fun addNonReplayableActions(traceIdx: Int, actionIdx: Int){
		skippedActions.add(Pair(traceIdx, actionIdx))
	}

	override suspend fun dump(context: ExplorationContext) {
		job.joinChildren()

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
