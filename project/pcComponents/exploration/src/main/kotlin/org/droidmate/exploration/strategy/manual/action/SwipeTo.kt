package saarland.cispa.exploration.android.strategy.action

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.Swipe
import org.droidmate.explorationModel.interaction.Widget

@Suppress("MemberVisibilityCanBePrivate")
object SwipeTo {
	private const val sxOffset=100 // for right scrolling to prevent the "open menu" feature, implemented in some apps

	fun widestElem(widgets:Collection<Widget>) = widgets.filter { it.isVisible && !it.isKeyboard}.maxBy { it.visibleBounds.width }

	fun appArea(widgets:Collection<Widget>) = widgets.find { it.parentId == null && it.isVisible }?.visibleBounds ?: Rectangle.empty()

	fun highestElement(widgets:Collection<Widget>) = with(appArea(widgets)) {
		widgets.filter {
			it.isVisible && !it.isKeyboard &&
					// since we try to ignore the overall layout as it may continue a menu bar which would prevent scroll to work with a start point on the menu element
					(it.visibleBounds.topY > topY || it.visibleBounds.bottomY < bottomY)
		}.maxBy { it.visibleBounds.height }
	}

	fun left(stateWidgets: Collection<Widget>): ExplorationAction = // use an offset of 2 from the right border since some apps do not recognize the swipe otherwise
			with(widestElem(stateWidgets)!!.visibleBounds){ Swipe(Pair(rightX-2,center.second),Pair(leftX,center.second))}

	fun right(stateWidgets: Collection<Widget>): ExplorationAction = with(widestElem(stateWidgets)!!.visibleBounds){ Swipe(Pair(leftX+sxOffset,center.second), Pair(rightX,center.second))}

	fun top(stateWidgets: Collection<Widget>): ExplorationAction =
			with(highestElement(stateWidgets)!!.visibleBounds){ Swipe(Pair(center.first,bottomY),Pair(center.first,topY))}

	fun bottom(stateWidgets: Collection<Widget>): ExplorationAction =
			with(highestElement(stateWidgets)!!.visibleBounds){ Swipe(Pair(center.first,topY), Pair(center.first,bottomY))}

}