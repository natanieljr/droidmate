package org.droidmate.example

import org.droidmate.exploration.actions.AbstractExplorationAction
import org.droidmate.exploration.actions.PressBackExplorationAction
import org.droidmate.exploration.actions.ResetAppExplorationAction
import org.droidmate.exploration.actions.TerminateExplorationAction
import org.droidmate.exploration.strategy.AbstractStrategy

class ExampleStrategy(private val someId: Int): AbstractStrategy(){
	// Model features can be accessed from the strategy as well
	val modelFeature : ExampleModelFeature by lazy { eContext.getOrCreateWatcher<ExampleModelFeature>() }

	override fun internalDecide(): AbstractExplorationAction {
		return when {
			eContext.isEmpty() -> ResetAppExplorationAction()
			modelFeature.count == someId -> TerminateExplorationAction()
			else -> return PressBackExplorationAction()
		}
	}

	override fun mustPerformMoreActions(): Boolean {
		return false
	}

}