package org.droidmate.example

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.actions.click
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.resetApp
import org.droidmate.exploration.actions.terminateApp
import org.droidmate.exploration.strategy.AbstractStrategy

class ExampleStrategy(private val someId: Int): AbstractStrategy(){
	// Model modelFeatures can be accessed from the strategy as well
	private val modelFeature : ExampleModelFeature
			get() = eContext.getOrCreateWatcher()

	override suspend fun internalDecide(): ExplorationAction {
		return when {
			eContext.isEmpty() -> eContext.resetApp()

			modelFeature.count == someId -> terminateApp()

			currentState.actionableWidgets.isNotEmpty() -> currentState.actionableWidgets.first().click()

			else -> eContext.pressBack()
		}
	}
}