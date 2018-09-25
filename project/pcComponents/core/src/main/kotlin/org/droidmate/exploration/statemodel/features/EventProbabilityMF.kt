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

package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.newCoroutineContext
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.sync.Mutex
import org.apache.commons.lang3.StringUtils
import org.droidmate.exploration.statemodel.StateData
import org.droidmate.exploration.statemodel.Widget
import org.droidmate.exploration.strategy.AbstractStrategy
import org.droidmate.exploration.strategy.ResourceManager
import weka.classifiers.trees.RandomForest
import weka.core.DenseInstance
import weka.core.Instance
import weka.core.Instances
import weka.core.converters.ConverterUtils
import java.io.InputStream
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext

@Suppress("MemberVisibilityCanBePrivate")
open class EventProbabilityMF(modelName: String,
							  arffName: String,
							  protected val useClassMembershipProbability: Boolean) : ModelFeature() {
	init{
		job = Job(parent = (this.job)) // we don't want to wait for other features (or having them wait for us), therefore create our own (child) job
	}

	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("EventProbabilityMF"), parent = job)

	/**
	 * Weka classifier with pre-trained model
	 */
	protected val classifier: RandomForest by lazy {
		val model: InputStream = ResourceManager.getResource(modelName)
		log.debug("Loading model file")
		weka.core.SerializationHelper.read(model) as RandomForest
	}
	/**
	 * Instances originally used to train the model.
	 */
	protected val wekaInstances: Instances by lazy {
		log.debug("Loading ARFF header")
		val modelData: InputStream = ResourceManager.getResource(arffName)
		initializeInstances(modelData)
	}

	/**
	 * Mutex for synchronization
	 */
	protected val mutex = Mutex()

	/**
	 * Load instances used to train the model and then remove all elements.
	 *
	 * This is necessary because we applied a String-to-Nominal filter and, therefore,
	 * we are required to use the same indices for the attributes on new instances, i.e.,
	 * otherwise the model would give false results.
	 *
	 * @return Empty Weka instance set (with loaded nominal attributes)
	 */
	protected fun initializeInstances(modelData: InputStream): Instances {
		val source = ConverterUtils.DataSource(modelData)
		val model = source.dataSet

		// Remove all instances (keep attributes)
		model.delete()

		// Set HasEvent attribute as Class attribute to predict
		val numAttributes = model.numAttributes()
		model.setClassIndex(numAttributes - 1)

		return model
	}

	override suspend fun onNewInteracted(traceId: UUID, targetWidgets: List<Widget>, prevState: StateData, newState: StateData) {
		try {
			mutex.lock()
			wekaInstances.delete()

			val actionableWidgets = newState.actionableWidgets
			actionableWidgets.forEach { wekaInstances.add(it.toWekaInstance(newState, wekaInstances)) }

			for (i in 0 until wekaInstances.numInstances()) {
				val instance = wekaInstances.instance(i)
				try {
					// Probability of having event
					val predictionProbability: Double

					val equivWidget = actionableWidgets[i]
					if (useClassMembershipProbability) {
						// Get probability distribution of the prediction ( [false, true] )
						predictionProbability = classifier.distributionForInstance(instance)[1]
					} else {
						val classification = classifier.classifyInstance(instance)
						// Classified as true = 1.0
						predictionProbability = classification
					}

					widgetProbability[equivWidget.uid] = predictionProbability

				} catch (e: ArrayIndexOutOfBoundsException) {
					log.error("Could not classify widget of type ${actionableWidgets[i]}. Ignoring it", e)
					// do nothing
				}
			}
		}
		finally {
			mutex.unlock()
		}
	}

	protected fun Widget.getRefinedType(): String {
		return if (AbstractStrategy.VALID_WIDGETS.contains(this.className.toLowerCase()))
			className.toLowerCase()
		else {
			//Get last part
			val parts = className.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
			var refType = parts[parts.size - 1].toLowerCase()
			refType = findClosestView(refType)

			refType.toLowerCase()
		}
	}

	protected fun findClosestView(target: String): String {
		var distance = Integer.MAX_VALUE
		var closest = ""

		for (compareObject in AbstractStrategy.VALID_WIDGETS) {
			val currentDistance = StringUtils.getLevenshteinDistance(compareObject, target)
			if (currentDistance < distance) {
				distance = currentDistance
				closest = compareObject
			}
		}
		return closest
	}

	/**
	 * Get the index of a String value on the original Weka training data (ARFF file)
	 *
	 * @return Index of the String in the attribute list or -1 if not found
	 */
	protected fun Instances.getNominalIndex(attributeNumber: Int, value: String): Double {
		return this.attribute(attributeNumber)
				.enumerateValues()
				.toList()
				.indexOf(value)
				.toDouble()
	}

	/**
	 * Converts a widget info given a eContext where the widget is inserted (used to locate parents
	 * and children) and a [Weka model][model]
	 *
	 * @receiver [Widget]
	 */
	protected fun Widget.toWekaInstance(state: StateData, model: Instances): Instance {
		val attributeValues = DoubleArray(5)

		attributeValues[0] = model.getNominalIndex(0, this.getRefinedType())

		if (this.parentId != null)
			attributeValues[1] = model.getNominalIndex(1, state.widgets.first { parent -> parent.id == this.parentId }.getRefinedType())
		else
			attributeValues[1] = model.getNominalIndex(1, "none")

		val children = state.widgets
				.filter { p -> p.parentId == this.id }

		if (children.isNotEmpty())
			attributeValues[2] = model.getNominalIndex(2, children.first().getRefinedType())
		else
			attributeValues[2] = model.getNominalIndex(2, "none")

		if (children.size > 1)
			attributeValues[3] = model.getNominalIndex(3, children[1].getRefinedType())
		else
			attributeValues[3] = model.getNominalIndex(3, "none")

		attributeValues[4] = model.getNominalIndex(4, "false")

		return DenseInstance(1.0, attributeValues)
	}

	protected val widgetProbability = mutableMapOf<UUID, Double>() // probability of each widget having an event

	open fun getProbabilities(state: StateData): Map<Widget, Double> {
		try {
			runBlocking{ mutex.lock() }
			val data = state.actionableWidgets
					.map { it to (widgetProbability[it.uid] ?: 0.0) }
					.toMap()

			assert(data.isNotEmpty()) { "No actionable widgets to be interacted with" }

			return data
		} finally {
			mutex.unlock()
		}
	}

	override fun equals(other: Any?): Boolean {
		return other is EventProbabilityMF &&
				other.useClassMembershipProbability == this.useClassMembershipProbability &&
				other.classifier == this.classifier
	}

	override fun hashCode(): Int {
		return this.classifier.hashCode() * this.useClassMembershipProbability.hashCode()
	}
}