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
package org.droidmate.exploration.strategy

import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.IExplorationActionResultObserver
import org.droidmate.exploration.actions.IExplorationActionRunResult
import org.droidmate.exploration.strategy.widget.AbstractWidgetStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Abstract class which contains common functionality for all exploration
 * strategies inside the strategy pool. Recommended to override this class
 * instead of the interface.

 * @author Nataniel P. Borges Jr.
 */
@SuppressWarnings("WeakerAccess")
abstract class AbstractStrategy : ISelectableExplorationStrategy, IExplorationActionResultObserver {
    /**
     * List of observers to be notified when widgets get blacklisted or
     * when a target is found
     */
    private val listeners = ArrayList<IControlObserver>()

    /**
     * Internal memory of the strategy. Syncronized with exploration memory upon initialization.
     */
    protected var memory: Memory = Memory()
        private set

    /**
     * Number of the current action being performed
     */
    protected var actionNr: Int = 0
        private set

    /**
     * Return the execution control to the [listeners]
     */
    private fun handleControl() {
        for (listener in this.listeners)
            listener.takeControl(this)
    }

    /**
     * Check if this is the first time an action will be performed ([memory] is empty)
     * @return If this is the first exploration action or not
     */
    internal fun firstDecisionIsBeingMade(): Boolean {
        return memory.isEmpty()
    }

    /**
     * Check if last performed action in the [memory] was to reset the app
     * @return If the last action was a reset
     */
    internal fun lastActionWasOfType(type: ExplorationType): Boolean {
        val lastAction = this.memory.getLastAction()
        return lastAction != null && lastAction.type == type
    }

    /**
     * Notify all [listeners] that all widgets on this screen are blacklisted
     */
    protected fun notifyAllWidgetsBlacklisted() {
        this.listeners.forEach { listener -> listener.notifyAllWidgetsBlacklisted() }
    }

    /**
     * Notify all [listeners] that an exploration target has been found
     *
     * @param targetWidget Widget that has been found
     * @param result Exploration action that triggered the target
     */
    protected fun notifyTargetFound(targetWidget: ITargetWidget, result: IExplorationActionRunResult) {
        this.listeners.forEach { listener -> listener.onTargetFound(this, targetWidget, result) }
    }

    override fun updateState(actionNr: Int) {
        this.actionNr = actionNr
    }

    override fun initialize(memory: Memory) {
        this.memory = memory
    }

    override fun registerListener(listener: IControlObserver) {
        this.listeners.add(listener)
    }

    override fun decide(widgetContext: WidgetContext): ExplorationAction {
        val action = this.internalDecide(widgetContext)

        if (!this.mustPerformMoreActions(widgetContext))
            this.handleControl()

        action.registerObserver(this)
        return action
    }

    override fun onTargetFound(strategy: ISelectableExplorationStrategy, satisfiedWidget: ITargetWidget,
                               result: IExplorationActionRunResult) {
        this.memory.getRecords()[0]
        // By default does nothing
    }

    /**
     * Notification fired when the action has been executed on the device, include
     * [results of executing exploration action][result]
     *
     * @return If observer should be unassociated with the action (always true)
     */
    override fun notifyActionExecuted(result: IExplorationActionRunResult): Boolean {
        this.memory.addResultToLastAction(result)

        return true
    }

    override fun equals(other: Any?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun hashCode(): Int {
        throw UnsupportedOperationException(this.javaClass.toString())
    }

    /**
     * Defines if the exploration action will perform more actions or if it will return the
     * execution control to the listeners.
     *
     * Example of strategies which would require multiple actions:
     * - Login
     * - Register
     *
     * @return If the strategy has to perform more actions
     */
    abstract fun mustPerformMoreActions(widgetContext: WidgetContext): Boolean

    /**
     * Selects an action to be executed based on the [current widget context][widgetContext]
     */
    abstract fun internalDecide(widgetContext: WidgetContext): ExplorationAction

    companion object {
        val logger: Logger = LoggerFactory.getLogger(AbstractWidgetStrategy::class.java)

        val VALID_WIDGETS = ResourceManager.getResourceAsStringList("validWidgets.txt")
    }
}
