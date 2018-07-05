package org.droidmate.exploration.actions

import org.droidmate.exploration.statemodel.Widget
import org.droidmate.uiautomator_daemon.guimodel.Action
import org.droidmate.uiautomator_daemon.guimodel.CoordinateLongClickAction
import org.droidmate.uiautomator_daemon.guimodel.LongClickAction

open class LongClickExplorationAction(widget: Widget,
                                      useCoordinates: Boolean = true,
                                      delay: Int = 100): AbstractClickExplorationAction(widget, useCoordinates, delay){
	override val description: String
		get() = "long-click"

	override fun toShortString(): String = "LC ${widget.toShortString()}"// "SW? ${if (swipe) 1 else 0} LC? ${if (longClick) 1 else 0} " + widget.toShortString()

	override fun getClickAction(widget: Widget): Action {
		return LongClickAction(widget.xpath, widget.resourceId)
	}

	override fun getCoordinateClickAction(x: Int, y: Int): Action {
		return CoordinateLongClickAction(x, y)
	}
}