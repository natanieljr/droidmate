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

import org.droidmate.configuration.Configuration
import org.droidmate.device.datatypes.EmptyGuiState
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

    private fun mustRepeatLastAction(widgetContext: WidgetContext): Boolean {
        if (!this.memory.isEmpty()) {
            val lastContext = this.memory.getLastAction().widgetContext
            val lastState = lastContext.guiState

            // Last state was runtime permission
            return (lastState.isRequestRuntimePermissionDialogBox) &&
                    // Has last action
                    this.memory.lastWidgetInfo !is EmptyWidgetInfo &&
                    // Has a state that is not a runtime permission
                    this.memory.getRecords()
                            .filterNot { it.widgetContext.guiState is EmptyGuiState }
                            .filterNot { it.widgetContext.guiState.isRequestRuntimePermissionDialogBox }
                            .isNotEmpty() &&
                    // Can re-execute the same action
                    this.getAvailableWidgets(widgetContext)
                            .any { p -> p.widget.isEquivalent(this.memory.lastWidgetInfo.widget) }
        }

        return false
    }

    private fun repeatLastAction(): ExplorationAction {
        val actionHistory = this.memory.getRecords()

        val lastActionBeforePermission = actionHistory
                .last { !(it.widgetContext.guiState.isRequestRuntimePermissionDialogBox || it.widgetContext.guiState is EmptyGuiState) }

        return lastActionBeforePermission.action
    }

    open protected fun getAvailableWidgets(widgetContext: WidgetContext): List<WidgetInfo> {
        return widgetContext.getActionableWidgetsInclChildren()//.actionableWidgetsInfo
                .filterNot { it.blackListed }
    }

    open protected fun chooseRandomWidget(widgetContext: WidgetContext): ExplorationAction {
        val availableWidgets = this.getAvailableWidgets(widgetContext)
        val minActedUponCount = availableWidgets
                .map { it.actedUponCount }
                .min()

        val candidates = availableWidgets
                .filter { it.actedUponCount == minActedUponCount }

        assert(candidates.isNotEmpty())

        val chosenWidgetInfo = candidates[random.nextInt(candidates.size)]

        this.memory.lastWidgetInfo = chosenWidgetInfo
        return chooseActionForWidget(chosenWidgetInfo)
    }

    open protected fun chooseActionForWidget(chosenWidgetInfo: WidgetInfo): ExplorationAction {
        var chosenWidget = chosenWidgetInfo.widget

        while (!chosenWidget.canBeActedUpon()) {
            chosenWidget = chosenWidget.parent!!
        }

        val actionList: MutableList<ExplorationAction> = ArrayList()

        if (chosenWidget.longClickable)
            actionList.add(ExplorationAction.newWidgetExplorationAction(chosenWidget, true))

        if (chosenWidget.clickable)
            actionList.add(ExplorationAction.newWidgetExplorationAction(chosenWidget))

        if (chosenWidget.checkable)
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

        chosenWidgetInfo.actedUponCount++

        logger.debug("Chosen widget info: $chosenWidgetInfo")

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
        if (mustRepeatLastAction(widgetContext))
            return repeatLastAction()

        return chooseRandomWidget(widgetContext)
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
