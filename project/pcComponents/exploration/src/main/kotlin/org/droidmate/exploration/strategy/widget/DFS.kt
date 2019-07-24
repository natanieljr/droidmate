package org.droidmate.exploration.strategy.widget

import org.droidmate.exploration.actions.terminateApp
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.click
import org.droidmate.exploration.actions.pressBack
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class DFS(private val priority: Int = 0): GraphBasedExploration(){
	override fun getPriority(): Int = priority

	override suspend fun <M: AbstractModel<S, W>,S: State<W>,W: Widget> computeNextAction(
		eContext: ExplorationContext<M, S, W>
	): ExplorationAction {
		val currentState = eContext.getCurrentState()
		val nextEdge = graph.edges(currentState)
				.firstOrNull { it.destination == null &&
						it.label.targetWidget != null &&
						currentState.actionableWidgets.contains(it.label.targetWidget!!)
				}

		return if (nextEdge == null) {
			val ancestors = graph.ancestors(currentState)

			return if (ancestors.isNotEmpty())
				ExplorationAction.pressBack()
			else
				ExplorationAction.terminateApp()
		}
		else
			nextEdge.label.targetWidget!!.click()
	}
}