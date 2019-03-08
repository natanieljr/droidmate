package org.droidmate.exploration.strategy.widget

import org.droidmate.exploration.actions.terminateApp
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.actions.click
import org.droidmate.exploration.actions.pressBack

class DFS: GraphBasedExploration(){
	override suspend fun chooseAction(): ExplorationAction {
		val nextEdge = graph.edges(currentState)
				.firstOrNull { it.destination == null &&
						it.label.targetWidget != null &&
						currentState.actionableWidgets.contains(it.label.targetWidget!!)
				}

		return if (nextEdge == null) {
			val ancestors = graph.ancestors(currentState)

			return if (ancestors.isNotEmpty())
				eContext.pressBack()
			else
				terminateApp()
		}
		else
			nextEdge.label.targetWidget!!.click()
	}
}