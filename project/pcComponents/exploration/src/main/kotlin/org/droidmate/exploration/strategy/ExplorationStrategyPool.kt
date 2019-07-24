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

import com.natpryce.konfig.Configuration
import kotlinx.coroutines.*
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.lang.Math.max

/**
 * Exploration strategy pool that selects an exploration for a pool
 * of possible strategies based on their fitness for the solution.
 *
 * @author Nataniel P. Borges Jr.
 */
open class ExplorationStrategyPool(
	receivedStrategies: List<AExplorationStrategy>,
	private val selectors: List<AStrategySelector>
) {

	companion object {
		@JvmStatic
		private val logger by lazy { LoggerFactory.getLogger(ExplorationStrategyPool::class.java) }
	}

	// region properties

	/**
	 * List of installed strategies
	 */
	private val strategies = mutableListOf<AExplorationStrategy>()

	val size: Int
		get() = this.strategies.size

	// endregion

	private val selectorThreadPool = newFixedThreadPoolContext (max(Runtime.getRuntime().availableProcessors()-1,1),name="SelectorsThread")
	/**
	 * Selects an exploration strategy to execute, given the current UI state.
	 * The selected strategy is the one with best fitness.
	 *
	 * If more than one exploration strategies have the same fitness, choose the first one.
	 *
	 * @return Exploration strategy with highest fitness.
	 */
	private suspend fun<M: AbstractModel<S,W>,S: State<W>,W:Widget> computeNextAction(
		eContext: ExplorationContext<M, S, W>
	): ExplorationAction {
		ExplorationStrategyPool.logger.debug("Selecting best strategy.")

		val bestStrategy =
			withContext(selectorThreadPool){
				// we check all strategies and selectors (sorted by priority) if they have an action to offer
				strategies.plus(selectors)
						.sortedBy { it.getPriority() }
						.map { Pair(it, async(coroutineContext+ CoroutineName("select-${it.uniqueStrategyName}")) { it.hasNext(eContext)
							 }) }
						.first{ it.second.await() }.first // choose the IActionSelector with the best priority (lowest value) which has a next action
			}

		ExplorationStrategyPool.logger.info("Best strategy is: ${bestStrategy.uniqueStrategyName}")

		return bestStrategy.nextAction(eContext)
	}

	// region initialization


	init {
		receivedStrategies.forEach { this.registerStrategy(it) }
	}

	fun<M: AbstractModel<S,W>,S: State<W>,W:Widget> init(
		cfg: Configuration,
		eContext: ExplorationContext<M, S, W>
	) {
		selectors.forEach {
			it.initialize(cfg)
			it.initialize(eContext)
		}
		strategies.forEach {
			it.initialize(cfg)
			it.initialize(eContext)
		}
	}

	@Suppress("MemberVisibilityCanBePrivate")
	fun registerStrategy(strategy: AExplorationStrategy): Boolean {
		ExplorationStrategyPool.logger.info("Registering strategy $strategy.")

		if (this.strategies.contains(strategy)) {
			ExplorationStrategyPool.logger.warn("Strategy already registered, skipping.")
			return false
		}

		this.strategies.add(strategy)

		return true
	}

	//endregion

	open suspend fun<M: AbstractModel<S, W>,S: State<W>,W: Widget> nextAction(eContext: ExplorationContext<M, S, W>): ExplorationAction {
		assert(this.strategies.isNotEmpty())

		val selectedAction = this.computeNextAction(eContext)
		logger.info("(${eContext.getSize()}) $selectedAction [id=${selectedAction.id}]")

		return selectedAction
	}

	open fun close(){
		selectorThreadPool.close()
	}

	fun <R> getFirstInstanceOf(klass: Class<R>): R? {
		return strategies
			.filterIsInstance(klass)
			.firstOrNull()
	}

	fun getByName(className: String) =
		strategies.firstOrNull { it.uniqueStrategyName == className }
			?: throw IllegalStateException("no strategy $className in the poll, register it first or call 'getOrCreate' instead")

	fun getOrCreate(className: String, createStrategy: ()-> AExplorationStrategy): AExplorationStrategy {
		val strategy = strategies.firstOrNull { it.uniqueStrategyName == className }

		return strategy ?: createStrategy()
			.also {
				check(it.uniqueStrategyName == className) { "ERROR your created strategy does not correspond to the requested name $className" }
				strategies.add(it)
			}
	}
}
