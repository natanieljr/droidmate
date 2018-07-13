package org.droidmate.example

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.statemodel.StateData
import org.droidmate.exploration.statemodel.Widget
import org.droidmate.exploration.statemodel.features.ModelFeature
import kotlin.coroutines.experimental.CoroutineContext

class ExampleModelFeature: ModelFeature(){
	// Prevents this feature from blocking the execution of others
	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("ExampleModelFeature"), parent = job)

	init {
		job = Job(parent = (this.job)) // We don't want to wait for other features (or having them wait for us), therefore create our own (child) job
	}

	override suspend fun onNewInteracted(targetWidget: Widget?, prevState: StateData, newState: StateData) {
		// Check [org.droidmate.exploration.statemodel.features.ModelFeature] for more notification possibilities
		println("Widget $targetWidget was clicked. Old state: $prevState, new state: $newState")
		count++
	}

	var count : Int = 0
		private set

}