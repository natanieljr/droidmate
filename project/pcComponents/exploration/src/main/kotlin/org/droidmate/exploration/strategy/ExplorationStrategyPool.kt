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

import kotlinx.coroutines.*
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.explorationModel.interaction.ActionResult
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.StrategySelector
import org.droidmate.explorationModel.debugOutput
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.lang.Math.max

/**
 * Exploration strategy pool that selects an exploration for a pool
 * of possible strategies based on their fitness for the solution.
 *
 * @author Nataniel P. Borges Jr.
 */
class ExplorationStrategyPool(receivedStrategies: List<ISelectableExplorationStrategy>,
                              private val selectors: List<StrategySelector>,
                              private val memory: ExplorationContext) : IExplorationStrategy {

	companion object {
		@JvmStatic
		private val logger by lazy { LoggerFactory.getLogger(ExplorationStrategyPool::class.java) }
	}

	// region properties

	/**
	 * List of installed strategies
	 */
	private val strategies: MutableList<ISelectableExplorationStrategy> = mutableListOf()

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
	private fun selectStrategy(): ISelectableExplorationStrategy {
		ExplorationStrategyPool.logger.debug("Selecting best strategy.")
		val mem = this.memory
		val pool = this
		val bestStrategy =
			runBlocking(selectorThreadPool){
				selectors
						.sortedBy { it.priority }
						.map { Pair(it, async(coroutineContext+ CoroutineName("select-${it.description}")) { it.selector(mem, pool, it.bundle)
							 }) }
						.first{ it.second.await() != null }
			}


		ExplorationStrategyPool.logger.info("Best strategy is (${bestStrategy.first.description}->${bestStrategy.second.getCompleted()?.uniqueStrategyName})")

		// notify
		bestStrategy.first.onSelected?.invoke(this.memory)

		return bestStrategy.second.getCompleted()!!
	}

	// region initialization


	init {
		receivedStrategies.forEach { this.registerStrategy(it) }
	}

	@Suppress("MemberVisibilityCanBePrivate")
	fun registerStrategy(strategy: ISelectableExplorationStrategy): Boolean {
		ExplorationStrategyPool.logger.info("Registering strategy $strategy.")

		if (this.strategies.contains(strategy)) {
			ExplorationStrategyPool.logger.warn("Strategy already registered, skipping.")
			return false
		}

		strategy.initialize(this.memory)
		this.strategies.add(strategy)

		return true
	}

	//endregion

	override suspend fun decide(result: ActionResult): ExplorationAction {

		if(debugOutput)
			logger.debug("ActionResult: ${result.action} => #widgets=${result.guiSnapshot} ,exception = ${result.exception}")

//		assert(result.successful) //FIXME no assert, instead use error handling/e.g. re-fetching or notify strategy of failed action

		assert(!this.strategies.isEmpty())

		val activeStrategy = this.selectStrategy()
		logger.debug("Control is currently with strategy $activeStrategy")

		val selectedAction = activeStrategy.decide()

		logger.info("(${this.memory.getSize()}) $selectedAction [id=${selectedAction.id}]")

		return selectedAction
	}

	override fun close(){
		selectorThreadPool.close()
	}

	fun <R> getFirstInstanceOf(klass: Class<R>): R?{
		return strategies
				.filterIsInstance(klass)
				.firstOrNull()
	}

	fun getByName(className: String) = strategies.firstOrNull { it.uniqueStrategyName == className } ?: throw IllegalStateException("no strategy $className in the poll, register it first or call 'getOrCreate' instead")

	fun getOrCreate(className: String, createStrategy: ()->ISelectableExplorationStrategy): ISelectableExplorationStrategy{
		val strategy = strategies.firstOrNull { it.uniqueStrategyName == className }
		return strategy ?: createStrategy().also{
			check(it.uniqueStrategyName==className) {"ERROR your created strategy does not correspond to the requested name $className"}
			it.initialize(memory); strategies.add(it)  }
	}
}
