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

import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.debug.debugT
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.statemodel.Widget
import org.droidmate.exploration.statemodel.emptyId
import org.droidmate.exploration.statemodel.features.ActionCounterMF
import org.droidmate.exploration.statemodel.features.BlackListMF
import org.droidmate.exploration.statemodel.features.listOfSmallest
import java.util.*

/**
 * Exploration strategy that select a (pseudo-)random widget from the screen.
 */
open class RandomWidget @JvmOverloads constructor(randomSeed: Long,
												  private val biased: Boolean = true) : ExplorationStrategy() {
	/**
	 * Creates a new exploration strategy instance using the []configured random seed][cfg]
	 */
	constructor(cfg: ConfigurationWrapper): this(cfg.randomSeed)

	protected val random = Random(randomSeed)
	@Suppress("MemberVisibilityCanBePrivate")
	protected val counter: ActionCounterMF by lazy { eContext.getOrCreateWatcher<ActionCounterMF>()	}
	private val blackList: BlackListMF by lazy {	eContext.getOrCreateWatcher<BlackListMF>() }

	private fun mustRepeatLastAction(): Boolean {
		if (!this.eContext.isEmpty()) {

			// Last state was runtime permission
			return (currentState.isRequestRuntimePermissionDialogBox) &&
					// Has last action
					this.eContext.lastTarget != null &&
					// Has a state that is not a runtime permission
					eContext.getModel().let{runBlocking { it.getStates() }}
							.filterNot { it.stateId == emptyId }
							.filterNot { it.isRequestRuntimePermissionDialogBox }
							.isNotEmpty() &&
					// Can re-execute the same action
					currentState.actionableWidgets
							.any { p -> eContext.lastTarget?.let { p.id == it.id } ?: false }
		}

		return false
	}

	/** if we trigger any functionality which requires (not yet granted) Android permissions an PermissionDialogue
	 * will appear, but the functionality may not be triggered yet.
	 * Now we do not want to penalize this target just because it required a permission and the functionality was not yet triggered
	 */
	private fun repeatLastAction(): AbstractExplorationAction {
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
	 * @param tInState the threshold to consider the widget blacklisted within the current state eContext
	 * @param tOverall the threshold to consider the widget blacklisted over all states
	 */
	protected open suspend fun excludeBlacklisted(candidates: List<Widget>, tInState:Int=1, tOverall:Int=2, block:(listedInsState:List<Widget>, blacklisted: List<Widget>)->List<Widget>): List<Widget> =
			candidates.filterNot { blackList.isBlacklistedInState(it.uid, currentState.uid, tInState) }.let{ noBlacklistedInState ->
				noBlacklistedInState.filterNot { blackList.isBlacklisted(it.uid, tOverall) }.let{ noBlacklisted ->
					block(noBlacklistedInState, noBlacklisted)
				}
			}


	private fun List<Widget>.chooseRandomly():AbstractExplorationAction{
		if(this.isEmpty())
			return eContext.resetApp()

		eContext.lastTarget = this[random.nextInt(this.size)]
		return chooseActionForWidget(eContext.lastTarget!!)
	}

	open suspend fun computeCandidates():Collection<Widget> = debugT("blacklist computation", {
		val nonCrashing = super.eContext.nonCrashingWidgets()
		excludeBlacklisted(super.eContext.nonCrashingWidgets()){ noBlacklistedInState, noBlacklisted ->
			when {
				noBlacklisted.isNotEmpty() -> noBlacklisted
				noBlacklistedInState.isNotEmpty() -> noBlacklistedInState
				else -> nonCrashing
			}
		}
	}, inMillis = true)

	private fun chooseBiased(): AbstractExplorationAction = runBlocking{
		val candidates = computeCandidates().let { filteredCandidates ->
			// for each widget in this state the number of interactions
			counter.numExplored(currentState, filteredCandidates).entries
					.groupBy { it.key.packageName }.flatMap { (pkgName,countEntry) ->
						if(pkgName != super.eContext.apk.packageName) {
							val pkgActions = counter.pkgCount(pkgName)
							countEntry.map { Pair(it.key, pkgActions) }
						} else
							countEntry.map { Pair(it.key, it.value) }
					}// we sum up all counters of widgets which do not belong to the app package to prioritize app targets
				.groupBy { (_,countVal) -> countVal }.let {
				it.listOfSmallest()?.map { (w,_) -> w }?.let { leastInState: List<Widget> ->
					// determine the subset of widgets which were least interacted with
					// if multiple widgets clicked with same frequency, choose the one least clicked over all states
					if (leastInState.size > 1) {
						leastInState.groupBy { counter.widgetCnt(it.uid) }.listOfSmallest()
					} else leastInState
				}
			}
					?: emptyList()
		}
		// no valid candidates -> go back to previous state
		if(candidates.isEmpty()){
			println("RANDOM: Back, reason - nothing (non-blacklisted) interactable to click")
			eContext.pressBack()
		} else candidates.chooseRandomly()
	}

	private fun chooseRandomly(): AbstractExplorationAction{
		return currentState.actionableWidgets.chooseRandomly()
	}

	protected open fun chooseRandomWidget(): AbstractExplorationAction {
		return if (biased)
			chooseBiased()
		else
			chooseRandomly()
	}

	protected open fun chooseActionForWidget(chosenWidget: Widget): AbstractExplorationAction {
		var widget = chosenWidget

		while (!chosenWidget.canBeActedUpon) {
			widget = currentState.widgets.first { it.id == chosenWidget.parentId }
		}

		val actionList: MutableList<AbstractExplorationAction> = mutableListOf()

		if (widget.longClickable){    // lower probability of longClick if click is possible as it is more probable progressing the exploration
			if(widget.clickable) {
				if (random.nextInt(100) > 55) actionList.add(widget.longClick())
			}else 	actionList.add(widget.longClick())
		}

		if (widget.clickable)
			actionList.add(widget.click())

		if (widget.checked != null)
			actionList.add(widget.click())

		// TODO: Currently is doing a normal click. Replace for the swipe action (bellow)
		if (widget.scrollable)
			actionList.add(widget.click())

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

	override fun chooseAction(): AbstractExplorationAction {
		// Repeat previous action is last action was to click on a runtime permission dialog
		if (mustRepeatLastAction())
			return repeatLastAction()

		return chooseRandomWidget()
	}
}
