package org.droidmate.exploration.strategy.widget

import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.graph.StateGraphMF
import org.droidmate.exploration.strategy.AExplorationStrategy
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

abstract class GraphBasedExploration : AExplorationStrategy(){
	lateinit var eContext : ExplorationContext<*,*,*>

	override fun<M: AbstractModel<S, W>,S: State<W>,W: Widget> initialize(initialContext: ExplorationContext<M,S,W>){
		this.eContext = initialContext
	}
	@Suppress("RemoveExplicitTypeArguments")
	protected val graph: StateGraphMF by lazy {
		if (!::eContext.isInitialized) throw IllegalStateException("you have to initialize the property 'eContext' before calling 'graph'")
		eContext.getOrCreateWatcher<StateGraphMF>()	}

}