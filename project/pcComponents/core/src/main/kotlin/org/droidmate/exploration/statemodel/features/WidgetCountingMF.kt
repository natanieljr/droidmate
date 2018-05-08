package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.joinChildren
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class WidgetCountingMF:  ModelFeature(){
	// records how often a specific widget was selected and from which state-context (widget.uid -> Map<state.uid -> numActions>)
	private val wCnt: ConcurrentHashMap<UUID, MutableMap<UUID, Int>> = ConcurrentHashMap()

	init{
		job = Job(parent = (this.job)) // we don't want to wait for other features (or having them wait for us), therefore create our own (child) job
	}

	/**
	 * this function is used to increase the counter of a specific widget with [wId] in the context of [stateId]
	 * if there is no entry yet for the given widget id, the counter value is initialized to 1
	 * @param wId the unique id of the target widget for the (new) action
	 * @param stateId the unique id of the state (the prevState) from which the widget was triggered
	 */
	fun incCnt(wId: UUID, stateId: UUID){
		wCnt.compute(wId,
				{ _, m -> m?.incCnt(stateId) ?: mutableMapOf(stateId to 1) })
	}

	/** decrease the counter for a given widget id [wId] and state context [stateId].
	 * The minimal possible value is 0 for any counter value.
	 */
	fun decCnt(wId: UUID, stateId: UUID){
		wCnt.compute(wId,
				{ _,m -> m?.decCnt(stateId) ?: mutableMapOf(stateId to 0)})
	}

	suspend fun isBlacklisted(wId: UUID, threshold: Int = 1): Boolean {
		job.joinChildren() // wait that all updates are applied before changing the counter value
		return wCnt.sumCounter(wId) >= threshold
	}

	suspend fun isBlacklistedInState(wId: UUID, sId: UUID, threshold: Int = 1): Boolean {
		job.joinChildren() // wait that all updates are applied before changing the counter value
		return wCnt.getCounter(wId, sId) >= threshold
	}

	/** dumping the current state of the widget counter
	 * job.joinChildren() before dumping to ensure that all updating coroutines completed
	 */
	suspend fun dump(file: File){
		job.joinChildren() // wait that all updates are applied before changing the counter value
		file.bufferedWriter().use { out ->
			out.write(header)
			wCnt.toSortedMap(compareBy { it.toString() }).forEach { wMap ->	wMap.value.entries.forEach { (sId, cnt) ->
				out.newLine()
				out.write("${wMap.key} ;\t$sId ;\t$cnt")
			}}
		}
	}
	companion object {
		val header = "WidgetId".padEnd(38)+"; State-Context".padEnd(38)+"; # listed"

	}
}