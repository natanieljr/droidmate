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

import org.droidmate.android_sdk.IApk
import org.droidmate.device.datatypes.IGuiState
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.actions.IExplorationActionRunResult
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * Pool of exploration strategies that can be selected
 * of exploration strategies

 * @author Nataniel P. Borges Jr.
 */
class StrategyPool : IStrategyPool, IControlObserver {
    // region properties

    /**
     * Internal list of strategies
     */
    private val strategies = ArrayList<ISelectableExplorationStrategy>()

    /**
     * Strategy which is currently active
     */
    private var activeStrategy: ISelectableExplorationStrategy? = null

    /**
     * Number of elapsed actions
     */
    private var actionNr: Int = 0

    /**
     * Are all widgets blacklisted
     */
    private var allWidgetsBlackListed = false

    override val size: Int
        get() = this.strategies.size

    override var memory = Memory()

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
     * Givers control to an internal exploration strategy given the [current UI][widgetContext]
     */
    private fun handleControl(widgetContext: WidgetContext) {
        logger.debug("Attempting to handle control to exploration strategy")
        assert(this.hasControl())
        this.activeStrategy = this.selectStrategy(widgetContext)

        assert(!this.hasControl())
        logger.debug("Control handled to strategy ${this.activeStrategy!!}")
    }

    /**
     * Selects an exploration strategy to [handle control to][handleControl], given the [current UI][widgetContext].
     * The selected strategy is the one with best fitness.
     *
     * If more than one exploration strategies have the same fitness, choose the first one.
     *
     * @return Exploration strategy with highest fitness.
     */
    private fun selectStrategy(widgetContext: WidgetContext): ISelectableExplorationStrategy {
        logger.debug("Selecting best strategy.")
        val maxFitness = this.strategies
                .map { Pair(it, it.getFitness(widgetContext)) }
                .maxBy { it.second.value }

        val bestStrategy = maxFitness!!.first
        logger.debug("Best strategy is $bestStrategy with fitness ${maxFitness.second}.")

        return bestStrategy
    }

    /**
     * Log the exploration progress after and internal strategy has selected it containing the
     * [action sent to the device][selectedAction], the [type of the strategy which create the action][explorationType],
     * the [state of the UI when the action was created][widgetContext] and the
     * [moment in which the strategy started selecting an action][startTimestamp] to send to the device
     *
     * @param selectedAction Action selected be an internal strategy
     * @param
     */
    private fun logExplorationProgress(selectedAction: ExplorationAction, explorationType: ExplorationType,
                                       widgetContext: WidgetContext, startTimestamp: LocalDateTime) {
        this.memory.logProgress(selectedAction, explorationType, widgetContext, startTimestamp)

        logger.debug(selectedAction.toString())
    }

    override fun takeControl(strategy: ISelectableExplorationStrategy) {
        logger.debug("Receiving back control from strategy $strategy")
        assert(this.strategies.contains(strategy))
        this.activeStrategy = null
    }

    // endregion

    // region initialization

    /**
     * Notifies all internal strategies that the exploration will start
     */
    private fun startStrategies() {
        for (strategy in this.strategies)
            strategy.start()
    }

    override fun registerStrategy(strategy: ISelectableExplorationStrategy): Boolean {
        logger.info("Registering strategy $strategy.")

        if (this.strategies.contains(strategy)) {
            logger.warn("Strategy already registered, skipping.")
            return false
        }

        strategy.registerListener(this)
        this.strategies.add(strategy)

        return true
    }

    override fun initialize() {
        for (strategy in this.strategies)
            strategy.initialize(this.memory)
    }

    //endregion

    /**
     * Notify the internal strategies to update their state
     */
    private fun updateStrategiesState() {
        for (strategy in this.strategies)
            strategy.updateState(this.actionNr)
    }

    /**
     * Update the internal state of the pool and then notify the internal strategies to do the same
     */
    private fun updateState(selectedAction: ExplorationAction, explorationType: ExplorationType,
                            widgetContext: WidgetContext, startTimestamp: LocalDateTime) {
        this.actionNr++

        logExplorationProgress(selectedAction, explorationType, widgetContext, startTimestamp)
        this.updateStrategiesState()
    }

    override fun decide(guiState: IGuiState, appPackageName: String): ExplorationAction {
        val startTimestamp = LocalDateTime.now()
        logger.debug("pool decide")
        assert(!this.strategies.isEmpty())

        if (this.memory.isEmpty())
            this.startStrategies()

        val widgetContext = this.memory.getWidgetContext(guiState, appPackageName)

        if (this.hasControl())
            this.handleControl(widgetContext)
        else
            logger.debug("Control is currently with strategy ${this.activeStrategy}")

        val explorationType = this.activeStrategy!!.type
        val selectedAction = this.activeStrategy!!.decide(widgetContext)

        this.updateState(selectedAction, explorationType, widgetContext, startTimestamp)

        return selectedAction
    }

    override fun notifyAllWidgetsBlacklisted() {
        this.allWidgetsBlackListed = true
    }

    override fun onTargetFound(strategy: ISelectableExplorationStrategy, targetWidget: ITargetWidget,
                               result: IExplorationActionRunResult) {
        this.strategies.forEach { it.onTargetFound(strategy, targetWidget, result) }
    }

    override fun resetMemory(apk: IApk) {
        this.memory = Memory(apk)
    }

    override fun clear() {
        this.strategies.clear()
        this.activeStrategy = null
        this.actionNr = 0
        this.allWidgetsBlackListed = false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StrategyPool::class.java)
    }
}
