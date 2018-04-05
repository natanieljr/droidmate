// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org
package org.droidmate.exploration.strategy.widget

import kotlinx.coroutines.experimental.joinChildren
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.configuration.Configuration
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.PressBackExplorationAction
import org.droidmate.exploration.statemodel.Widget
import org.droidmate.exploration.statemodel.config.emptyId
import org.droidmate.exploration.statemodel.features.ActionCounterMF
import org.droidmate.exploration.statemodel.features.BlackListMF
import org.droidmate.exploration.statemodel.features.listOfSmallest
import org.droidmate.exploration.strategy.StrategyPriority
import java.util.*

/**
 * Exploration strategy that select a (pseudo-)random widget from the screen.
 */
open class RandomWidget @JvmOverloads constructor(randomSeed: Long,
												  private val priority: StrategyPriority = StrategyPriority.PURELY_RANDOM_WIDGET) : Explore() {
	/**
	 * Creates a new exploration strategy instance using the []configured random seed][cfg]
	 */
	@JvmOverloads
	constructor(cfg: Configuration,
				priority: StrategyPriority = StrategyPriority.PURELY_RANDOM_WIDGET): this(cfg.randomSeed.toLong(), priority)

	protected val random = Random(randomSeed)
	private val counter: ActionCounterMF by lazy { context.getOrCreateWatcher<ActionCounterMF>()	}
	private val blackList: BlackListMF by lazy {	context.getOrCreateWatcher<BlackListMF>() }

	private fun mustRepeatLastAction(): Boolean {
		if (!this.context.isEmpty()) {

			// Last state was runtime permission
			return (currentState.isRequestRuntimePermissionDialogBox) &&
					// Has last action
					this.context.lastTarget != null &&
					// Has a state that is not a runtime permission
					context.getModel().let{runBlocking { it.getStates() }}
							.filterNot { it.stateId == emptyId }
							.filterNot { it.isRequestRuntimePermissionDialogBox }
							.isNotEmpty() &&
					// Can re-execute the same action
					currentState.actionableWidgets
							.any { p -> context.lastTarget?.let { p.id == it.id } ?: false }
		}

		return false
	}

	/** if we trigger any functionality which requires (not yet granted) Android permissions an PermissionDialogue
	 * will appear, but the functionality may not be triggered yet.
	 * Now we do not want to penalize this target just because it required a permission and the functionality was not yet triggered
	 */
	private fun repeatLastAction(): ExplorationAction {
//		val lastActionBeforePermission = currentState.let {
//			!(it.isRequestRuntimePermissionDialogBox || it.stateId == emptyId)
//		}

//        return lastActionBeforePermission.action
		TODO("instead PermissionStrategy should decrease the widgetCounter again, if the prev state is the same like after handling permission " +
				"to avoid penalizing the target")
	}

	protected open fun getAvailableWidgets(): List<Widget> {
		return currentState.actionableWidgets
	}

	/** use this function to filter potential candidates against previously blacklisted widgets
	 * @param block your function determining the ExplorationAction based on the filtered candidates
	 * @param t1 the threshold to consider the widget blacklisted within the current state context
	 * @param t2 the threshold to consider the widget blacklisted over all states
	 */
	protected open fun excludeBlacklisted(candidates: List<Widget>, t1:Int=1, t2:Int=2, block:( listedInsState:List<Widget>, blacklisted: List<Widget>)->ExplorationAction): ExplorationAction =
			candidates.filterNot { blackList.isBlacklistedInState(it.uid, currentState.uid, t1) }.let{ noBlacklistedInState ->
				noBlacklistedInState.filterNot { blackList.isBlacklisted(it.uid, t2) }.let{ noBlacklisted ->
					block(noBlacklistedInState, noBlacklisted)
				}
			}


	private fun List<Widget>.chooseRandomly():ExplorationAction{
		context.lastTarget = this[random.nextInt(this.size)]
		return chooseActionForWidget(context.lastTarget!!)
	}

	protected open fun chooseRandomWidget(): ExplorationAction {
		runBlocking { counter.job.joinChildren() }  // this waits for both children counter and blacklist
		val candidates =
		// for each widget in this state the number of interactions
				counter.numExplored(currentState).entries.groupBy { it.value }.let {
					it.listOfSmallest()?.map { it.key }?.let { leastInState: List<Widget> ->
						// determine the subset of widgets which were least interacted with
						// if multiple widgets clicked with same frequency, choose the one least clicked over all states
						if (leastInState.size > 1) {
							leastInState.groupBy { counter.widgetCnt(it.uid) }.listOfSmallest()
						} else leastInState
					}
				}
						?: currentState.actionableWidgets

		assert(candidates.isNotEmpty())
		return excludeBlacklisted(candidates){ noBlacklistedInState, noBlacklisted ->
			when {
				noBlacklisted.isNotEmpty() -> noBlacklisted.chooseRandomly()
				noBlacklistedInState.isEmpty() -> noBlacklistedInState.chooseRandomly()
				else -> PressBackExplorationAction() // we are stuck, everything is blacklisted
			}
		}
	}

	protected open fun chooseActionForWidget(chosenWidget: Widget): ExplorationAction {
		var widget = chosenWidget

		while (!chosenWidget.canBeActedUpon()) {
			widget = currentState.widgets.first { it.id == chosenWidget.parentId }
		}

		val actionList: MutableList<ExplorationAction> = mutableListOf()

		if (widget.longClickable)
			actionList.add(ExplorationAction.newWidgetExplorationAction(widget, longClick = true))

		if (widget.clickable)
			actionList.add(ExplorationAction.newWidgetExplorationAction(widget))

		if (widget.checked != null)
			actionList.add(ExplorationAction.newWidgetExplorationAction(widget))

		// TODO: Currently is doing a normal click. Replace for the swipe action (bellow)
		if (widget.scrollable)
			actionList.add(ExplorationAction.newWidgetExplorationAction(widget))

		/*if (chosenWidget.scrollable) {
				actionList.add(ExplorationAction.newWidgetExplorationAction(chosenWidget, 0, guiActionSwipe_right))
				actionList.add(ExplorationAction.newWidgetExplorationAction(chosenWidget, 0, guiActionSwipe_up))
				actionList.add(ExplorationAction.newWidgetExplorationAction(chosenWidget, 0, guiActionSwipe_left))
				actionList.add(ExplorationAction.newWidgetExplorationAction(chosenWidget, 0, guiActionSwipe_down))
		}*/

		logger.debug("Chosen widget info: $widget")

		val maxVal = actionList.size
		assert(maxVal > 0)
		val randomIdx = random.nextInt(maxVal)
		return actionList[randomIdx]
	}

	override fun getFitness(): StrategyPriority {
		// Arbitrary established
		return this.priority
	}

	override fun chooseAction(): ExplorationAction {
		// Repeat previous action is last action was to click on a runtime permission dialog
		if (mustRepeatLastAction())
			return repeatLastAction()

		return chooseRandomWidget()
	}

	// region java overrides

	override fun equals(other: Any?): Boolean {
		if (other !is RandomWidget)
			return false

		return other.priority == this.priority
	}

	override fun hashCode(): Int {
		return this.priority.value.hashCode()
	}

	override fun toString(): String {
		return "${this.javaClass}\tPriority: ${this.priority}"
	}

	// endregion
}
