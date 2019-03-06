package org.droidmate.exploration.modelFeatures.graph

import kotlinx.coroutines.CoroutineName
import org.droidmate.deviceInterface.exploration.isLaunchApp
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.ModelFeature
import org.droidmate.explorationModel.emptyId
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.Interaction.Companion.ActionDataFields
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext

class StateGraphMF @JvmOverloads constructor(private val graph: IGraph<State, Interaction> =
		                                             Graph(State.emptyState,
				                                             stateComparison = { a, b -> a.uid == b.uid },
				                                             labelComparison = { a, b ->
					                                             val fields = arrayOf(ActionDataFields.Action, ActionDataFields.WId, ActionDataFields.Exception, ActionDataFields.SuccessFul)
					                                             val aData = a.actionString(fields)
					                                             val bData = b.actionString(fields)

					                                             aData == bData
				                                             })) : ModelFeature(), IGraph<State, Interaction> by graph {
	private fun emptyWithWidget(widget: Widget?): Interaction =
			Interaction("EMPTY", widget, LocalDateTime.MIN, LocalDateTime.MIN, true, "root action", emptyId, //FIXME sep should be read from eContext instead
					prevState = emptyId)


	override val coroutineContext: CoroutineContext = CoroutineName("StateGraphMF")

	override suspend fun onContextUpdate(context: ExplorationContext) {
		val lastAction = context.getLastAction()
		val newState = context.getCurrentState()
		// After reset, link to root node.
		// Root node is static (Empty) because the app may present different initial screens after each reset
		@Suppress("DEPRECATION")
		val sourceState = if (lastAction.actionType.isLaunchApp())
			root.data
		else
			context.getState(lastAction.prevState) ?: State.emptyState

		val prevLabel = emptyWithWidget(lastAction.targetWidget)

		// Try to update a previous label (from prevState to emptyState) if it exists, otherwise update a new one
		if (prevLabel.targetWidget != null)
			this.update(sourceState, State.emptyState, newState, prevLabel, lastAction) ?: this.add(sourceState, newState, lastAction)
		else
			this.add(sourceState, newState, lastAction)

		// Add all available widgets as transitions to an emptyState,
		// After an action this transition is updated
		newState.actionableWidgets.forEach {
			this.add(newState, null, emptyWithWidget(it), updateIfExists = false)
		}
	}

	override fun toString(): String {
		return graph.toString()
	}
}