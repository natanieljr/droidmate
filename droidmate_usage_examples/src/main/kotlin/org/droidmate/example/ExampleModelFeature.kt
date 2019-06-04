package org.droidmate.example

import kotlinx.coroutines.CoroutineName
import org.droidmate.exploration.modelFeatures.ModelFeature
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import java.util.*
import kotlin.coroutines.CoroutineContext

class ExampleModelFeature: ModelFeature(){
	// Prevents this feature from blocking the execution of others
	override val coroutineContext: CoroutineContext = CoroutineName("ExampleModelFeature")


	override suspend fun onNewAction(traceId: UUID, interactions: List<Interaction<*>>, prevState: State<*>, newState: State<*>) {
		super.onNewAction(traceId, interactions, prevState, newState)
		val firstAction = interactions.first()

		// Check [org.droidmate.explorationModel.modelFeatures.ModelFeature] for more notification possibilities
		println("Transitioning from state $prevState to state $newState")

		if (firstAction.targetWidget != null)
			println("Clicked widget: ${firstAction.targetWidget}")

		println("Triggered APIs: ${firstAction.deviceLogs.joinToString { "$it\n" }}")

		firstAction.deviceLogs

		count++
	}

	var count : Int = 0
		private set

}