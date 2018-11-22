package org.droidmate.exploration.strategy.others

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.actions.minimizeMaximize
import org.droidmate.exploration.strategy.AbstractStrategy

class MinimizeMaximize : AbstractStrategy() {
	override suspend fun internalDecide(): ExplorationAction {
		return eContext.minimizeMaximize()
	}
}