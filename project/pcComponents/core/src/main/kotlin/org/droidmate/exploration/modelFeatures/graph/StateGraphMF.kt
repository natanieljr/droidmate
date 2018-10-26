package org.droidmate.exploration.modelFeatures.graph

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.deviceInterface.exploration.isLaunchApp
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.ModelFeature
import org.droidmate.explorationModel.ActionData
import org.droidmate.explorationModel.ActionData.Companion.ActionDataFields
import org.droidmate.explorationModel.StateData
import kotlin.coroutines.experimental.CoroutineContext

class StateGraphMF @JvmOverloads constructor(private val graph: IGraph<StateData, ActionData> =
		                                             Graph(StateData.emptyState,
				                                             stateComparison = { a, b -> a.uid == b.uid },
				                                             labelComparison = { a, b ->
					                                             val fields = arrayOf(ActionDataFields.Action, ActionDataFields.WId, ActionDataFields.Exception, ActionDataFields.SuccessFul)
					                                             val aData = a.actionString(fields)
					                                             val bData = b.actionString(fields)

					                                             aData == bData
				                                             })) : ModelFeature(), IGraph<StateData, ActionData> by graph {


	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("StateGraphMF"), parent = job)

	override suspend fun onContextUpdate(context: ExplorationContext) {
		val lastAction = context.getLastAction()
		val newState = context.getCurrentState()
		// After reset, link to root node.
		// Root node is static (Empty) because the app may present different initial screens after each reset
		@Suppress("DEPRECATION")
		val sourceState = if (lastAction.actionType.isLaunchApp())
			root.data
		else
			context.getState(lastAction.prevState) ?: StateData.emptyState

		val prevLabel = ActionData.emptyWithWidget(lastAction.targetWidget)

		// Try to update a previous label (from prevState to emptyState) if it exists, otherwise add a new one
		if (prevLabel.targetWidget != null)
			this.update(sourceState, StateData.emptyState, newState, prevLabel, lastAction) ?: this.add(sourceState, newState, lastAction)
		else
			this.add(sourceState, newState, lastAction)

		// Add all available widgets as transitions to an emptyState,
		// After an action this transition is updated
		newState.actionableWidgets.forEach {
			this.add(newState, null, ActionData.emptyWithWidget(it), updateIfExists = false)
		}
	}

	override fun toString(): String {
		return graph.toString()
	}
}