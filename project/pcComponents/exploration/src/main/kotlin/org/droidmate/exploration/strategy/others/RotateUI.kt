package org.droidmate.exploration.strategy.others

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.actions.rotate
import org.droidmate.exploration.strategy.AbstractStrategy

class RotateUI(private val rotation: Int) : AbstractStrategy() {
	override suspend fun internalDecide(): ExplorationAction {
		return eContext.rotate(rotation)
	}
}
