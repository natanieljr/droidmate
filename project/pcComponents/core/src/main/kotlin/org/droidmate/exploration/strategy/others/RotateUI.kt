package org.droidmate.exploration.strategy.others

import org.droidmate.deviceInterface.guimodel.ExplorationAction
import org.droidmate.exploration.actions.rotate
import org.droidmate.exploration.strategy.AbstractStrategy

class RotateUI(private val rotation: Int) : AbstractStrategy() {
	override fun internalDecide(): ExplorationAction {
		return eContext.rotate(rotation)
	}
}
