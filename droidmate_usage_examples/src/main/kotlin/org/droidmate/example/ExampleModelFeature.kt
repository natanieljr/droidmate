package org.droidmate.example

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.explorationModel.ActionData
import org.droidmate.explorationModel.StateData
import org.droidmate.exploration.modelFeatures.ModelFeature
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext

class ExampleModelFeature: ModelFeature(){
	// Prevents this feature from blocking the execution of others
	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("ExampleModelFeature"), parent = job)

	init {
		job = Job(parent = (this.job)) // We don't want to wait for other modelFeatures (or having them wait for us), therefore create our own (child) job
	}

	override suspend fun onNewAction(traceId: UUID, deferredAction: Deferred<ActionData>, prevState: StateData, newState: StateData) {
		val action = deferredAction.await()

		// Check [org.droidmate.explorationModel.modelFeatures.ModelFeature] for more notification possibilities
		println("Transitioning from state $prevState to state $newState")

		if (action.targetWidget != null)
			println("Clicked widget: ${action.targetWidget}")

		println("Triggered APIs: ${action.deviceLogs.apiLogs.joinToString { "${it.uniqueString}\n" }}")

		action.deviceLogs

		count++
	}

	var count : Int = 0
		private set

}