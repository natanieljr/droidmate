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

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.droidmate.debug.debugT
import org.droidmate.debug.nullableDebugT
import org.droidmate.exploration.statemodel.ActionResult
import org.droidmate.exploration.statemodel.StateData
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.AbstractContext
import org.droidmate.exploration.StrategySelector
import org.slf4j.LoggerFactory

/**
 * Exploration strategy pool that selects an exploration for a pool
 * of possible strategies based on their fitness for the solution.
 *
 * @author Nataniel P. Borges Jr.
 */
@Suppress("MemberVisibilityCanBePrivate")
class ExplorationStrategyPool(receivedStrategies: List<ISelectableExplorationStrategy>,
                              private val selectors: List<StrategySelector>,
                              private val memory: AbstractContext) : IExplorationStrategy, IControlObserver {

	companion object {
		@JvmStatic
		private val logger = LoggerFactory.getLogger(ExplorationStrategyPool::class.java)
	}

	// region properties

	/**
	 * List of installed strategies
	 */
	private val strategies: MutableList<ISelectableExplorationStrategy> = mutableListOf()

	/**
	 * Strategy which is currently active
	 */
	private var activeStrategy: ISelectableExplorationStrategy? = null

	/**
	 * Number of elapsed actionTrace
	 */
	private var actionNr: Int = 0

	/**
	 * Are all widgets blacklisted
	 */
	private var allWidgetsBlackListed = false

	val size: Int
		get() = this.strategies.size

	// endregion

	// region control handling

	/**
	 * Checks if the execution control is with the strategy pool or with an internal strategy
	 *
	 * @return If the strategy pool is responsible for choosing a new exploration strategy
	 */
	private fun hasControl(): Boolean {
		return this.activeStrategy == null
	}

	/**
	 * Givers control to an internal exploration strategy given the current UI
	 */
	private fun handleControl() {
		ExplorationStrategyPool.logger.debug("Attempting to handle control to exploration strategy")
		assert(this.hasControl())
		this.activeStrategy = this.selectStrategy()

		assert(!this.hasControl())
		ExplorationStrategyPool.logger.debug("Control handled to strategy ${this.activeStrategy!!}")
	}

	/**
	 * Selects an exploration strategy to [handle control to][handleControl], given the [current UI][StateData].
	 * The selected strategy is the one with best fitness.
	 *
	 * If more than one exploration strategies have the same fitness, choose the first one.
	 *
	 * @return Exploration strategy with highest fitness.
	 */
	private fun selectStrategy(): ISelectableExplorationStrategy {
		ExplorationStrategyPool.logger.debug("Selecting best strategy.")
		val mem = this.memory
		val pool = this
		val bestStrategy = debugT("strategy selection time",
				{
					/*
					selectors
					.sortedBy { it.priority }
					.mapNotNull { it -> nullableDebugT("decision time [${it.priority}]", { it.selector(this.memory, this, it.bundle) } ) }
					.first()
					*/
					runBlocking {
						selectors
								.map { Pair(it.priority, async { nullableDebugT("decision time [${it.priority}]", { it.selector(mem, pool, it.bundle) } ) } ) }
								.sortedBy { it.first }
								.first { it.second.await() != null }
								.also { println("best: ${it.first}") }
								.second.await()
					}
				} )

		ExplorationStrategyPool.logger.debug("Best strategy is $bestStrategy.")

		return bestStrategy!!
	}

	override fun takeControl(strategy: ISelectableExplorationStrategy) {
		ExplorationStrategyPool.logger.debug("Receiving back control from strategy $strategy")
		assert(this.strategies.contains(strategy))
		this.activeStrategy = null
	}

	// endregion

	// region initialization


	init {
		receivedStrategies.forEach { this.registerStrategy(it) }
	}

	fun registerStrategy(strategy: ISelectableExplorationStrategy): Boolean {
		ExplorationStrategyPool.logger.info("Registering strategy $strategy.")

		if (this.strategies.contains(strategy)) {
			ExplorationStrategyPool.logger.warn("Strategy already registered, skipping.")
			return false
		}

		strategy.registerListener(this)
		strategy.initialize(this.memory)
		this.strategies.add(strategy)

		return true
	}

	//endregion

	/**
	 * Notify the internal strategies to update their state
	 */
	private fun updateStrategiesState(record: ActionResult) {
		for (strategy in this.strategies)
			strategy.updateState(this.actionNr, record)
	}

	override fun update(record: ActionResult) {
		this.actionNr++

		this.updateStrategiesState(record)
	}

	override fun decide(result: ActionResult): ExplorationAction {

		logger.debug("decide($result)")

		assert(result.successful)

		logger.debug("pool decide")
		assert(!this.strategies.isEmpty())

		if (this.hasControl())
			this.handleControl()
		else
			logger.debug("Control is currently with strategy ${this.activeStrategy}")

		val selectedAction = this.activeStrategy!!.decide()

		logger.info("(${this.memory.getSize()}) $selectedAction")

		return selectedAction
	}

	override fun notifyAllWidgetsBlacklisted() {
		this.allWidgetsBlackListed = true
	}

	override fun onTargetFound(strategy: ISelectableExplorationStrategy, targetWidget: ITargetWidget,
	                           result: ActionResult) {
		this.strategies.forEach { it.onTargetFound(strategy, targetWidget, result) }
	}

	fun clear() {
		this.strategies.clear()
		this.activeStrategy = null
		this.actionNr = 0
		this.allWidgetsBlackListed = false
	}

	fun <R> getFirstInstanceOf(klass: Class<R>): R?{
		return strategies
				.filterIsInstance(klass)
				.firstOrNull()
	}
}
