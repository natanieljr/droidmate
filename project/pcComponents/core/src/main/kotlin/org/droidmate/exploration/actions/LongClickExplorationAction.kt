package org.droidmate.exploration.actions

import org.droidmate.exploration.statemodel.Widget
import org.droidmate.uiautomator_daemon.guimodel.Action
import org.droidmate.uiautomator_daemon.guimodel.CoordinateLongClickAction

@Deprecated("to be removed",replaceWith = ReplaceWith("widget.longClick()"))
open class LongClickExplorationAction
@Deprecated("click actions are always going to use coordinates in the future",ReplaceWith("widget.longClick()"))
constructor(widget: Widget, useCoordinates: Boolean = true, delay: Int = 0): AbstractClickExplorationAction(widget, useCoordinates, delay){
	constructor(widget: Widget): this(widget,true,0)


	override val description: String
		get() = "long-click"

	override fun toShortString(): String = "LC ${widget.toShortString()}"// "SW? ${if (swipe) 1 else 0} LC? ${if (longClick) 1 else 0} " + widget.toShortString()

	override fun getClickAction(widget: Widget): Action {
		return widget.getLongClickAction()
	}

	override fun getCoordinateClickAction(x: Int, y: Int): Action {
		return CoordinateLongClickAction(x, y)
	}
}