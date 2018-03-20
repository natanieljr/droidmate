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
package org.droidmate.exploration.strategy.widget

import org.droidmate.configuration.Configuration
import org.droidmate.exploration.actions.ExplorationAction
import org.droidmate.exploration.strategy.ISelectableExplorationStrategy
import org.droidmate.exploration.strategy.WidgetContext
import org.droidmate.exploration.strategy.WidgetInfo

/**
 * Exploration strategy which selects widgets following Fitness Proportionate Selection
 * The fitness is calculated considering the probability to have an event according to a model
 */
open class FitnessProportionateSelection protected constructor(randomSeed: Long,
                                                        modelName: String,
                                                        arffName: String) : ModelBased(randomSeed, modelName, arffName) {

    /**
     * Get all widgets which from a [widget context][widgetContext].
     * For each widget, stores the estimated probability to have an event (according to the model)
     *
     * @return List of widgets with their probability to have an event
     */
    override fun internalGetWidgets(widgetContext: WidgetContext): List<WidgetInfo> {
        wekaInstances.delete()

        val actionableWidgets = widgetContext.actionableWidgetsInfo
        actionableWidgets
                .forEach { p -> wekaInstances.add(p.toWekaInstance(widgetContext, wekaInstances)) }

        val candidates: MutableList<WidgetInfo> = mutableListOf()
        for (i in 0..(wekaInstances.numInstances() - 1)) {
            val instance = wekaInstances.instance(i)
            try {
                // Get probability distribution of the prediction ( [false, true] )
                val predictionProbabilities = classifier.distributionForInstance(instance)
                // Probability of having event
                val probabilityTrue = predictionProbabilities[1]

                val equivWidget = actionableWidgets[i]
                equivWidget.probabilityHaveEvent = probabilityTrue//Math.pow(probabilityTrue, 2.0)

                if (equivWidget.actedUponCount == 0)
                    equivWidget.probabilityHaveEvent = 2 * equivWidget.probabilityHaveEvent

                candidates.add(equivWidget)

            } catch (e: ArrayIndexOutOfBoundsException) {
                logger.error("Could not classify widget of type ${actionableWidgets[i]}. Ignoring it", e)
                // do nothing
            }
        }

        return candidates
    }

    /**
     * Selects a widget following "Fitness Proportionate Selection"
     */
    override fun chooseRandomWidget(widgetContext: WidgetContext): ExplorationAction {
        val candidates = this.getAvailableWidgets(widgetContext)
        assert(candidates.isNotEmpty())

        val probabilities = getCandidatesProbabilities(candidates, widgetContext)
        val selectedIdx = stochasticSelect(probabilities, 10)
        val chosenWidgetInfo = candidates[selectedIdx]

        this.memory.lastWidgetInfo = chosenWidgetInfo
        return chooseActionForWidget(chosenWidgetInfo)
    }

    /**
     * Returns an array with the probabilities of the candidates
     */
    protected open fun getCandidatesProbabilities(candidates: List<WidgetInfo>, widgetContext: WidgetContext): DoubleArray {
        val candidateProbabilities = DoubleArray(candidates.size)

        for (i in candidates.indices)
            candidateProbabilities[i] = candidates[i].probabilityHaveEvent

        return candidateProbabilities
    }

    /**
     * Implementation of the "Roulette-wheel selection with Stochastic acceptance"
     *
     * weight: array with the probabilities of each candidate
     * n_select: number of iterations
     */
    private fun stochasticSelect(weight: DoubleArray, n_select: Int): Int {

        val n = weight.size
        val counter = IntArray(n)

        for (i in 0 until n_select) {
            val index = rouletteSelect(weight)
            counter[index]++
        }

        // If there is an error, we return the last item's index
        return indexOfMax(counter) ?: weight.size-1
    }

    private fun indexOfMax(a: IntArray): Int? {
        return a.withIndex().maxBy { it.value }?.index
    }

    /**
     * Implementation of the "Fitness proportionate selection" strategy
     *
     * Returns the selected index based on the weights(probabilities)
     */
    private fun rouletteSelect(weight: DoubleArray): Int {
        // calculate the total weight
        val weightSum = weight.sum()

        // get a random value
        var value = random.nextDouble() * weightSum

        // locate the random value based on the weights
        for (i in weight.indices) {
            value -= weight[i]
            if (value <= 0)
                return i
        }

        // when rounding errors occur, we return the last item's index
        return weight.size - 1
    }

    // region java overrides

    override fun equals(other: Any?): Boolean {
        if (other !is FitnessProportionateSelection)
            return false

        return other.classifier == this.classifier
    }

    override fun hashCode(): Int {
        return this.classifier.hashCode()
    }

    // endregion

    companion object {
        /**
         * Creates a new exploration strategy instance
         */
        fun build(cfg: Configuration, modelName: String = "HasModel.model",
                  arffName: String = "baseModelFile.arff"): ISelectableExplorationStrategy {
            return FitnessProportionateSelection(cfg.randomSeed.toLong(), modelName, arffName)
        }
    }
}