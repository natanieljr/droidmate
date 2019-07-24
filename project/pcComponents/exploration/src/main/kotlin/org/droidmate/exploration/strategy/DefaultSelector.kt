package org.droidmate.exploration.strategy

import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

object DefaultSelector {
	/**
	 * (dummy) Selector to synchronize statement coverage
	 */
	fun statementCoverage(prio: Int) = object : AStrategySelector() {
		override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> selectStrategy(eContext: ExplorationContext<M, S, W>): AExplorationStrategy {
			throw RuntimeException("this function should never be invoked")
		}

		override fun getPriority(): Int = prio

		override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean {
			eContext.findWatcher { it is StatementCoverageMF }?.join()
			return false
		}
	}
}