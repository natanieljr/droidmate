package org.droidmate.example

import kotlinx.coroutines.runBlocking
import org.droidmate.api.ExplorationAPI
import org.droidmate.command.ExploreCommandBuilder
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
					val command = ExploreCommandBuilder.fromConfig(cfg)
						.withStrategy(ExampleStrategy(someId))
						.insertBefore(StrategySelector.randomWidget,"Example Selector", mySelector, arrayOf(someId))

					// Run Droidmate
					val explorationOutput = ExplorationAPI.explore(cfg, command)

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
