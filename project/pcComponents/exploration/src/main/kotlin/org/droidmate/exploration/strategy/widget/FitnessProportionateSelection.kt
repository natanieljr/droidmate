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

import kotlinx.coroutines.runBlocking
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.exploration.modelFeatures.ActionCounterMF
import org.droidmate.exploration.modelFeatures.tobedeleted.EventProbabilityMF

/**
 * Exploration strategy which selects widgets following Fitness Proportionate Selection
 * The fitness is calculated considering the probability to have an event according to a model
 */
open class FitnessProportionateSelection @JvmOverloads constructor(randomSeed: Long,
                                                                   modelName: String = "HasModel.model",
                                                                   arffName: String = "baseModelFile.arff") : ModelBased(randomSeed, modelName, arffName) {

	@JvmOverloads
	constructor(cfg: ConfigurationWrapper, modelName: String = "HasModel.model", arffName: String = "baseModelFile.arff")
			: this(cfg.randomSeed, modelName, arffName)

	protected open val eventWatcher: EventProbabilityMF by lazy {
		(eContext.findWatcher { it is EventProbabilityMF }
				?: EventProbabilityMF(modelName, arffName, true)
						.also { eContext.addWatcher(it) }) as EventProbabilityMF
	}

	@Suppress("MemberVisibilityCanBePrivate")
	protected val countWatcher: ActionCounterMF by lazy { eContext.getOrCreateWatcher<ActionCounterMF>() }

	/**
	 * Get all widgets which from a [widget eContext][currentState].
	 * For each widget, stores the estimated probability to have an event (according to the model)
	 *
	 * @return List of widgets with their probability to have an event
	 */
	override fun internalGetWidgets(): List<Widget> {
		return eventWatcher.getProbabilities(currentState)
				.map { it.key }
	}

	/**
	 * Selects a widget following "Fitness Proportionate Selection"
	 */
	override fun chooseRandomWidget(): ExplorationAction {
		val candidates = this.internalGetWidgets()
		assert(candidates.isNotEmpty())

		val probabilities = getCandidatesProbabilities()
		val selectedIdx = stochasticSelect(probabilities.values, 10)
		val widget = candidates[selectedIdx]

		return chooseActionForWidget(widget)
	}

	/**
	 * Returns an array with the probabilities of the candidates
	 */
	protected open fun getCandidatesProbabilities(): Map<Widget, Double> {
		return eventWatcher.getProbabilities(currentState)
				.map {
					it.key to
							(if (runBlocking { countWatcher.widgetCnt(it.key.uid) } == 0)
								it.value * 2
							else
								it.value)
				}
				.toMap()
	}

	/**
	 * Implementation of the "Roulette-wheel selection with Stochastic acceptance"
	 *
	 * weight: array with the probabilities of each candidate
	 * n_select: number of iterations
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	protected fun stochasticSelect(weight: Collection<Double>, n_select: Int): Int {

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
	private fun rouletteSelect(weight: Collection<Double>): Int {
		// calculate the total weight
		val weightSum = weight.sum()

		// get a random value
		var value = random.nextDouble() * weightSum

		// locate the random value based on the weights
		for (i in weight.indices) {
			value -= weight.elementAt(i)
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

		return other.eventWatcher == this.eventWatcher &&
				other.countWatcher == this.countWatcher
	}

	override fun hashCode(): Int {
		return this.javaClass.hashCode()
	}

	// endregion
}