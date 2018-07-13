package org.droidmate.example

import org.droidmate.ExplorationAPI
import org.droidmate.command.ExploreCommand
import org.droidmate.configuration.ConfigurationBuilder
import org.droidmate.exploration.SelectorFunction
import org.droidmate.exploration.StrategySelector
import java.nio.file.FileSystems

class Example {
	companion object {
		@JvmStatic
		fun main(args: Array<String>) {
			println("Starting Droidmate")
			try {
				// Arguments for droidmate can be found in [org.droidmate.configuration.ConfigurationWrapper]
				// Default configuration values can be found in [project/pcComponents/core/src/main/resources/defaultConfig.properties]
				val customArgsForDroidmate = args.toMutableList()
						.also {
							it.add("--Selectors-actionLimit=100")
							it.add("--Selectors-resetEvery=50")
						}

				// Some random example value
				val someId = 10

				// Create a configuration to run Droidmate
				val cfg = ConfigurationBuilder().build(customArgsForDroidmate.toTypedArray(), FileSystems.getDefault())

				// Create the strategy and add it to the list of default strategies on Droidmate
				val myStrategy = ExampleStrategy(someId)
				val strategies = ExploreCommand.getDefaultStrategies(cfg).toMutableList().apply {
					add(myStrategy)
				}

				// Get the default selectors from droidmate
				// By default, the last selector is the random action,
				// It's used as a fallback option in case no other selector was triggered
				// For this example we add a custom selector and re-add the random again as a fallback
				val defaultSelectors = ExploreCommand.getDefaultSelectors(cfg).toMutableList()
						// Remove random
						.dropLast(1)

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
					}

					StrategySelector.logger.debug("Bundle is empty or id is incorrect, return null.")
					null
				}

				val selectors = defaultSelectors.toMutableList()
				selectors.add(StrategySelector(priority = defaultSelectors.size + 1,
						description = "Example Selector",
						selector = mySelector,
						bundle = listOf(someId)))

				// Run Droidmate
				val explorationOutput = ExplorationAPI.explore(cfg, strategies, selectors)

				explorationOutput.forEach { appResult ->
					// Process results for each application
					println("App: ${appResult.apk} Crashed? ${appResult.exceptionIsPresent}")
				}
			} catch (e: Exception) {
				println("Droidmate finished with error")
				println(e.message)
				e.printStackTrace()
				System.exit(1)
			}

			System.exit(0)
		}
	}
}
