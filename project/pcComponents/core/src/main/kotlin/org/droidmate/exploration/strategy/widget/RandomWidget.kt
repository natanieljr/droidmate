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
import org.droidmate.configuration.ConfigProperties.Strategies.Parameters
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.misc.debugT
import org.droidmate.deviceInterface.guimodel.ExplorationAction
import org.droidmate.deviceInterface.guimodel.Swipe
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.*
import org.droidmate.explorationModel.Widget
import org.droidmate.explorationModel.config.emptyId
import org.droidmate.exploration.modelFeatures.ActionCounterMF
import org.droidmate.exploration.modelFeatures.BlackListMF
import org.droidmate.exploration.modelFeatures.listOfSmallest
import java.util.Random

/**
 * Exploration strategy that select a (pseudo-)random widget from the screen.
 *
 * @param randomSeed Random exploration seed
 * @param biased Prioritise UI elements which have not yet been interacted with, instead of plain random
 * @param randomScroll Trigger not only clicks and long clicks, but also scroll actions when the UI element supports it
 */
open class RandomWidget @JvmOverloads constructor(private val randomSeed: Long,
												  private val biased: Boolean = true,
												  private val randomScroll: Boolean = true) : ExplorationStrategy() {

	protected var random = Random(randomSeed)
		private set

	override fun initialize(memory: ExplorationContext) {
		super.initialize(memory)
		random = Random(randomSeed)
	}

	/**
	 * Creates a new exploration strategy instance using the [configured random seed][cfg]
	 */
	constructor(cfg: ConfigurationWrapper) : this(cfg.randomSeed, cfg[Parameters.biasedRandom], cfg[Parameters.randomScroll])

	@Suppress("MemberVisibilityCanBePrivate")
	protected val counter: ActionCounterMF by lazy { eContext.getOrCreateWatcher<ActionCounterMF>() }
	private val blackList: BlackListMF by lazy { eContext.getOrCreateWatcher<BlackListMF>() }

	private fun mustRepeatLastAction(): Boolean {
		if (!this.eContext.isEmpty()) {

			// Last state was runtime permission
			return (currentState.isRequestRuntimePermissionDialogBox) &&
					// Has last action
					this.eContext.lastTarget != null &&
					// Has a state that is not a runtime permission
					eContext.getModel().let { runBlocking { it.getStates() } }
							.filterNot { it.stateId == emptyId && it.isRequestRuntimePermissionDialogBox }
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
	 * @param tInState the threshold to consider the widget blacklisted within the current state eContext
	 * @param tOverall the threshold to consider the widget blacklisted over all states
	 */
	protected open suspend fun excludeBlacklisted(candidates: List<Widget>, tInState: Int = 1, tOverall: Int = 2, block: (listedInsState: List<Widget>, blacklisted: List<Widget>) -> List<Widget>): List<Widget> =
			candidates.filterNot { blackList.isBlacklistedInState(it.uid, currentState.uid, tInState) }.let { noBlacklistedInState ->
				noBlacklistedInState.filterNot { blackList.isBlacklisted(it.uid, tOverall) }.let { noBlacklisted ->
					block(noBlacklistedInState, noBlacklisted)
				}
			}


	private fun List<Widget>.chooseRandomly(): ExplorationAction {
		if (this.isEmpty())
			return eContext.resetApp()
		return chooseActionForWidget(this[random.nextInt(this.size)])
	}

	open suspend fun computeCandidates(): Collection<Widget> = debugT("blacklist computation", {
		val nonCrashing = super.eContext.nonCrashingWidgets()
		excludeBlacklisted(super.eContext.nonCrashingWidgets()) { noBlacklistedInState, noBlacklisted ->
			when {
				noBlacklisted.isNotEmpty() -> noBlacklisted
				noBlacklistedInState.isNotEmpty() -> noBlacklistedInState
				else -> nonCrashing
			}
		}
	}, inMillis = true)
			.filter { it.clickable || it.longClickable || it.checked != null } // the other actions are currently not supported

	@Suppress("MemberVisibilityCanBePrivate")
	protected suspend fun getCandidates(): List<Widget> {
		return computeCandidates()
				.let { filteredCandidates ->
					// for each widget in this state the number of interactions
					counter.numExplored(currentState, filteredCandidates).entries
							.groupBy { it.key.packageName }.flatMap { (pkgName, countEntry) ->
								if (pkgName != super.eContext.apk.packageName) {
									val pkgActions = counter.pkgCount(pkgName)
									countEntry.map { Pair(it.key, pkgActions) }
								} else
									countEntry.map { Pair(it.key, it.value) }
							}// we sum up all counters of widgets which do not belong to the app package to prioritize app targets
							.groupBy { (_, countVal) -> countVal }.let { map ->
								map.listOfSmallest()?.map { (w, _) -> w }?.let { leastInState: List<Widget> ->
									// determine the subset of widgets which were least interacted with
									// if multiple widgets clicked with same frequency, choose the one least clicked over all states
									if (leastInState.size > 1) {
										leastInState.groupBy { counter.widgetCnt(it.uid) }.listOfSmallest()
									} else leastInState
								}
							}
							?: emptyList()
				}
	}

	private fun chooseBiased(): ExplorationAction = runBlocking {
		val candidates = getCandidates()
		// no valid candidates -> go back to previous state
		if (candidates.isEmpty()) {
			println("RANDOM: Back, reason - nothing (non-blacklisted) interactable to click")
			eContext.pressBack()
		} else candidates.chooseRandomly()
	}

	private fun chooseRandomly(): ExplorationAction {
		return currentState.actionableWidgets.chooseRandomly()
	}

	protected open fun chooseRandomWidget(): ExplorationAction {
		return if (biased)
			chooseBiased()
		else
			chooseRandomly()
	}

	protected open fun chooseActionForWidget(chosenWidget: Widget): ExplorationAction {
		var widget = chosenWidget

		while (!chosenWidget.canBeActedUpon) {
			widget = currentState.widgets.first { it.id == chosenWidget.parentId }
		}

		logger.debug("Chosen widget info: $widget: ${widget.canBeActedUpon}\t${widget.clickable}\t${widget.checked}\t${widget.longClickable}\t${widget.scrollable}")

		val actionList = if (randomScroll)
			widget.availableActions()
		else
			widget.availableActions().filterNot { it is Swipe }

		val maxVal = actionList.size

		assert(maxVal > 0) { "No actions can be performed on the widget $widget" }

		val randomIdx = random.nextInt(maxVal)
		return actionList[randomIdx]
	}

	override fun chooseAction(): ExplorationAction {
		if (eContext.isEmpty())
			return eContext.resetApp() // very first action -> start the app via reset
		// Repeat previous action is last action was to click on a runtime permission dialog
		if (mustRepeatLastAction())
			return repeatLastAction()

		return chooseRandomWidget()
	}
}
