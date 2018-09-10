package org.droidmate.example

import org.droidmate.deviceInterface.guimodel.ExplorationAction
import org.droidmate.exploration.actions.click
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.resetApp
import org.droidmate.exploration.actions.terminateApp
import org.droidmate.exploration.strategy.AbstractStrategy

class ExampleStrategy(private val someId: Int): AbstractStrategy(){
	// Model features can be accessed from the strategy as well
	val modelFeature : ExampleModelFeature by lazy { eContext.getOrCreateWatcher<ExampleModelFeature>() }

	override fun internalDecide(): ExplorationAction {
		return when {
			eContext.isEmpty() -> eContext.resetApp()

			modelFeature.count == someId -> terminateApp()

			currentState.actionableWidgets.isNotEmpty() -> currentState.actionableWidgets.first().click()

			else -> eContext.pressBack()
		}
	}

	override fun mustPerformMoreActions(): Boolean {
		return false
	}

}