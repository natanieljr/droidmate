package org.droidmate.example

import org.droidmate.exploration.SelectorFunction
import org.droidmate.exploration.StrategySelector

val mySelector: SelectorFunction = { context, pool, bundle ->
	// Selectors are always invoked to define the strategy, they can also be used to register model
	// features that need to be ready before the strategy starts
	val modelFeature = context.getOrCreateWatcher<ExampleModelFeature>()


	// Selector function receives the current state [context], the strategy pool with all strategies [pool]
	// and a nullable array of content [bundle], defined on it's creation, the bundle can be used to store
	// values for the selector to check. Example usages are available in [org.droidmate.exploration.StrategySelector]
	if (bundle != null) {
		assert(bundle.first() is Int)
		val id = bundle.first() as Int

		StrategySelector.logger.debug("Evaluating payload and current state.")

		if (modelFeature.count == id) {
			StrategySelector.logger.debug("Correct id, return strategy.")
			pool.getFirstInstanceOf(ExampleStrategy::class.java)
		}
		else{
			StrategySelector.logger.debug("It is not yet time to execute the strategy....")
			null
		}
	}
	else {
		StrategySelector.logger.debug("Bundle is empty or id is incorrect, return null.")
		null
	}
}