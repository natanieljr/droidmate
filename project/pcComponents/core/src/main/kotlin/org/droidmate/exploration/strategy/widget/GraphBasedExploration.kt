package org.droidmate.exploration.strategy.widget

import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.graph.StateGraphMF

abstract class GraphBasedExploration : ExplorationStrategy(){
	protected val graph: StateGraphMF by lazy { eContext.getOrCreateWatcher<StateGraphMF>()	}

	override fun initialize(memory: ExplorationContext) {
		super.initialize(memory)

		// Forces the graph to be initialized, even before the strategy is first invoked
		logger.debug("Initializing state graph watcher: $graph")
	}
}