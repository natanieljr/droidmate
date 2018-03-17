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

import com.google.common.base.Ticker
import org.droidmate.configuration.Configuration
import org.droidmate.device.datatypes.statemodel.ActionResult
import org.droidmate.device.datatypes.statemodel.StateData
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.data_aggregators.IExplorationLog
import org.droidmate.exploration.strategy.reset.AppCrashedReset
import org.droidmate.exploration.strategy.reset.CannotExploreReset
import org.droidmate.exploration.strategy.reset.InitialReset
import org.droidmate.exploration.strategy.reset.IntervalReset
import org.droidmate.exploration.strategy.termination.ActionBasedTerminate
import org.droidmate.exploration.strategy.termination.CannotExploreTerminate
import org.droidmate.exploration.strategy.termination.TimeBasedTerminate
import org.droidmate.exploration.strategy.widget.AllowRuntimePermission
import org.droidmate.exploration.strategy.widget.FitnessProportionateSelection
import org.droidmate.exploration.strategy.widget.ModelBased
import org.droidmate.exploration.strategy.widget.RandomWidget
import org.slf4j.LoggerFactory

/**
 * Exploration strategy pool that selects an exploration for a pool
 * of possible strategies based on their fitness for the solution.
 *
 * @author Nataniel P. Borges Jr.
 */
class ExplorationStrategyPool(receivedStrategies: MutableList<ISelectableExplorationStrategy>,
                              private val memory: IExplorationLog) : IExplorationStrategy, IControlObserver {

    companion object {
        private val logger = LoggerFactory.getLogger(ExplorationStrategyPool::class.java)

        private fun getTerminationStrategies(cfg: Configuration): List<ISelectableExplorationStrategy> {
            val strategies: MutableList<ISelectableExplorationStrategy> = ArrayList()

            if (cfg.widgetIndexes.isNotEmpty() || cfg.actionsLimit > 0)
                strategies.add(ActionBasedTerminate(cfg))

            if (cfg.timeLimit > 0)
                strategies.add(TimeBasedTerminate(cfg.timeLimit, Ticker.systemTicker()))

            strategies.add(CannotExploreTerminate())

            return strategies
        }

        private fun getResetStrategies(cfg: Configuration): List<ISelectableExplorationStrategy> {
            val strategies: MutableList<ISelectableExplorationStrategy> = ArrayList()

            strategies.add(InitialReset())
            strategies.add(AppCrashedReset())
            strategies.add(CannotExploreReset())

            // Interval reset
            if (cfg.resetEveryNthExplorationForward > 0)
                strategies.add(IntervalReset(cfg.resetEveryNthExplorationForward))
            return strategies
        }

        fun build(explorationLog: IExplorationLog, cfg: Configuration): ExplorationStrategyPool {

            val strategies = ArrayList<ISelectableExplorationStrategy>()

            // Default strategies
            strategies.addAll(getTerminationStrategies(cfg))
            strategies.addAll(getResetStrategies(cfg))

            // Press back
            if (cfg.pressBackProbability > 0.0)
                strategies.add(PressBack.build(cfg.pressBackProbability, cfg))

            // Random exploration
            if (cfg.explorationStrategies.contains(StrategyTypes.RandomWidget.strategyName))
                strategies.add(RandomWidget.build(cfg))

            // ExplorationContext based
            if (cfg.explorationStrategies.contains(StrategyTypes.ModelBased.strategyName))
                strategies.add(ModelBased.build(cfg))

            // Allow runtime dialogs
            if (cfg.explorationStrategies.contains(StrategyTypes.AllowRuntimePermission.strategyName))
                strategies.add(AllowRuntimePermission.build())

            // Seek targets
            if (cfg.explorationStrategies.contains(StrategyTypes.SeekTargets.strategyName)) {
                val targetedStrategies = SeekTarget.build(ArrayList(), "")
                targetedStrategies.forEach { p -> strategies.add(p) }
            }

            // Fitness Proportionate Selection
            if (cfg.explorationStrategies.contains(StrategyTypes.FitnessProportionate.strategyName))
                strategies.add(FitnessProportionateSelection.build(cfg))

            return ExplorationStrategyPool(strategies, explorationLog)
        }
    }

    // region properties

    /**
     * Internal list of strategies
     */
    private val strategies: MutableList<ISelectableExplorationStrategy> = ArrayList()

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
     * Givers control to an internal exploration strategy given the [current UI][currentState]
     */
    private fun handleControl(currentState: StateData) {
        ExplorationStrategyPool.logger.debug("Attempting to handle control to exploration strategy")
        assert(this.hasControl())
        this.activeStrategy = this.selectStrategy(currentState)

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
    private fun selectStrategy(StateData: StateData): ISelectableExplorationStrategy {
        ExplorationStrategyPool.logger.debug("Selecting best strategy.")
        val maxFitness = this.strategies
                .map { Pair(it, it.getFitness(StateData)) }
                .maxBy { it.second.value }

        val bestStrategy = maxFitness!!.first
        ExplorationStrategyPool.logger.debug("Best strategy is $bestStrategy with fitness ${maxFitness.second}.")

        return bestStrategy
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

    /**
     * Notifies all internal strategies that the exploration will start
     */
    private fun startStrategies() {
        for (strategy in this.strategies)
            strategy.start()
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

        if (this.memory.isEmpty())
            this.startStrategies()

//        val StateData = this.memory.getStateData(guiState)    TODO
        val StateData = this.memory.getState() StateData (emptyList(), "", memory.apk.packageName)

        if (this.hasControl())
            this.handleControl(StateData)
        else
            logger.debug("Control is currently with strategy ${this.activeStrategy}")

        //val explorationType = this.activeStrategy!!.type
        val selectedAction = this.activeStrategy!!.decide(StateData)

        //this.updateState(selectedAction, explorationType, StateData, startTimestamp)

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

}
