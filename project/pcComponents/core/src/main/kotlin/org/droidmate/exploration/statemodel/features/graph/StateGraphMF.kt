package org.droidmate.exploration.statemodel.features.graph

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.statemodel.StateData
import org.droidmate.exploration.statemodel.features.ModelFeature
import kotlin.coroutines.experimental.CoroutineContext

class StateGraphMF(val stateGraph: Graph) : ModelFeature(){
	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("StateGraphMF"), parent = job)

	override suspend fun onNewAction(lazyAction: Lazy<ActionData>, prevState: StateData, newState: StateData) {
		stateGraph.add(prevState, newState, lazyAction.value)
	}
}