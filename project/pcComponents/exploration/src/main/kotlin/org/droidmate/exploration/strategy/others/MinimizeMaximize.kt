package org.droidmate.exploration.strategy.others

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.minimizeMaximize
import org.droidmate.exploration.strategy.AExplorationStrategy
import org.droidmate.exploration.strategy.AbstractStrategy
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

@Deprecated("to be deleted")
class MinimizeMaximize : AExplorationStrategy() {
	override fun getPriority(): Int {
		TODO("deprecated")
	}

	override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> computeNextAction(eContext: ExplorationContext<M, S, W>): ExplorationAction {
		return eContext.minimizeMaximize()
	}
}