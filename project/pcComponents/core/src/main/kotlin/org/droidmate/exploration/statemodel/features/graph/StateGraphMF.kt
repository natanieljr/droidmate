package org.droidmate.exploration.statemodel.features.graph

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.newCoroutineContext
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.actions.AbstractExplorationAction.Companion.getActionIdentifier
import org.droidmate.exploration.statemodel.ActionData
import org.droidmate.exploration.statemodel.ActionData.Companion.ActionDataFields
import org.droidmate.exploration.statemodel.StateData
import org.droidmate.exploration.statemodel.features.ModelFeature
import java.lang.Thread.sleep
import kotlin.coroutines.experimental.CoroutineContext

class StateGraphMF @JvmOverloads constructor(private val graph: IGraph<StateData, ActionData> =
						   Graph(StateData.emptyState,
								   stateComparison = {a, b -> a.uid == b.uid },
								   labelComparison = {a, b ->
									   val fields = arrayOf(ActionDataFields.Action, ActionDataFields.WId, ActionDataFields.Exception, ActionDataFields.SuccessFul)
									   val aData = a.actionString(fields)
									   val bData = b.actionString(fields)

									   aData == bData
								   })) : ModelFeature(), IGraph<StateData, ActionData> by graph {


	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("StateGraphMF"), parent = job)

	override suspend fun onNewAction(lazyAction: Lazy<ActionData>, prevState: StateData, newState: StateData) {
		// TODO For some reason it's not initialized here sometimes,
		try{
			log.debug("Adding action to model: ${lazyAction.value}")
		}catch(e: Exception){
			sleep(100)
		}

		// After reset, link to root node.
		// Root node is static (Empty) because the app may present different initial screens after each reset
		val sourceState = if (lazyAction.value.actionType == getActionIdentifier<ResetAppExplorationAction>())
			root.data
		else
			prevState

		val prevLabel = ActionData.emptyWithWidget(lazyAction.value.targetWidget)

		// Try to update a previous label (from prevState to emptyState) if it exists, otherwise add a new one
		if (prevLabel.targetWidget != null)
			this.update(sourceState, StateData.emptyState, newState, prevLabel, lazyAction.value) ?: this.add(sourceState, newState, lazyAction.value)
		else
			this.add(sourceState, newState, lazyAction.value)

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