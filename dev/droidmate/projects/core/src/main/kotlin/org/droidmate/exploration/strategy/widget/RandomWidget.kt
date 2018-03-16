// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018 Konrad Jamrozik
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
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org
package org.droidmate.exploration.strategy.widget

import org.droidmate.configuration.Configuration
import org.droidmate.device.datatypes.Widget
import org.droidmate.device.datatypes.statemodel.emptyId
import org.droidmate.device.datatypes.statemodel.features.ActionCounterMF
import org.droidmate.device.datatypes.statemodel.features.listOfSmallest
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.strategy.*
import java.util.*
import kotlin.collections.ArrayList

/**
 * Exploration strategy that select a (pseudo-)random widget from the screen.
 */
open class RandomWidget protected constructor(randomSeed: Long,
                                              private val priority: StrategyPriority = StrategyPriority.PURELY_RANDOM_WIDGET) : Explore() {
    protected val random = Random(randomSeed)

    private fun mustRepeatLastAction(): Boolean {
        if (!this.memory.isEmpty()) {

            // Last state was runtime permission
            return (this.memory.getCurrentState().isRequestRuntimePermissionDialogBox) &&
                    // Has last action
                    this.memory.lastTarget != null &&
                    // Has a state that is not a runtime permission
                    this.memory.getRecords().getStates()
                            .filterNot { it.stateId == emptyId }
                            .filterNot { it.isRequestRuntimePermissionDialogBox }
                            .isNotEmpty() &&
                    // Can re-execute the same action
                    memory.getCurrentState().actionableWidgets
                            .any { p -> memory.lastTarget?.let{ p.isEquivalent(it)}?: false }
        }

        return false
    }

    private fun repeatLastAction(): ExplorationAction {
        val lastActionBeforePermission = this.memory.getCurrentState().let{
                !(it.isRequestRuntimePermissionDialogBox || it.stateId == emptyId) }

//        return lastActionBeforePermission.action
        TODO("extract WidgetId from recorded trace and look it up in current Context to choose as target")
    }

    open protected fun getAvailableWidgets(widgetContext: WidgetContext): List<Widget> {
        return widgetContext.getActionableWidgetsInclChildren()//.actionableWidgetsInfo
//                .filterNot { it.blackListed } //TODO
    }

	protected open fun chooseRandomWidget(): ExplorationAction {
		val candidates = memory.watcher.find{ it is ActionCounterMF }?.let { counter -> counter as ActionCounterMF
			// for each widget in this state the number of interactions
			counter.numExplored(memory.getCurrentState()).entries.groupBy { it.value }.let {
				it.listOfSmallest()?.map { it.key }?.let{ leastInState:List<Widget> -> // determine the subset of widgets which were least interacted with
					// if multiple widgets clicked with same frequency, choose the one least clicked over all states
					if(leastInState.size>1){
						leastInState.groupBy { counter.widgetCnt(it.uid) }.listOfSmallest()
					}else leastInState
				}
			}
		}?: memory.getCurrentState().actionableWidgets

		assert(candidates.isNotEmpty())

		val chosenWidget = candidates[random.nextInt(candidates.size)]

		this.memory.lastTarget = chosenWidget
		return chooseActionForWidget(chosenWidget)
	}

    protected open fun chooseActionForWidget(chosenWidget: Widget): ExplorationAction {

        while (!chosenWidget.canBeActedUpon()) {
	        memory.getCurrentState().widgets.find { it.id == chosenWidget.parentId }
        }

        val actionList: MutableList<ExplorationAction> = ArrayList()

        if (chosenWidget.longClickable)
            actionList.add(ExplorationAction.newWidgetExplorationAction(chosenWidget, false))

        if (chosenWidget.clickable)
            actionList.add(ExplorationAction.newWidgetExplorationAction(chosenWidget))

        if (chosenWidget.checked!=null)
            actionList.add(ExplorationAction.newWidgetExplorationAction(chosenWidget))

        // TODO: Currently is doing a normal click. Replace for the swipe action (bellow)
        if (chosenWidget.scrollable)
            actionList.add(ExplorationAction.newWidgetExplorationAction(chosenWidget))

        /*if (chosenWidget.scrollable) {
            actionList.add(ExplorationAction.newWidgetExplorationAction(chosenWidget, 0, guiActionSwipe_right))
            actionList.add(ExplorationAction.newWidgetExplorationAction(chosenWidget, 0, guiActionSwipe_up))
            actionList.add(ExplorationAction.newWidgetExplorationAction(chosenWidget, 0, guiActionSwipe_left))
            actionList.add(ExplorationAction.newWidgetExplorationAction(chosenWidget, 0, guiActionSwipe_down))
        }*/

//        chosenWidget.actedUponCount++ //TODO this has to be implemented as Model Feature

        logger.debug("Chosen widget info: $chosenWidget")

        val maxVal = actionList.size
        assert(maxVal > 0)
        val randomIdx = random.nextInt(maxVal)
        return actionList[randomIdx]
    }

    override fun getFitness(widgetContext: WidgetContext): StrategyPriority {
        // Arbitrary established
        return this.priority
    }

    override fun chooseAction(widgetContext: WidgetContext): ExplorationAction {
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

    companion object {
        /**
         * Creates a new exploration strategy instance
         */
        fun build(cfg: Configuration): ISelectableExplorationStrategy {
            return RandomWidget(cfg.randomSeed.toLong())
        }
    }
}
