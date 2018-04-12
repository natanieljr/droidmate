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

import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.exploration.statemodel.Widget
import org.droidmate.exploration.statemodel.features.EventProbabilityMF

/**
 * Exploration strategy that select a (pseudo-)random widget from the screen.
 */
open class ModelBased @JvmOverloads constructor(randomSeed: Long,
												modelName: String = "HasModel.model",
												arffName: String = "baseModelFile.arff") : RandomWidget(randomSeed) {
	/**
	 * Creates a new exploration strategy instance reading the random seed from the configuration file
	 */
	@JvmOverloads
	constructor(cfg: ConfigurationWrapper,
				modelName: String = "HasModel.model",
				arffName: String = "baseModelFile.arff") : this(cfg.randomSeed.toLong(), modelName, arffName)


	private val watcher: EventProbabilityMF by lazy {
		(context.watcher.find { it is EventProbabilityMF }
				?: EventProbabilityMF(modelName, arffName, true)
						.also { context.watcher.add(it) }) as EventProbabilityMF
	}

	/**
	 * Get all widgets which from the current state that are classified as "with event"
	 *
	 * @return List of widgets which have an associated event (according to the model)
	 */
	protected open fun internalGetWidgets(): List<Widget> {
		return watcher.getProbabilities(currentState)
				.filter { it.value == 1.0 }
				.map { it.key }
	}

	/**
	 * Return the widgets which can be interacted with. In this strategy only widgets "with events"
	 * can be interacted with.
	 *
	 * @return List of widgets which have an associated event (according to the model)
	 */
	override fun getAvailableWidgets(): List<Widget> {
		var candidates = internalGetWidgets()

		this.context.lastTarget?.let { candidates = candidates.filterNot { p -> p.uid == it.uid } }

		return candidates
	}

	// region java overrides

	override fun equals(other: Any?): Boolean {
		if (other !is ModelBased)
			return false

		return other.watcher == this.watcher
	}

	override fun hashCode(): Int {
		return this.javaClass.hashCode()
	}

	// endregion
}
