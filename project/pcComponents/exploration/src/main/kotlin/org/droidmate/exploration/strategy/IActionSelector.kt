package org.droidmate.exploration.strategy

import com.natpryce.konfig.Configuration
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.strategy.manual.Logging
import org.droidmate.exploration.strategy.manual.getLogger
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger

interface IActionSelector: Logging {
	val uniqueStrategyName: String get() = "${getPriority().toString().padStart(2)}-${this::class.java.simpleName}"
	/** retrieve a logger object for this class (i.e. by calling getLogger()) */
	override val log: Logger get() = getLogger()

	/** this function is called in the exploration loop and allows to use any configuration parameter for initialization.
	 * This function is called before initialize(initialContext). */
	fun initialize(cfg: Configuration){}
	/** this function is called in the exploration loop for each app before any action is executed.
	 * This function may be used to register ModelFeatures or other context dependent properties */
	fun<M: AbstractModel<S,W>,S: State<W>,W:Widget> initialize(initialContext: ExplorationContext<M,S,W>){}
	/** this function is called in the end of an exploration for each app.
	 * This function may be used to persist any strategy custom data or for cleanup tasks */
	suspend fun tearDown(){}

	fun getPriority(): Int
	suspend fun<M: AbstractModel<S,W>,S: State<W>,W:Widget> hasNext(
		eContext: ExplorationContext<M,S,W>
	): Boolean

	/**
	 * Selects an exploration action based on the [current GUI](exploration context).
	 *
	 * When using an exploration pool, this method is only invoked if the current strategy
	 * had the highest fitness
	 *
	 * @return Exploration action to be sent to the device (has to be supported by DroidMate)
	 */
	suspend fun<M: AbstractModel<S,W>,S: State<W>,W:Widget> nextAction(
		eContext: ExplorationContext<M,S,W>
	): ExplorationAction
}