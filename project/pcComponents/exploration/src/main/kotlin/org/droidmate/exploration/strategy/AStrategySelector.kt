package org.droidmate.exploration.strategy

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.strategy.manual.getLogger
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger

abstract class AStrategySelector : IActionSelector {
	override val log: Logger = getLogger()

	protected abstract suspend fun<M: AbstractModel<S, W>,S: State<W>,W: Widget> selectStrategy(
		eContext: ExplorationContext<M, S, W>
	): AExplorationStrategy

	override suspend fun<M: AbstractModel<S, W>,S: State<W>,W: Widget> nextAction(
		eContext: ExplorationContext<M, S, W>
	): ExplorationAction =
		selectStrategy(eContext).nextAction(eContext)
}