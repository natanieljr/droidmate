package org.droidmate.exploration.strategy.widget

import org.droidmate.exploration.actions.AbstractExplorationAction
import org.droidmate.exploration.actions.ClickExplorationAction
import org.droidmate.exploration.actions.PressBackExplorationAction
import org.droidmate.exploration.actions.TerminateExplorationAction

class DFS: GraphBasedExploration(){
	override fun chooseAction(): AbstractExplorationAction {
		val nextEdge = graph.edges(currentState)
				.firstOrNull { it.destination == null &&
						it.label.targetWidget != null &&
						currentState.actionableWidgets.contains(it.label.targetWidget!!)
				}

		return if (nextEdge == null) {
			val ancestors = graph.ancestors(currentState)

			return if (ancestors.isNotEmpty())
				PressBackExplorationAction()
			else
				TerminateExplorationAction()
		}
		else
			ClickExplorationAction(nextEdge.label.targetWidget!!)
	}
}