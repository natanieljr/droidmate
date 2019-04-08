package org.droidmate.example

import com.natpryce.konfig.Configuration
import kotlinx.coroutines.runBlocking
import org.droidmate.ExplorationAPI
import org.droidmate.command.ExploreCommand
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.exploration.StrategySelector

class Example {
	companion object {
		@JvmStatic
		fun main(args: Array<String>) {
			runBlocking {
				// Create a configuration to run Droidmate
				val cfg = ExplorationAPI.config(args)

				run(cfg)
			}
		}

		suspend fun run(cfg: ConfigurationWrapper) {
				println("Starting Droidmate")
				try {
					// Some random example value
					// Create the strategy and update it to the list of default strategies on Droidmate
					val someId = 10
					val myStrategy = ExampleStrategy(someId)

					val strategies = ExploreCommand.getDefaultStrategies(cfg).toMutableList()
						.apply {
							add(myStrategy)
						}

					val random = ExploreCommand.getDefaultSelectors(cfg).last()
					val defaultSelectors = ExploreCommand.getDefaultSelectors(cfg).toMutableList()
						// Remove random
						.dropLast(1)

					val selectors = defaultSelectors.toMutableList()
					selectors.add(
						StrategySelector(
							priority = defaultSelectors.size + 1,
							description = "Example Selector",
							selector = mySelector,
							bundle = someId
						)
					)

					selectors.add(random)

					// Run Droidmate
					val explorationOutput = ExplorationAPI.explore(cfg, strategies, selectors)

					explorationOutput.forEach { appResult ->
						// Process results for each application
						println("App: ${appResult.key} Crashed? ${appResult.value.error.isNotEmpty()}")
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
