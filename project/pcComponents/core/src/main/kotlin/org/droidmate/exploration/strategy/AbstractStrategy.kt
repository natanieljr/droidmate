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

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.ActionResult
import org.droidmate.explorationModel.interaction.State
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.strategy.widget.ExplorationStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Abstract class which contains common functionality for all exploration
 * strategies inside the strategy pool. Recommended to override this class
 * instead of the interface.

 * @author Nataniel P. Borges Jr.
 */
abstract class AbstractStrategy : ISelectableExplorationStrategy {
	override val uniqueStrategyName: String = javaClass.simpleName
	/**
	 * List of observers to be notified when widgets get blacklisted or
	 * when a target is found
	 */
	override val listeners = ArrayList<IControlObserver>()
	/**
	 * if this parameter is true we can invoke a strategy without registering it, this should be only used for internal
	 * default strategies from our selector functions like Reset,Back etc.
	 */
	override val noContext: Boolean = false

	/**
	 * Internal context of the strategy. Synchronized with exploration context upon initialization.
	 */
	protected lateinit var eContext: ExplorationContext
		private set

	protected val currentState: State get() = eContext.getCurrentState()

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
	 * Check if this is the first time an action will be performed ([eContext] is empty)
	 * @return If this is the first exploration action or not
	 */
	internal fun firstDecisionIsBeingMade(): Boolean {
		return eContext.isEmpty()
	}

	/**
	 * Check if last performed action in the [eContext] was to reset the app
	 * @return If the last action was a reset
	 */
	internal fun lastAction(): Interaction {
		return this.eContext.getLastAction()
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
	protected fun notifyTargetFound(targetWidget: ITargetWidget, result: ActionResult) {
		this.listeners.forEach { listener -> listener.onTargetFound(this, targetWidget, result) }
	}

	/**
	 * Get action before the last one.
	 *
	 * Used by some strategies (ex: Terminate and Back) to prevent loops (ex: Reset -> Back -> Reset -> Back)
	 */
	fun getSecondLastAction(): Interaction {
		if (this.eContext.getSize() < 2)
			return Interaction.empty

		return this.eContext.explorationTrace.getActions().dropLast(1).last()
	}

	override fun updateState(actionNr: Int, record: ActionResult) {
		this.actionNr = actionNr
	}

	override fun initialize(memory: ExplorationContext) {
		this.eContext = memory
	}

	override fun registerListener(listener: IControlObserver) {
		this.listeners.add(listener)
	}

	override fun decide(): ExplorationAction {
		val action = this.internalDecide()

		this.handleControl()

		return action
	}

	override fun onTargetFound(strategy: ISelectableExplorationStrategy, satisfiedWidget: ITargetWidget,
	                           result: ActionResult) {
		// By default does nothing
	}

	override fun equals(other: Any?): Boolean {
		return (other != null) && this.javaClass == other.javaClass
	}

	override fun hashCode(): Int {
		return this.javaClass.hashCode()
	}
	override fun toString() = uniqueStrategyName

	/**
	 * Selects an action to be executed based on the [current widget context][currentState]
	 */
	abstract fun internalDecide(): ExplorationAction

	companion object {
		val logger: Logger by lazy { LoggerFactory.getLogger(ExplorationStrategy::class.java) }

		val VALID_WIDGETS = ResourceManager.getResourceAsStringList("validWidgets.txt")
	}
}
