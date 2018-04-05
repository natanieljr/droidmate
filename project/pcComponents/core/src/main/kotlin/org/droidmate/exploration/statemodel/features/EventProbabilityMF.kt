package org.droidmate.exploration.statemodel.features

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.newCoroutineContext
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

open class EventProbabilityMF(modelName: String,
							  arffName: String,
							  private val useClassMembershipProbability: Boolean) : ModelFeature() {
	init{
		job = Job(parent = (this.job)) // we don't want to wait for other features (or having them wait for us), therefore create our own (child) job
	}

	override val context: CoroutineContext = newCoroutineContext(context = CoroutineName("EventProbabilityMF"), parent = job)

	/**
	 * Weka classifier with pre-trained model
	 */
	private val classifier: RandomForest by lazy {
		val model: InputStream = ResourceManager.getResource(modelName)
		log.debug("Loading model file")
		weka.core.SerializationHelper.read(model) as RandomForest
	}
	/**
	 * Instances originally used to train the model.
	 */
	private val wekaInstances: Instances by lazy {
		log.debug("Loading ARFF header")
		val modelData: InputStream = ResourceManager.getResource(arffName)
		initializeInstances(modelData)
	}

	/**
	 * Load instances used to train the model and then remove all elements.
	 *
	 * This is necessary because we applied a String-to-Nominal filter and, therefore,
	 * we are required to use the same indices for the attributes on new instances, i.e.,
	 * otherwise the model would give false results.
	 *
	 * @return Empty Weka instance set (with loaded nominal attributes)
	 */
	private fun initializeInstances(modelData: InputStream): Instances {
		val source = ConverterUtils.DataSource(modelData)
		val model = source.dataSet

		// Remove all instances (keep attributes)
		model.delete()

		// Set HasEvent attribute as Class attribute to predict
		val numAttributes = model.numAttributes()
		model.setClassIndex(numAttributes - 1)

		return model
	}

	override suspend fun onNewInteracted(targetWidget: Widget?, prevState: StateData, newState: StateData) {
		wekaInstances.delete()

		val actionableWidgets = newState.actionableWidgets
		actionableWidgets.forEach { wekaInstances.add(it.toWekaInstance(newState, wekaInstances)) }

		for (i in 0 until wekaInstances.numInstances()) {
			val instance = wekaInstances.instance(i)
			try {
				// Probability of having event
				val predictionProbability : Double

				val equivWidget = actionableWidgets[i]
				if (useClassMembershipProbability) {
					// Get probability distribution of the prediction ( [false, true] )
					predictionProbability = classifier.distributionForInstance(instance)[1]
				}
				else {
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

	private fun Widget.getRefinedType(): String {
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

	private fun findClosestView(target: String): String {
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
	private fun Instances.getNominalIndex(attributeNumber: Int, value: String): Double {
		return this.attribute(attributeNumber)
				.enumerateValues()
				.toList()
				.indexOf(value)
				.toDouble()
	}

	/**
	 * Converts a widget info given a context where the widget is inserted (used to locate parents
	 * and children) and a [Weka model][model]
	 *
	 * @receiver [Widget]
	 */
	private fun Widget.toWekaInstance(state: StateData, model: Instances): Instance {
		val attributeValues = DoubleArray(5)

		attributeValues[0] = model.getNominalIndex(0, this.getRefinedType())

		if (this.parentId != null)
			attributeValues[1] = model.getNominalIndex(1, state.widgets.first { it.id == parentId }.getRefinedType())
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

	private val widgetProbability = mutableMapOf<UUID, Double>() // probability of each widget having an event

	fun getProbabilities(state: StateData): Map<Widget, Double> {
		return state.actionableWidgets
				.map { it to (widgetProbability[it.uid] ?: 0.0) }
				.toMap()
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