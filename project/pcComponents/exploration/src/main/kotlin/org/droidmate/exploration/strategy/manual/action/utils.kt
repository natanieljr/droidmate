package org.droidmate.exploration.strategy.manual.action

import org.droidmate.deviceInterface.exploration.isActivated
import org.droidmate.exploration.actions.clickEvent
import org.droidmate.exploration.actions.longClickEvent
import org.droidmate.exploration.actions.tick
import org.droidmate.explorationModel.interaction.Widget

fun Widget.triggerTap(delay: Long, isVisible: Boolean = false) =
	when{
		clickable||selected.isActivated() -> clickEvent(delay)
		checked!=null -> tick(ignoreVisibility = isVisible)
		longClickable -> longClickEvent(delay, ignoreVisibility = isVisible)
		else -> throw RuntimeException("given widget ${this.id} : $this is not clickable")
	}

