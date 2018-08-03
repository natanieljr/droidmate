package org.droidmate.exploration.strategy.others

import org.droidmate.deviceInterface.guimodel.ExplorationAction
import org.droidmate.exploration.actions.minimizeMaximize
import org.droidmate.exploration.strategy.AbstractStrategy

class MinimizeMaximize : AbstractStrategy() {
	override fun mustPerformMoreActions(): Boolean = false

	override fun internalDecide(): ExplorationAction {
		return eContext.minimizeMaximize()
	}
}