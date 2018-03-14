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
package org.droidmate.exploration.strategy.widget

import org.droidmate.configuration.Configuration
import org.droidmate.device.datatypes.Widget
import org.droidmate.exploration.strategy.*
import weka.classifiers.trees.RandomForest
import weka.core.DenseInstance
import weka.core.Instance
import weka.core.Instances
import weka.core.converters.ConverterUtils
import java.io.InputStream

/**
 * Exploration strategy that select a (pseudo-)random widget from the screen.
 */
open class ModelBased protected constructor(randomSeed: Long,
                                            modelName: String,
                                            arffName: String) : RandomWidget(randomSeed) {

    /**
     * Weka classifier with pre-trained model
     */
    protected val classifier: RandomForest by lazy {
        val model: InputStream by lazy { ResourceManager.getResource(modelName) }
        weka.core.SerializationHelper.read(model) as RandomForest
    }
    /**
     * Instances originally used to train the model.
     */
    protected val wekaInstances: Instances by lazy {
        val modelData: InputStream by lazy { ResourceManager.getResource(arffName) }
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
     * Converts a widget info given a [context where the widget is inserted][widgetContext] (used to locate parents
     * and children) and a [Weka model][model]
     *
     * @receiver [WidgetInfo]
     */
    protected fun WidgetInfo.toWekaInstance(widgetContext: WidgetContext, model: Instances): Instance {
        val attributeValues: DoubleArray = kotlin.DoubleArray(5)

        attributeValues[0] = model.getNominalIndex(0, this.widget.getRefinedType())

        if (this.widget.parentId != null)
            attributeValues[1] = model.getNominalIndex(1, memory.getCurrentState().widgets.find{it.id == widget.parentId}!!.className)  //TODO get rectified name
        else
            attributeValues[1] = model.getNominalIndex(1, "none")

        val children = memory.getCurrentState().widgets
                .filter { p -> p.parentId == this.widget.id }

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

    /**
     * Get all widgets which from a [widget context][widgetContext] that are classified as "with event"
     *
     * @return List of widgets which have an associated event (according to the model)
     */
    protected open fun internalGetWidgets(widgetContext: WidgetContext): List<Widget> {
        TODO()
//        wekaInstances.delete()
//
//        val actionableWidgets = widgetContext.getActionableWidgetsInclChildren()//.actionableWidgetsInfo
//        actionableWidgets
//                .forEach { p -> wekaInstances.add(p.toWekaInstance(widgetContext, wekaInstances)) }
//
//        val candidates: MutableList<WidgetInfo> = ArrayList()
//        for (i in 0..(wekaInstances.numInstances() - 1)) {
//            val instance = wekaInstances.instance(i)
//            try {
//                val classification = classifier.classifyInstance(instance)
//                // Classified as true
//                if (classification == 1.0) {
//                    val equivWidget = actionableWidgets[i]
//                    candidates.add(equivWidget)
//                }
//            } catch (e: ArrayIndexOutOfBoundsException) {
//                logger.error("Could not classify widget of type ${actionableWidgets[i]}. Ignoring it", e)
//                // do nothing
//            }
//        }
//
//        return candidates
    }

    /**
     * Return the widgets which can be interacted with. In this strategy only widgets "with events"
     * can be interacted with.
     *
     * @return List of widgets which have an associated event (according to the model)
     */
    override fun getAvailableWidgets(widgetContext: WidgetContext): List<Widget> {
        var candidates = internalGetWidgets(widgetContext)

        candidates = candidates.filterNot { it.isEquivalent(this.memory.lastTarget) }

        return candidates
    }

    /**
     * Returns a high priority when the model found widgets with events. Otherwise this strategy's priority is 0
     */
    override fun getFitness(widgetContext: WidgetContext): StrategyPriority {
        val candidates = getAvailableWidgets(widgetContext)

        // Lower priority than reset, more than random exploration
        return if (candidates.isNotEmpty())
            StrategyPriority.BIASED_RANDOM_WIDGET
        else
            StrategyPriority.NONE
    }

    // region java overrides

    override fun equals(other: Any?): Boolean {
        if (other !is ModelBased)
            return false

        return other.classifier == this.classifier
    }

    override fun hashCode(): Int {
        return this.classifier.hashCode()
    }

    override fun toString(): String {
        val thisClassifierName = this.classifier.javaClass.simpleName
        return "${this.javaClass}\tClassifier: $thisClassifierName"
    }

    // endregion

    companion object {
        /**
         * Creates a new exploration strategy instance
         */
        fun build(cfg: Configuration, modelName: String = "HasModel.model",
                  arffName: String = "baseModelFile.arff"): ISelectableExplorationStrategy {
            return ModelBased(cfg.randomSeed.toLong(), modelName, arffName)
        }
    }
}
