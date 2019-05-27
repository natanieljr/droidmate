package saarland.cispa.exploration.android.strategy.action

import org.droidmate.deviceInterface.exploration.ActionQueue
import org.droidmate.deviceInterface.exploration.Click
import org.droidmate.exploration.actions.*
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.Widget

fun Widget.triggerTap(delay: Long, isVisible: Boolean = false) =
	when{
		clickable -> clickEvent(delay)
		checked!=null -> tick(ignoreVisibility = isVisible)
		longClickable -> clickOrLongClickC(delay, isVisible = isVisible)
		else -> throw RuntimeException("given widget ${this.id} : $this is not clickable")
	}

fun Widget.clickOrLongClickC(delay: Long=0, isVisible: Boolean = false) =
	if(!clickable) {
		ExplorationTrace.widgetTargets.add(this) // widgetTarget for the coordinate click event
		ActionQueue(
			listOf(clickCoordinateC().let { (x, y) -> Click(x, y, true, delay) }, longClickEvent(delay, isVisible)),
			delay = 0
		)
	}else longClickEvent(delay,isVisible)

// for the function clickOrLongClick it works better to use the center of visible bounds due to layout fracturing in the affected item lists
fun Widget.clickCoordinateC(): Pair<Int, Int> = visibleBounds.center

