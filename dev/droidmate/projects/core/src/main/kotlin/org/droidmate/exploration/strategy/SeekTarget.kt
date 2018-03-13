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
package org.droidmate.exploration.strategy

import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.strategy.widget.RandomWidget
import org.droidmate.misc.isEquivalentIgnoreLocation

/**
 * Exploration strategy that seeks targets. Has a higher priority than the normal widget explorations
 * only when the target is on sight.
 *
 * @param target Target widget
 * @param appPackageName Application which contains the widget
 *
 * @author Nataniel P. Borges Jr.
 */
class SeekTarget private constructor(private val target: ITargetWidget, private val appPackageName: String) : RandomWidget(0) {
    /**
     * Target which was acquired and acted upon (used to monitor DroidMate's callback)
     */
    private var acquiredTarget: ITargetWidget = DummyTarget()

    /**
     * Return the widgets which can be interacted with. In this strategy only the target widget or one of its
     * dependencies can be interacted with.
     *
     * @return List of widgets which are meaningful targets
     */
    override fun getAvailableWidgets(widgetContext: WidgetContext): List<WidgetInfo> {
        val widgetInfo = super.getAvailableWidgets(widgetContext)

        val toSatisfy = this.target.getNextWidgetsCanSatisfy()

        return widgetInfo.filter { w ->
            toSatisfy.any { it.widget.isEquivalentIgnoreLocation(w.widget) }
        }
    }

    override fun getFitness(widgetContext: WidgetContext): StrategyPriority {
        // If it is not yet satisfied and can handle action
        if (this.getAvailableWidgets(widgetContext).isNotEmpty())
            return StrategyPriority.SPECIFIC_WIDGET

        // Otherwise does nothing
        return StrategyPriority.NONE
    }

    override fun onTargetFound(strategy: ISelectableExplorationStrategy, satisfiedWidget: ITargetWidget,
                               result: IMemoryRecord) {
        if ((this == strategy) || (strategy !is SeekTarget))
            return

        this.target.trySatisfyWidgetOrDependency(satisfiedWidget)
    }

    override fun chooseActionForWidget(chosenWidgetInfo: WidgetInfo): ExplorationAction {
        this.acquiredTarget = this.target.getTarget(chosenWidgetInfo)

        return super.chooseActionForWidget(chosenWidgetInfo)
    }

    override fun updateState(actionNr: Int, record: IMemoryRecord) {
        super.updateState(actionNr, record)

        if (this.acquiredTarget !is DummyTarget) {
            this.acquiredTarget.satisfy()
            this.notifyTargetFound(this.acquiredTarget, record)

            this.acquiredTarget = DummyTarget()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is SeekTarget)
            return false

        return other.target == this.target && other.appPackageName == this.appPackageName
    }

    override fun hashCode(): Int {
        return this.appPackageName.hashCode()
    }

    override fun toString(): String {
        return "${this.javaClass}\tTarget: ${this.target}"
    }

    companion object {
        /**
         * Creates one exploration strategy per [target which must be located][target] on a
         * [specific application name][appPackageName]
         *
         * @return List of exploration strategies (1 strategy per target)
         */
        fun build(targets: List<ITargetWidget>, appPackageName: String): List<ISelectableExplorationStrategy> {
            val strategies = ArrayList<ISelectableExplorationStrategy>()
            targets.forEach { p -> strategies.add(SeekTarget(p, appPackageName)) }

            return strategies
        }
    }

}
