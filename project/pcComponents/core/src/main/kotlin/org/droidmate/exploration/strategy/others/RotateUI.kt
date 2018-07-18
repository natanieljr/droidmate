package org.droidmate.exploration.strategy.others

import org.droidmate.exploration.actions.AbstractExplorationAction
import org.droidmate.exploration.actions.rotate
import org.droidmate.exploration.strategy.AbstractStrategy

class RotateUI(private val rotation: Int) : AbstractStrategy() {
	override fun mustPerformMoreActions(): Boolean {
		return false
	}

	override fun internalDecide(): AbstractExplorationAction {
		return eContext.rotate(rotation)
	}
}
