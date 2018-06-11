package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.statemodel.Model
import java.util.*
import kotlin.collections.HashSet
import kotlin.coroutines.experimental.CoroutineContext

/**
 * This model is used by the playback class to identify actions which could not be replayed.
 * This information is the used for dumping/reporting
 */
class ActionPlaybackFeature(val storedModel: Model,
							val skippedActions: MutableSet<Pair<Int,Int>> = HashSet()) : ModelFeature() {
	init{
		job = Job(parent = (this.job)) // we don't want to wait for other features (or having them wait for us), therefore create our own (child) job
	}

	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("EventProbabilityMF"), parent = job)

	fun addNonReplayableActions(traceIdx: Int, actionIdx: Int){
		skippedActions.add(Pair(traceIdx, actionIdx))
	}
}
