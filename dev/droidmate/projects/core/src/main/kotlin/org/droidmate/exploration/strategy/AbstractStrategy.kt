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

import org.droidmate.device.datatypes.statemodel.ActionData
import org.droidmate.device.datatypes.statemodel.ActionResult
import org.droidmate.device.datatypes.statemodel.StateData
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.data_aggregators.AbstractContext
import org.droidmate.exploration.strategy.widget.Explore
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Abstract class which contains common functionality for all exploration
 * strategies inside the strategy pool. Recommended to override this class
 * instead of the interface.

 * @author Nataniel P. Borges Jr.
 */
abstract class AbstractStrategy : ISelectableExplorationStrategy {
    /**
     * List of observers to be notified when widgets get blacklisted or
     * when a target is found
     */
    private val listeners = ArrayList<IControlObserver>()

    /**
     * Internal memory of the strategy. Syncronized with exploration memory upon initialization.
     */
    protected lateinit var memory: AbstractContext
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
    internal fun lastAction(): ActionData {
        return this.memory.getLastAction()
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
     * @param targetWidget OldWidget that has been found
     * @param result Exploration action that triggered the target
     */
    protected fun notifyTargetFound(targetWidget: ITargetWidget, result: ActionResult) {
        this.listeners.forEach { listener -> listener.onTargetFound(this, targetWidget, result) }
    }

    override fun updateState(actionNr: Int, record: ActionResult) {
        this.actionNr = actionNr
    }

    override fun initialize(memory: AbstractContext) {
        this.memory = memory
    }

    override fun registerListener(listener: IControlObserver) {
        this.listeners.add(listener)
    }

    override fun decide(currentState: StateData): ExplorationAction {
        val action = this.internalDecide(currentState)

        if (!this.mustPerformMoreActions(currentState))
            this.handleControl()

        return action
    }

    override fun onTargetFound(strategy: ISelectableExplorationStrategy, satisfiedWidget: ITargetWidget,
                               result: ActionResult) {
        // By default does nothing
    }


    override fun equals(other: Any?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun hashCode(): Int {
        throw UnsupportedOperationException(this.javaClass.toString())
    }

    /**
     * Defines if the exploration action will perform more actionTrace or if it will return the
     * execution control to the listeners.
     *
     * Example of strategies which would require multiple actionTrace:
     * - Login
     * - Register
     *
     * @return If the strategy has to perform more actionTrace
     */
    abstract fun mustPerformMoreActions(currentState: StateData): Boolean

    /**
     * Selects an action to be executed based on the [current widget context][currentState]
     */
    abstract fun internalDecide(currentState: StateData): ExplorationAction

    companion object {
        val logger: Logger = LoggerFactory.getLogger(Explore::class.java)

        val VALID_WIDGETS = ResourceManager.getResourceAsStringList("validWidgets.txt")
    }
}
